package auth

import (
	"context"
	"crypto"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"math/big"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"
)

type Verifier struct {
	Enabled bool
	JWKSURI string
	Issuer  string
	Client  *http.Client

	mu       sync.Mutex
	keys     map[string]*rsa.PublicKey
	loadedAt time.Time
	cacheTTL time.Duration
}

type jwks struct {
	Keys []jwk `json:"keys"`
}

type jwk struct {
	Kid string `json:"kid"`
	Kty string `json:"kty"`
	Alg string `json:"alg"`
	Use string `json:"use"`
	N   string `json:"n"`
	E   string `json:"e"`
}

func NewFromEnv() *Verifier {
	enabled := strings.EqualFold(os.Getenv("KEYCLOAK_AUTH_ENABLED"), "true") || strings.EqualFold(os.Getenv("NEBULAOPS_SECURITY_ENABLED"), "true")
	jwksURI := os.Getenv("KEYCLOAK_JWKS_URI")
	if jwksURI == "" {
		jwksURI = "http://localhost:8180/realms/nebulaops/protocol/openid-connect/certs"
	}
	issuer := os.Getenv("KEYCLOAK_ISSUER")
	return &Verifier{Enabled: enabled, JWKSURI: jwksURI, Issuer: issuer, Client: &http.Client{Timeout: 5 * time.Second}, cacheTTL: 10 * time.Minute}
}

func (v *Verifier) Middleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !v.Enabled || r.URL.Path == "/health" || strings.HasPrefix(r.URL.Path, "/actuator/health") {
			next.ServeHTTP(w, r)
			return
		}
		authz := r.Header.Get("Authorization")
		if !strings.HasPrefix(authz, "Bearer ") {
			http.Error(w, "missing Bearer token", http.StatusUnauthorized)
			return
		}
		claims, err := v.Verify(strings.TrimSpace(strings.TrimPrefix(authz, "Bearer ")))
		if err != nil {
			http.Error(w, "invalid Bearer token", http.StatusUnauthorized)
			return
		}
		ctx := context.WithValue(r.Context(), "nebulaops.jwt.claims", claims)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func (v *Verifier) Verify(token string) (map[string]any, error) {
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return nil, errors.New("invalid jwt format")
	}
	var header map[string]any
	if err := decodeJSON(parts[0], &header); err != nil {
		return nil, err
	}
	if fmt.Sprint(header["alg"]) != "RS256" {
		return nil, errors.New("unsupported jwt alg")
	}
	kid := fmt.Sprint(header["kid"])
	if kid == "" || kid == "<nil>" {
		return nil, errors.New("missing kid")
	}
	key, err := v.key(kid)
	if err != nil {
		return nil, err
	}
	sig, err := base64.RawURLEncoding.DecodeString(parts[2])
	if err != nil {
		return nil, err
	}
	h := sha256.Sum256([]byte(parts[0] + "." + parts[1]))
	if err := rsa.VerifyPKCS1v15(key, crypto.SHA256, h[:], sig); err != nil {
		return nil, err
	}
	claims := map[string]any{}
	if err := decodeJSON(parts[1], &claims); err != nil {
		return nil, err
	}
	if exp, ok := claims["exp"].(float64); ok && time.Now().Unix() >= int64(exp) {
		return nil, errors.New("token expired")
	}
	if v.Issuer != "" && fmt.Sprint(claims["iss"]) != v.Issuer {
		return nil, errors.New("invalid issuer")
	}
	return claims, nil
}

func (v *Verifier) key(kid string) (*rsa.PublicKey, error) {
	v.mu.Lock()
	defer v.mu.Unlock()
	if v.keys != nil && time.Since(v.loadedAt) < v.cacheTTL {
		if k := v.keys[kid]; k != nil {
			return k, nil
		}
	}
	if err := v.refreshKeysLocked(); err != nil {
		return nil, err
	}
	if k := v.keys[kid]; k != nil {
		return k, nil
	}
	return nil, errors.New("jwt signing key not found")
}

func (v *Verifier) refreshKeysLocked() error {
	resp, err := v.Client.Get(v.JWKSURI)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		return fmt.Errorf("jwks endpoint returned %s", resp.Status)
	}
	var set jwks
	if err := json.NewDecoder(resp.Body).Decode(&set); err != nil {
		return err
	}
	keys := map[string]*rsa.PublicKey{}
	for _, item := range set.Keys {
		if item.Kty != "RSA" || item.N == "" || item.E == "" || item.Kid == "" {
			continue
		}
		pub, err := jwkToRSA(item)
		if err == nil {
			keys[item.Kid] = pub
		}
	}
	v.keys = keys
	v.loadedAt = time.Now()
	return nil
}

func jwkToRSA(k jwk) (*rsa.PublicKey, error) {
	nBytes, err := base64.RawURLEncoding.DecodeString(k.N)
	if err != nil {
		return nil, err
	}
	eBytes, err := base64.RawURLEncoding.DecodeString(k.E)
	if err != nil {
		return nil, err
	}
	e := 0
	for _, b := range eBytes {
		e = e*256 + int(b)
	}
	if e == 0 {
		e = 65537
	}
	return &rsa.PublicKey{N: new(big.Int).SetBytes(nBytes), E: e}, nil
}

func decodeJSON(part string, out any) error {
	b, err := base64.RawURLEncoding.DecodeString(part)
	if err != nil {
		return err
	}
	return json.Unmarshal(b, out)
}

func certToPublicKey(der []byte) (*rsa.PublicKey, error) {
	cert, err := x509.ParseCertificate(der)
	if err != nil {
		return nil, err
	}
	pub, ok := cert.PublicKey.(*rsa.PublicKey)
	if !ok {
		return nil, errors.New("certificate public key is not RSA")
	}
	return pub, nil
}
