> Enterprise Cloud-Native Portfolio Project  
> Developed by Peyman Eshghi Malayeri (2024)  
> Contact: peyman_em@yahoo.com

# NebulaOps Diagram Guide

This folder contains SVG diagrams aligned with the current v11 implementation.

## Diagrams

| File                       | Explains                                   |
|----------------------------|--------------------------------------------|
| `runtime-architecture.svg` | End-to-end runtime architecture            |
| `gitlab-argocd-flow.svg`   | CI/CD and GitOps delivery flow             |
| `messaging-cache-flow.svg` | Kafka, RabbitMQ and Redis responsibilities |
| `kubernetes-helm-view.svg` | Helm workloads inside Kubernetes           |

## How to use them in portfolio

Use the runtime diagram in the README, the GitLab/Argo CD diagram during DevOps discussion, and the messaging diagram
when explaining why Kafka and RabbitMQ coexist.

- `request-flow-sequence.svg` — end-to-end API/event/cache/queue sequence.
- `service-port-map.svg` — local ports and runtime responsibilities.