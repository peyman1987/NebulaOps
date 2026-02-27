package main

import (
	"context"
	"encoding/json"
	"log"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/peyman/nebulaops/cache-service/internal/auth"
	"github.com/peyman/nebulaops/cache-service/internal/cache"
)

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
func main() {
	svc, err := cache.New(env("REDIS_ADDR", "localhost:6379"), env("RABBITMQ_HTTP_URL", "http://localhost:15672"), env("RABBITMQ_USER", "guest"), env("RABBITMQ_PASSWORD", "guest"), env("RABBITMQ_CACHE_QUEUE", "nebula.cache.events"))
	if err != nil {
		log.Fatalf("cache service startup failed: %v", err)
	}
	mux := http.NewServeMux()
	obsCache := cache.NewObservabilityCache(svc)
	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("content-type", "application/json")
		_, _ = w.Write([]byte(`{"status":"UP","service":"go-cache-service"}`))
	})

	mux.HandleFunc("/cache/stats", func(w http.ResponseWriter, r *http.Request) {
		ctx, cancel := context.WithTimeout(r.Context(), 3*time.Second)
		defer cancel()
		stats, err := svc.Stats(ctx)
		if err != nil {
			http.Error(w, err.Error(), 502)
			return
		}
		w.Header().Set("content-type", "application/json")
		_ = json.NewEncoder(w).Encode(stats)
	})

	mux.HandleFunc("/cache/observability/", func(w http.ResponseWriter, r *http.Request) {
		ctx, cancel := context.WithTimeout(r.Context(), 3*time.Second)
		defer cancel()
		source := strings.TrimPrefix(r.URL.Path, "/cache/observability/")
		query := r.URL.Query().Get("query")
		if query == "" {
			http.Error(w, "query is required", http.StatusBadRequest)
			return
		}
		switch r.Method {
		case http.MethodGet:
			value, err := obsCache.Get(ctx, source, query)
			if err != nil {
				http.Error(w, err.Error(), http.StatusNotFound)
				return
			}
			w.Header().Set("content-type", "application/json")
			_ = json.NewEncoder(w).Encode(map[string]any{"source": source, "query": query, "key": obsCache.Key(source, query), "value": value, "cache": "HIT"})
		case http.MethodPut, http.MethodPost:
			var payload cache.ObservabilityPayload
			if err := json.NewDecoder(r.Body).Decode(&payload); err != nil {
				http.Error(w, err.Error(), http.StatusBadRequest)
				return
			}
			if payload.Query == "" {
				payload.Query = query
			}
			if payload.Source == "" {
				payload.Source = source
			}
			if err := obsCache.Put(ctx, payload.Source, payload.Query, payload.JSONValue(), payload.TTLSeconds); err != nil {
				http.Error(w, err.Error(), http.StatusBadGateway)
				return
			}
			w.Header().Set("content-type", "application/json")
			_ = json.NewEncoder(w).Encode(map[string]any{"status": "cached", "source": payload.Source, "query": payload.Query, "key": obsCache.Key(payload.Source, payload.Query)})
		default:
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		}
	})

	mux.HandleFunc("/cache/", func(w http.ResponseWriter, r *http.Request) {
		ctx, cancel := context.WithTimeout(r.Context(), 3*time.Second)
		defer cancel()
		key := r.URL.Path[len("/cache/"):]
		switch r.Method {
		case http.MethodGet:
			value, err := svc.Get(ctx, key)
			if err != nil {
				http.Error(w, err.Error(), http.StatusNotFound)
				return
			}
			_ = json.NewEncoder(w).Encode(map[string]string{"key": key, "value": value})
		case http.MethodPut:
			var item cache.CacheItem
			if err := json.NewDecoder(r.Body).Decode(&item); err != nil {
				http.Error(w, err.Error(), 400)
				return
			}
			if item.Key == "" {
				item.Key = key
			}
			if err := svc.Put(ctx, item); err != nil {
				http.Error(w, err.Error(), 500)
				return
			}
			w.WriteHeader(http.StatusAccepted)
			_ = json.NewEncoder(w).Encode(map[string]string{"status": "cached", "key": item.Key})
		default:
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		}
	})
	addr := ":" + env("PORT", "8091")
	log.Printf("go-cache-service listening on %s", addr)
	verifier := auth.NewFromEnv()
	log.Printf("keycloak auth enabled=%t jwks=%s", verifier.Enabled, verifier.JWKSURI)
	log.Fatal(http.ListenAndServe(addr, verifier.Middleware(mux)))
}
