#!/usr/bin/env bash
set -euo pipefail
cat <<'INFO'
Observability endpoints after ./scripts/local-up.sh:
  Grafana:    http://localhost:3000  login admin/admin
  Prometheus: http://localhost:9090
  Gateway:    http://localhost:8080/actuator/health
  Task metrics endpoint: http://localhost:8082/actuator/prometheus

Open Grafana, go to Dashboards → NebulaOps → NebulaOps Platform Overview.
INFO
