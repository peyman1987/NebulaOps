package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/peyman/nebulaops/event-worker/internal/events"
)

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
func main() {
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()
	consumer := events.Consumer{RabbitHTTPURL: env("RABBITMQ_HTTP_URL", "http://localhost:15672"), User: env("RABBITMQ_USER", "guest"), Password: env("RABBITMQ_PASSWORD", "guest"), Queue: env("RABBITMQ_CACHE_QUEUE", "nebula.cache.events"), Handler: func(body []byte) error {
		log.Printf("processed async event at %s: %s", time.Now().UTC().Format(time.RFC3339), string(body))
		return nil
	}}
	log.Printf("go-event-worker polling queue %s", consumer.Queue)
	if err := consumer.Run(ctx); err != nil && err != context.Canceled {
		log.Fatal(err)
	}
}
