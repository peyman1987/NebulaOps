# Interview Story

## 60-second pitch

NebulaOps is a cloud-native microservice platform built as a senior portfolio project. It uses Angular, Spring Boot, Go,
MongoDB, RabbitMQ, Redis, Docker, Prometheus, Grafana, Helm, GitLab CI and Argo CD. The design focuses on clear service
boundaries, a stable event-driven architecture, local developer productivity and deployment readiness.

## Architecture explanation

The frontend communicates with a Spring Cloud Gateway. The gateway routes traffic to domain services such as Auth, Task,
File and Notification. Task Service persists data in MongoDB and publishes events to RabbitMQ. Consumers process
asynchronous work, Redis supports cache-oriented access patterns, and Prometheus/Grafana provide observability.

## Why RabbitMQ

RabbitMQ is a strong fit for this system because the platform needs durable work queues and event delivery, not
large-scale log streaming. It is lighter to run locally, easier to inspect through the management UI and appropriate for
task/notification workflows.

## What this project proves

- ability to design a distributed architecture
- ability to implement polyglot services
- ability to operate a local cloud-native stack
- ability to document engineering decisions professionally
- ability to prepare Kubernetes and GitOps deployment assets

## Strong demo path

1. Start the system with Docker Compose.
2. Open the animated architecture diagram.
3. Show the frontend dashboard.
4. Call APIs through the gateway.
5. Inspect RabbitMQ queues.
6. Show Prometheus targets.
7. Show Grafana dashboards.
8. Explain Helm and Argo CD delivery.
