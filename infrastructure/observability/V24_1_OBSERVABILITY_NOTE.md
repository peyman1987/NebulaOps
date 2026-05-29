# NebulaOps v24.1 Observability Note

The observability stack is expected to use live Prometheus, Grafana, Loki/Tempo and gateway diagnostics endpoints. When a tool is not reachable, NebulaOps v24.1 reports an explicit unavailable state instead of generated operational data.
