package cache

import (
	"bufio"
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/http"
	"strings"
	"time"
)

type Service struct{ RedisAddr, RabbitHTTPURL, RabbitUser, RabbitPassword, Queue string }
type CacheItem struct {
	Key        string `json:"key"`
	Value      string `json:"value"`
	TTLSeconds int    `json:"ttlSeconds"`
}

func New(redisAddr, rabbitHTTPURL, user, password, queue string) (*Service, error) {
	s := &Service{RedisAddr: redisAddr, RabbitHTTPURL: strings.TrimRight(rabbitHTTPURL, "/"), RabbitUser: user, RabbitPassword: password, Queue: queue}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	if err := s.redisCommand(ctx, "PING"); err != nil {
		return nil, err
	}
	_ = s.ensureQueue(ctx)
	return s, nil
}

func (s *Service) Put(ctx context.Context, item CacheItem) error {
	if item.Key == "" {
		return errors.New("key is required")
	}
	ttl := item.TTLSeconds
	if ttl <= 0 {
		ttl = 300
	}
	if err := s.redisCommand(ctx, "SET", item.Key, item.Value, "EX", fmt.Sprint(ttl)); err != nil {
		return err
	}
	payload, _ := json.Marshal(map[string]any{"type": "cache.item.updated", "key": item.Key, "ttlSeconds": ttl, "createdAt": time.Now().UTC().Format(time.RFC3339)})
	return s.publish(ctx, payload)
}

func (s *Service) Stats(ctx context.Context) (map[string]string, error) {
	info, err := s.redisRaw(ctx, "INFO", "stats")
	if err != nil {
		return nil, err
	}
	out := map[string]string{"redisAddr": s.RedisAddr, "queue": s.Queue}
	for _, line := range strings.Split(info, "\n") {
		line = strings.TrimSpace(line)
		if strings.Contains(line, ":") {
			parts := strings.SplitN(line, ":", 2)
			if parts[0] == "keyspace_hits" || parts[0] == "keyspace_misses" || parts[0] == "total_commands_processed" || parts[0] == "instantaneous_ops_per_sec" {
				out[parts[0]] = parts[1]
			}
		}
	}
	return out, nil
}

func (s *Service) Get(ctx context.Context, key string) (string, error) {
	if key == "" {
		return "", errors.New("key is required")
	}
	return s.redisBulk(ctx, "GET", key)
}

func (s *Service) redisCommand(ctx context.Context, args ...string) error {
	_, err := s.redisRaw(ctx, args...)
	return err
}
func (s *Service) redisBulk(ctx context.Context, args ...string) (string, error) {
	return s.redisRaw(ctx, args...)
}

func (s *Service) redisRaw(ctx context.Context, args ...string) (string, error) {
	d := net.Dialer{}
	conn, err := d.DialContext(ctx, "tcp", s.RedisAddr)
	if err != nil {
		return "", err
	}
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(3 * time.Second))
	var b bytes.Buffer
	fmt.Fprintf(&b, "*%d\r\n", len(args))
	for _, a := range args {
		fmt.Fprintf(&b, "$%d\r\n%s\r\n", len(a), a)
	}
	if _, err := conn.Write(b.Bytes()); err != nil {
		return "", err
	}
	r := bufio.NewReader(conn)
	line, err := r.ReadString('\n')
	if err != nil {
		return "", err
	}
	line = strings.TrimSpace(line)
	switch {
	case strings.HasPrefix(line, "+"):
		return strings.TrimPrefix(line, "+"), nil
	case strings.HasPrefix(line, ":"):
		return strings.TrimPrefix(line, ":"), nil
	case strings.HasPrefix(line, "$-1"):
		return "", errors.New("cache miss")
	case strings.HasPrefix(line, "$"):
		var n int
		fmt.Sscanf(line, "$%d", &n)
		buf := make([]byte, n+2)
		_, err := r.Read(buf)
		if err != nil {
			return "", err
		}
		return string(buf[:n]), nil
	case strings.HasPrefix(line, "-"):
		return "", errors.New(line)
	default:
		return "", fmt.Errorf("unexpected redis response: %s", line)
	}
}

func (s *Service) ensureQueue(ctx context.Context) error {
	url := s.RabbitHTTPURL + "/api/queues/%2F/" + s.Queue
	body := bytes.NewBufferString(`{"durable":true}`)
	req, _ := http.NewRequestWithContext(ctx, http.MethodPut, url, body)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Basic "+base64.StdEncoding.EncodeToString([]byte(s.RabbitUser+":"+s.RabbitPassword)))
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		return fmt.Errorf("rabbitmq queue declare failed: %s", resp.Status)
	}
	return nil
}

func (s *Service) publish(ctx context.Context, payload []byte) error {
	url := s.RabbitHTTPURL + "/api/exchanges/%2F/amq.default/publish"
	reqBody, _ := json.Marshal(map[string]any{"properties": map[string]any{"delivery_mode": 2}, "routing_key": s.Queue, "payload": string(payload), "payload_encoding": "string"})
	req, _ := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(reqBody))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Basic "+base64.StdEncoding.EncodeToString([]byte(s.RabbitUser+":"+s.RabbitPassword)))
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 300 {
		return fmt.Errorf("rabbitmq publish failed: %s", resp.Status)
	}
	return nil
}
