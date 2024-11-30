# NebulaOps v20.1 Release Notes

## Added

- New `INFRA` tab with console launchers for Grafana, Redis Commander, Mongo Express, RabbitMQ, Prometheus, Gateway API,
  Pipeline Engine API and internal feature shortcuts.
- New CI/CD Pipeline Designer module with animated canvas and glowing nodes.
- New `pipeline-engine-service` backend on port `8087`.
- GitLab export and ArgoCD sync simulation endpoints.
- New v20.1 docs for CI/CD Designer and INFRA Hub.

## Updated

- Frontend branding and local storage keys moved from v19.3 to v20.1.
- Home launcher now includes CI/CD Designer and INFRA access; INFRA is the dedicated hub for all external consoles and
  platform modules.
- Gateway now routes both DevSecOps and Pipeline Engine APIs.
- Docker Compose image names and project names updated to `nebulaops-v20-1`.

## Portfolio value

This release highlights end-to-end DevOps maturity: design pipelines visually, export GitLab YAML, simulate security
gates, build Docker images, deploy Helm charts and trigger ArgoCD-style sync from a single cockpit.
