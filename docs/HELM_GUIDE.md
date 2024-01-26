# Helm Deployment Guide

NebulaOps now includes a first-class Helm chart for Kubernetes deployment.

## Location

```text
infrastructure/helm/nebulaops
```

## What the chart deploys

- Angular frontend
- Spring Cloud Gateway
- Auth Service
- Task Service
- File Service
- Notification Service
- MongoDB StatefulSet
- Kafka broker in KRaft mode
- Prometheus
- Grafana with provisioned datasource and dashboard

## Render manifests without installing

```bash
./scripts/helm-render.sh
```

The rendered output is saved to:

```text
infrastructure/helm/rendered-nebulaops.yaml
```

## Install locally on Kubernetes

Prerequisites:

- Docker Desktop Kubernetes, Minikube, Kind or another local cluster
- Helm 3
- kubectl

```bash
./scripts/helm-install-local.sh
```

## Useful port-forwards

```bash
kubectl -n nebulaops port-forward svc/nebulaops-frontend 4200:4200
kubectl -n nebulaops port-forward svc/nebulaops-gateway 8080:8080
kubectl -n nebulaops port-forward svc/nebulaops-grafana 3000:3000
kubectl -n nebulaops port-forward svc/nebulaops-prometheus 9090:9090
```

## Production notes

For a real AWS/EKS deployment, replace the local dependencies with managed services:

- MongoDB Atlas or Amazon DocumentDB-compatible layer, depending on constraints
- Amazon MSK for Kafka
- Amazon Managed Prometheus or self-managed Prometheus Operator
- Grafana Cloud or Amazon Managed Grafana
- External Secrets Operator for secrets
- AWS Load Balancer Controller for ingress

## Important interview point

The chart shows that the project is not just application code. It includes repeatable deployment, environment
configuration, service discovery, probes, metrics scraping and dashboard provisioning.

## Author

**Peyman Eshghi Malayeri**  
Email: peyman_em@yahoo.com  
Project Year: 2024
