package cache

import "testing"

func TestCacheItemValidationShape(t *testing.T) {
	item := CacheItem{Key: "dashboard:summary", Value: "{}", TTLSeconds: 30}
	if item.Key == "" || item.TTLSeconds != 30 {
		t.Fatalf("unexpected cache item: %+v", item)
	}
}
