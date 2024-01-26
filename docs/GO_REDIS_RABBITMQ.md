# Go + Redis + RabbitMQ Extension

NebulaOps v9 adds two Go services to show polyglot cloud engineering.

## Services

### go-cache-service

A small HTTP service written in Go. It stores hot dashboard/task values in Redis and publishes a cache event to
RabbitMQ.

Endpoints:

```bash
curl http://localhost:8091/health

curl -X PUT http://localhost:8091/cache/dashboard:summary \
  -H 'content-type: application/json' \
  -d '{"value":"{\"openTasks\":12}","ttlSeconds":120}'

curl http://localhost:8091/cache/dashboard:summary
```

### go-event-worker

A background worker written in Go. It consumes `nebula.cache.events` from RabbitMQ and acknowledges processed messages.

## Why both Kafka and RabbitMQ?

Kafka is used for domain events and event streaming between business services. RabbitMQ is used for command/work queues
where acknowledgements, retries and worker semantics are more natural.

## Redis

Redis is used for short-lived cache entries, such as dashboard summaries and expensive task aggregations.

## Author

**Peyman Eshghi Malayeri**  
Email: peyman_em@yahoo.com  
Project Year: 2024
