# Grafana & Observability Guide

NebulaOps includes a local observability stack based on Prometheus and Grafana.

## Local Docker Compose

Start the platform:

```bash
./scripts/local-up.sh
```

Open:

```text
Grafana:    http://localhost:3000
Prometheus: http://localhost:9090
```

Default Grafana credentials:

```text
admin / admin
```

## Provisioned assets

```text
infrastructure/observability/prometheus/prometheus.yml
infrastructure/observability/grafana/provisioning/datasources/datasource.yml
infrastructure/observability/grafana/provisioning/dashboards/dashboard.yml
infrastructure/observability/grafana/dashboards/nebulaops-overview.json
```

Grafana automatically loads:

- Prometheus datasource
- NebulaOps dashboard folder
- NebulaOps Platform Overview dashboard

## Metrics exposed by services

Each Spring Boot service exposes actuator endpoints:

```text
/actuator/health
/actuator/metrics
/actuator/prometheus
```

Prometheus scrapes:

- gateway-service:8080
- auth-service:8081
- task-service:8082
- notification-service:8083
- file-service:8084

## Useful PromQL examples

Request throughput:

```promql
sum(rate(http_server_requests_seconds_count[1m])) * 60
```

p95 latency:

```promql
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, application))
```

JVM memory:

```promql
sum(jvm_memory_used_bytes) by (application, area)
```

## Portfolio explanation

This observability layer demonstrates operational maturity:

- services expose health and metrics
- Prometheus scrapes metrics automatically
- Grafana dashboards are provisioned as code
- the same observability model exists in Docker Compose and Helm

## Author

**Peyman Eshghi Malayeri**  
Email: peyman_em@yahoo.com  
Project Year: 2024
