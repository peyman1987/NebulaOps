package events

import "testing"

func TestHandlerContract(t *testing.T) {
	called := false
	h := Handler(func(body []byte) error { called = string(body) == "ok"; return nil })
	if err := h([]byte("ok")); err != nil || !called {
		t.Fatalf("handler contract failed")
	}
}
