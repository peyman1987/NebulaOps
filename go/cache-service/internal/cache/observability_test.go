package cache

import "testing"

func TestObservabilityCacheKeyIsStable(t *testing.T) {
	c := &ObservabilityCache{}
	a := c.Key("prometheus", "up")
	b := c.Key("prometheus", "up")
	if a != b || a == "" {
		t.Fatalf("unexpected key values: %q %q", a, b)
	}
}
