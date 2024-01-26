# Interview Story

## 30-second pitch

NebulaOps is a cloud-native workflow orchestration platform I built as a senior-level portfolio project. It uses Spring
Boot microservices, Angular, MongoDB, Kafka, Docker, Kubernetes and Terraform. I focused on architecture, security,
CI/CD, observability and AWS deployment readiness.

## Architecture explanation

I split the system into bounded contexts: Auth, Task, File and Notification. The API Gateway exposes a single entry
point. Task operations emit events through Kafka, and Notification Service consumes them asynchronously. This keeps the
user-facing API fast and decouples side effects.

## Senior points to emphasize

- I designed the service boundaries around business capabilities.
- I used organization-based multi-tenancy to model SaaS behavior.
- I separated runtime configuration from source code.
- I prepared Kubernetes and Terraform assets for cloud deployment.
- I documented production risks and improvements.
- I included observability considerations from the beginning.

## Trade-offs

For the portfolio version, I kept the implementation simple enough to run locally. In production I would add centralized
identity, stricter RBAC, externalized secrets, network policies, dead-letter queues, tracing collectors and full
integration tests.

## Future evolution

- Add OpenTelemetry traces end-to-end
- Add Grafana dashboards as code
- Add ArgoCD GitOps deployment
- Add Helm charts
- Add service mesh with Istio or Linkerd
- Add AI assistant for task summarization

## Author

**Peyman Eshghi Malayeri**  
Email: peyman_em@yahoo.com  
Project Year: 2024
