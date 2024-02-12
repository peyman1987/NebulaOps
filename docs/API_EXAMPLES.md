# API Examples

These examples are designed for local Docker execution.

## Health checks

```bash
curl -i http://localhost:8080/actuator/health
curl -i http://localhost:8081/actuator/health
curl -i http://localhost:8082/actuator/health
curl -i http://localhost:8083/actuator/health
curl -i http://localhost:8084/actuator/health
curl -i http://localhost:8091/health
```

## Task service through gateway

```bash
curl -i http://localhost:8080/api/tasks
```

Create a task:

```bash
curl -i -X POST http://localhost:8080/api/tasks   -H 'Content-Type: application/json'   -d '{"title":"Prepare cloud portfolio demo","description":"Validate services, queues and dashboards","status":"OPEN"}'
```

## Go cache service

```bash
curl -i http://localhost:8091/health
curl -i http://localhost:8091/cache/demo-key
```

## RabbitMQ verification

Open the management UI:

```text
http://localhost:15672
```

Default local credentials:

```text
guest / guest
```

Use RabbitMQ to inspect queues, consumers and message rates during the task workflow.
