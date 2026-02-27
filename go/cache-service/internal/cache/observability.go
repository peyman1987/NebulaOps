package cache

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
)

type ObservabilityCache struct{ service *Service }

func NewObservabilityCache(service *Service) *ObservabilityCache {
	return &ObservabilityCache{service: service}
}

type ObservabilityPayload struct {
	Source     string `json:"source"`
	Query      string `json:"query"`
	Value      string `json:"value"`
	TTLSeconds int    `json:"ttlSeconds"`
}

func (c *ObservabilityCache) Key(source, query string) string {
	sum := sha256.Sum256([]byte(source + ":" + query))
	return "observability:" + source + ":" + hex.EncodeToString(sum[:])
}

func (c *ObservabilityCache) Put(ctx context.Context, source, query, value string, ttl int) error {
	if source != "loki" && source != "prometheus" {
		return fmt.Errorf("unsupported observability source: %s", source)
	}
	if ttl <= 0 {
		ttl = 30
	}
	return c.service.Put(ctx, CacheItem{Key: c.Key(source, query), Value: value, TTLSeconds: ttl})
}

func (c *ObservabilityCache) Get(ctx context.Context, source, query string) (string, error) {
	if source != "loki" && source != "prometheus" {
		return "", fmt.Errorf("unsupported observability source: %s", source)
	}
	return c.service.Get(ctx, c.Key(source, query))
}

func (p ObservabilityPayload) JSONValue() string {
	if p.Value != "" {
		return p.Value
	}
	b, _ := json.Marshal(map[string]string{"status": "EMPTY"})
	return string(b)
}
