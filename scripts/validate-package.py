#!/usr/bin/env python3
from pathlib import Path
import json
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors = []

def check_file(path: str) -> None:
    if not (ROOT / path).exists():
        errors.append(f"missing: {path}")

required_files = [
    "README.md",
    "docs/architecture-animated.svg",
    "docs/diagrams/runtime-architecture.svg",
    "docs/diagrams/gitlab-argocd-flow.svg",
    "docs/diagrams/messaging-cache-flow.svg",
    "docs/diagrams/kubernetes-helm-view.svg",
    "docs/diagrams/request-flow-sequence.svg",
    "docs/diagrams/service-port-map.svg",
    "docs/TECHNICAL_DOCUMENTATION.md",
    "docs/GITLAB_ARGOCD.md",
    "docs/TROUBLESHOOTING.md",
    "infrastructure/docker-compose.yml",
    "infrastructure/helm/nebulaops/Chart.yaml",
    "infrastructure/observability/prometheus/prometheus.yml",
    "infrastructure/observability/grafana/dashboards/nebulaops-overview.json",
    "infrastructure/argocd/project.yaml",
    "infrastructure/argocd/application.yaml",
    "infrastructure/argocd/applicationset.yaml",
    ".gitlab-ci.yml",
    "frontend/package.json",
    "backend/pom.xml",
    "go/cache-service/go.mod",
    "go/cache-service/cmd/server/main.go",
    "go/event-worker/go.mod",
    "go/event-worker/cmd/worker/main.go",
    "scripts/wsl/docker-cache-repair.sh",
]

for path in required_files:
    check_file(path)

for svg_path in [
    "docs/architecture-animated.svg",
    "docs/diagrams/runtime-architecture.svg",
    "docs/diagrams/gitlab-argocd-flow.svg",
    "docs/diagrams/messaging-cache-flow.svg",
    "docs/diagrams/kubernetes-helm-view.svg",
    "docs/diagrams/request-flow-sequence.svg",
    "docs/diagrams/service-port-map.svg",
]:
    try:
        ET.parse(ROOT / svg_path)
    except Exception as exc:
        errors.append(f"invalid SVG XML {svg_path}: {exc}")

for json_path in [
    "infrastructure/observability/grafana/dashboards/nebulaops-overview.json",
    "infrastructure/helm/nebulaops/files/dashboards/nebulaops-overview.json",
]:
    if (ROOT / json_path).exists():
        try:
            json.loads((ROOT / json_path).read_text())
        except Exception as exc:
            errors.append(f"invalid JSON {json_path}: {exc}")

try:
    import yaml  # type: ignore
    for yaml_path in [
        ".gitlab-ci.yml",
        "infrastructure/docker-compose.yml",
        "docker-compose.yml",
        "infrastructure/observability/prometheus/prometheus.yml",
        "infrastructure/argocd/project.yaml",
        "infrastructure/argocd/application.yaml",
        "infrastructure/argocd/applicationset.yaml",
    ]:
        with open(ROOT / yaml_path, "r", encoding="utf-8") as f:
            list(yaml.safe_load_all(f))
except ModuleNotFoundError:
    print("PyYAML not installed; skipping deep YAML validation")
except Exception as exc:
    errors.append(f"invalid YAML: {exc}")

readme = (ROOT / "README.md").read_text()
required_terms = ["Angular", "MongoDB", "RabbitMQ", "Helm", "Grafana", "WSL", "Go", "Redis", "RabbitMQ", "GitLab", "Argo CD", "Prometheus"]
for term in required_terms:
    if term not in readme:
        errors.append(f"README missing term: {term}")


try:
    import yaml  # type: ignore
    prometheus = yaml.safe_load((ROOT / "infrastructure/observability/prometheus/prometheus.yml").read_text())
    jobs = {job.get("job_name") for job in prometheus.get("scrape_configs", [])}
    for job in ["gateway-service", "auth-service", "task-service", "notification-service", "file-service", "go-cache-service"]:
        if job not in jobs:
            errors.append(f"Prometheus missing scrape job: {job}")
except Exception as exc:
    errors.append(f"Prometheus semantic validation failed: {exc}")

for compose_path in ["docker-compose.yml", "infrastructure/docker-compose.yml"]:
    compose = (ROOT / compose_path).read_text()
    for volume in ["mongo-data:", "redis-data:", "rabbitmq-data:"]:
        if volume not in compose:
            errors.append(f"{compose_path} missing volume declaration: {volume}")

tech_doc = (ROOT / "docs/TECHNICAL_DOCUMENTATION.md").read_text(encoding="utf-8")
for term in required_terms:
    if term not in tech_doc:
        errors.append(f"TECHNICAL_DOCUMENTATION missing term: {term}")
for diagram in [
    "runtime-architecture.svg",
    "gitlab-argocd-flow.svg",
    "messaging-cache-flow.svg",
    "kubernetes-helm-view.svg",
    "request-flow-sequence.svg",
    "service-port-map.svg",
]:
    if diagram not in readme:
        errors.append(f"README missing diagram reference: {diagram}")
    if diagram not in tech_doc:
        errors.append(f"TECHNICAL_DOCUMENTATION missing diagram reference: {diagram}")

# Frontend shell Docker image and template must include every shell-declared same-origin remote.
try:
    dockerfile = (ROOT / "frontend/Dockerfile").read_text(encoding="utf-8")
    shell = (ROOT / "frontend/src/app/app.component.ts").read_text(encoding="utf-8")
    template = (ROOT / "frontend/src/app/app.component.html").read_text(encoding="utf-8")
    import re
    remotes_match = re.search(r"readonly\s+remotes:\s+RemoteDefinition\[\]\s*=\s*\[(.*?)\]\s+as\s+RemoteDefinition\[\]", shell, re.S)
    if not remotes_match:
        errors.append("frontend shell remotes array not found")
        remote_block = ""
    else:
        remote_block = remotes_match.group(1)
        if re.search(r",\s*,", remote_block):
            errors.append("frontend shell remotes array contains an empty slot; this breaks Array.find navigation at runtime")
    remote_slugs = sorted(set(re.findall(r"['\"]route['\"]?\s*:\s*['\"]/remotes/([^/]+)/", remote_block)))
    remote_tags = sorted(set(re.findall(r"['\"]tag['\"]?\s*:\s*['\"]([^'\"]+)", remote_block)))
    if len(remote_slugs) != len(remote_tags):
        errors.append(f"frontend shell remote slug/tag count mismatch: {len(remote_slugs)} slugs, {len(remote_tags)} tags")
    for slug in remote_slugs:
        expected = f"COPY remotes/{slug}/dist/browser/ /usr/share/nginx/html/remotes/{slug}/"
        if expected not in dockerfile:
            errors.append(f"frontend Dockerfile missing same-origin remote copy: {slug}")
    for tag in remote_tags:
        if f"<{tag}" not in template:
            errors.append(f"frontend template missing custom element host for remote tag: {tag}")
except Exception as exc:
    errors.append(f"frontend remote copy/template validation failed: {exc}")

if errors:
    print("Package validation FAILED")
    for err in errors:
        print(" -", err)
    sys.exit(1)

print("Package validation OK")
print("v9 Go/Redis/RabbitMQ files present")
print("v10 GitLab/Argo CD files present")
print("v11 docs and SVG diagrams aligned")
