# KubeBridge

NebulaOps v23.1 extension implemented as a Spring Boot MVC application.

## Runtime

- Framework: Spring Boot MVC + Thymeleaf
- HTTP port: 8080
- Health endpoint: `/healthz`
- Live data endpoint: `/api/live`
- Kubernetes NodePort: `31111`

## Data policy

This extension uses only live data from configured integrations. If the Kubernetes API, service endpoint, credentials or mounted directory are not available, the UI reports `NOT_CONFIGURED`, `DEGRADED` or `UNAVAILABLE`.

## Source path

```text
extensions/kubebridge
```
