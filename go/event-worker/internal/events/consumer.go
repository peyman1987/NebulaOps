package events

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"strings"
	"time"
)

type Handler func([]byte) error
type Consumer struct {
	RabbitHTTPURL, User, Password, Queue string
	Handler                              Handler
	PollInterval                         time.Duration
}

func (c Consumer) Run(ctx context.Context) error {
	if c.PollInterval == 0 {
		c.PollInterval = 2 * time.Second
	}
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}
		bodies, err := c.fetch(ctx)
		if err != nil {
			log.Printf("poll failed: %v", err)
			time.Sleep(c.PollInterval)
			continue
		}
		for _, body := range bodies {
			if err := c.Handler(body); err != nil {
				log.Printf("event failed: %v", err)
			}
		}
		time.Sleep(c.PollInterval)
	}
}

func (c Consumer) fetch(ctx context.Context) ([][]byte, error) {
	url := strings.TrimRight(c.RabbitHTTPURL, "/") + "/api/queues/%2F/" + c.Queue + "/get"
	payload := bytes.NewBufferString(`{"count":5,"ackmode":"ack_requeue_false","encoding":"auto","truncate":50000}`)
	req, _ := http.NewRequestWithContext(ctx, http.MethodPost, url, payload)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Basic "+base64.StdEncoding.EncodeToString([]byte(c.User+":"+c.Password)))
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		return nil, fmt.Errorf("rabbitmq get failed: %s", resp.Status)
	}
	raw, _ := io.ReadAll(resp.Body)
	var items []struct {
		Payload string `json:"payload"`
	}
	if err := json.Unmarshal(raw, &items); err != nil {
		return nil, err
	}
	out := make([][]byte, 0, len(items))
	for _, it := range items {
		out = append(out, []byte(it.Payload))
	}
	return out, nil
}
