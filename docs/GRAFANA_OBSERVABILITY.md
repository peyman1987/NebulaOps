# Observability Guide

## Purpose

NebulaOps includes an observability stack to demonstrate production-style operations. The goal is to make service
health, metrics and platform behavior visible during local execution.

## Components

| Component              | Role                                     |
|------------------------|------------------------------------------|
| Spring Actuator        | Service health and metrics endpoints     |
| Prometheus             | Metrics scraping and time-series storage |
| Grafana                | Dashboard visualization                  |
| RabbitMQ Management UI | Queue and broker inspection              |

## Local URLs

| Tool        | URL                    |
|-------------|------------------------|
| Prometheus  | http://localhost:9090  |
| Grafana     | http://localhost:3000  |
| RabbitMQ UI | http://localhost:15672 |

## Recommended dashboard panels

- service uptime
- JVM memory usage
- request rate by service
- HTTP error rate
- task API latency
- RabbitMQ queue depth
- RabbitMQ publish/consume rates
- Redis availability
- MongoDB availability

## Operational checklist

1. Start the platform.
2. Open Prometheus targets.
3. Confirm all expected targets are up.
4. Open Grafana.
5. Review service-level panels.
6. Trigger API requests.
7. Confirm metrics change after traffic.
8. Inspect RabbitMQ queue behavior.
