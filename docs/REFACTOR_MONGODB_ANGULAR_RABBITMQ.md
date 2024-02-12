# Refactor Summary — MongoDB, Angular, RabbitMQ

## Goal

This version aligns the platform around a stable, professional local architecture:

- Angular for frontend delivery
- MongoDB for document persistence
- RabbitMQ as the single broker
- Redis for cache workloads
- Spring Boot and Go for backend services
- Prometheus/Grafana for observability
- Helm and Argo CD for deployment readiness

## Removed components

Kafka and Zookeeper are intentionally not included. This reduces local memory pressure, startup complexity and
operational noise while keeping a clear event-driven architecture through RabbitMQ.

## Result

The architecture is easier to run, easier to explain and better suited for a professional portfolio demonstration.
