# Go, Redis and RabbitMQ Design

## Goal

The Go services demonstrate lightweight infrastructure-oriented workloads inside a polyglot microservice platform.

## Go Cache Service

The Go Cache Service is responsible for Redis-backed access patterns. It is intentionally small and fast, making it easy
to inspect, test and extend.

Typical responsibilities:

- expose cache health endpoints
- read/write lightweight cache entries
- demonstrate Go service packaging in Docker
- integrate with Redis using a small runtime footprint

## Go Event Worker

The Go Event Worker represents a foundation for background consumers. In a production version, it can consume RabbitMQ
queues, perform idempotent operations and update external integrations.

## Redis usage

Redis is used for:

- low-latency cache reads
- temporary state
- future rate-limiting primitives
- future session/cache extension points

## RabbitMQ usage

RabbitMQ is used for:

- durable domain events
- notification queues
- worker integration
- future retry and dead-letter queues

## Recommended hardening

- define explicit exchanges and routing keys
- add dead-letter exchanges
- make consumers idempotent
- use correlation IDs
- add structured JSON logs
- expose worker metrics
- configure consumer prefetch limits
