# Refactor Notes: MongoDB + Angular + Kafka

## Frontend

The previous Angular frontend was replaced with Angular 18 using standalone components and Angular CDK Drag & Drop. The
UI is designed as a senior portfolio dashboard with a glassmorphism dark theme, Kanban board, metrics and responsive
layout.

## Backend

JPA/MongoDB dependencies were removed from the service POMs and replaced with `spring-boot-starter-data-mongodb`. Kafka
was replaced by Spring Kafka.

## MongoDB Modeling

Task documents are stored as flexible aggregate roots:

```json
{
  "organizationId": "demo-org",
  "projectId": "portfolio",
  "title": "Kafka task event contract",
  "status": "IN_PROGRESS",
  "priority": "CRITICAL",
  "labels": ["event-driven", "portfolio"]
}
```

## Kafka Event Contract

Topic: `nebula.task.events`

```json
{
  "eventId": "uuid",
  "type": "TaskCreated",
  "taskId": "mongo-object-id",
  "organizationId": "demo-org",
  "occurredAt": "2026-05-17T10:00:00Z"
}
```

## Local Platform

`docker compose up --build` starts MongoDB, Kafka, all Spring Boot services and the Angular frontend behind Nginx.

## Author

**Peyman Eshghi Malayeri**  
Email: peyman_em@yahoo.com  
Project Year: 2024
