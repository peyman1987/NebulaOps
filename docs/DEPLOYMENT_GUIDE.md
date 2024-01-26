# NebulaOps Deployment Guide

NebulaOps supports two deployment modes:

1. Docker Compose for local development
2. Helm for Kubernetes/cloud deployment

## 1. Local Docker Compose

```bash
chmod +x scripts/*.sh
./scripts/verify-local.sh
./scripts/local-up.sh
```

Services:

| Service              | URL                   |
|----------------------|-----------------------|
| Angular frontend     | http://localhost:4200 |
| Gateway              | http://localhost:8080 |
| Auth service         | http://localhost:8081 |
| Task service         | http://localhost:8082 |
| Notification service | http://localhost:8083 |
| File service         | http://localhost:8084 |
| MongoDB              | localhost:27017       |
| Kafka                | localhost:9092        |
| Prometheus           | http://localhost:9090 |
| Grafana              | http://localhost:3000 |

Grafana login:

```text
admin / admin
```

## 2. Smoke Test

```bash
./scripts/smoke-test.sh
```

The test creates a task via Gateway and verifies that the platform path is alive.

## 3. Kubernetes with Helm

Render manifests:

```bash
./scripts/helm-render.sh
```

Install or upgrade:

```bash
./scripts/helm-install-local.sh
```

Port-forward:

```bash
kubectl -n nebulaops port-forward svc/nebulaops-frontend 4200:4200
kubectl -n nebulaops port-forward svc/nebulaops-gateway 8080:8080
kubectl -n nebulaops port-forward svc/nebulaops-grafana 3000:3000
kubectl -n nebulaops port-forward svc/nebulaops-prometheus 9090:9090
```

## 4. AWS/EKS Production Direction

Recommended production version:

- EKS for workloads
- Amazon MSK for Kafka
- MongoDB Atlas or a managed document database strategy
- AWS Load Balancer Controller
- External Secrets Operator
- AWS Secrets Manager
- Amazon ECR for images
- Route53 + ACM for TLS
- Grafana Cloud or Amazon Managed Grafana
- Prometheus Operator or Amazon Managed Prometheus

## 5. Release Workflow

Suggested CI/CD pipeline:

```text
Pull Request
  -> Angular build
  -> Java tests
  -> Docker image build
  -> security scan
  -> push images
  -> helm lint
  -> helm template
  -> deploy to staging
  -> smoke tests
  -> production approval
```

## Author

**Peyman Eshghi Malayeri**  
Email: peyman_em@yahoo.com  
Project Year: 2024
