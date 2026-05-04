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
    "extensions/apiforge/pom.xml",
    "extensions/apiforge/Dockerfile",
    "extensions/apiforge/src/main/resources/application.properties",
    "infrastructure/kubernetes/apiforge.yaml",
    "scripts/wsl/deploy-apiforge-k8s.sh",
    "scripts/wsl/deploy-extensions-k8s.sh",
    "scripts/wsl/undeploy-extensions-k8s.sh",
    "scripts/verify-live-only-runtime.py",
    "extensions/extensions.manifest.json",
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
        "infrastructure/kubernetes/apiforge.yaml",
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

# NebulaOps extensions must be controlled from the separate EXTENSIONS panel, not as APP BAR cards.
try:
    shell = (ROOT / "frontend/src/app/app.component.ts").read_text(encoding="utf-8")
    template = (ROOT / "frontend/src/app/app.component.html").read_text(encoding="utf-8")
    ext_panel = (ROOT / "frontend/src/assets/nebulaops-extension-control-panel.js").read_text(encoding="utf-8")
    dist_index = (ROOT / "frontend/dist/nebulaops/browser/index.html").read_text(encoding="utf-8")
    manifest = json.loads((ROOT / "extensions/extensions.manifest.json").read_text(encoding="utf-8"))
    apiprops = (ROOT / "extensions/apiforge/src/main/resources/application.properties").read_text(encoding="utf-8")
    if '"group": "Extensions"' in shell or "group: 'Extensions'" in shell:
        errors.append("APP BAR serviceLinks must not contain Extensions cards; use the EXTENSIONS panel instead")
    if "neb-extensions-trigger" not in template:
        errors.append("Sidebar is missing the separate EXTENSIONS button")
    if "nebulaops-extension-appbar-controls.js" in dist_index:
        errors.append("Legacy nested APP BAR extension control script is still loaded")
    if "nebulaops-extension-control-panel.js" not in dist_index:
        errors.append("EXTENSIONS control panel script is not loaded in frontend dist index")
    for item in manifest:
        title = item["title"]
        slug = item["slug"]
        if slug not in ext_panel:
            errors.append(f"EXTENSIONS panel missing installed extension slug: {slug}")
        for action in ["start", "stop", "restart", "status", "open"]:
            if action not in ext_panel:
                errors.append(f"EXTENSIONS panel missing action {action} for {slug}")
        if item.get("enabledByDefault") is not False or item.get("defaultState") != "DISABLED":
            errors.append(f"Extension {slug} must be disabled by default in extensions.manifest.json")
        k8s_path = ROOT / item["kubernetesManifest"]
        k8s = k8s_path.read_text(encoding="utf-8")
        if f"name: {slug}" not in k8s or f"nodePort: {item['nodePort']}" not in k8s:
            errors.append(f"Kubernetes manifest missing resource names or NodePort for extension: {slug}")
        if "replicas: 0" not in k8s:
            errors.append(f"Kubernetes manifest must keep extension disabled by default with replicas: 0: {slug}")
    if "server.servlet.context-path=/apiforge" not in apiprops:
        errors.append("APIForge must run with /apiforge context path")
    if "NebulaOps v23.3 extension theme override" not in (ROOT / "extensions/apiforge/src/main/resources/static/css/app.css").read_text(encoding="utf-8"):
        errors.append("APIForge is missing NebulaOps extension theme override")
except Exception as exc:
    errors.append(f"NebulaOps extension validation failed: {exc}")

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

# v23.3 release hygiene: visible/runtime version identifiers must be aligned.
try:
    ignored = {
        "frontend/package-lock.json",  # dependency versions can legitimately contain 22.x.
        "scripts/wsl/stop.sh",         # intentionally removes legacy compose projects.
        "scripts/validate-package.py", # contains stale-pattern literals for this validation itself.
        "scripts/wsl/purge-stale-release-assets.sh", # contains the known stale filenames it removes.
    }
    ignored_suffixes = ("package-lock.json",)
    stale_text_patterns = ["v23.1", "V23.1", "23_1", "v23-1", "nebulaops-v23-1", "N22.5", "N22.2", "v22-5", "v22-3"]
    stale_path_patterns = ["NebulaOps_v22", "docker-compose.v22", "v22-", "V22-", "v22.", "V22.", "N22"]
    internal_registry_patterns = ["packages.applied-caas-gateway", "internal.api.openai.org", "artifactory/api/npm/npm-public"]
    for path in ROOT.rglob("*"):
        if not path.is_file() or any(part in {".git", "target", "dist"} for part in path.parts):
            continue
        rel = path.relative_to(ROOT).as_posix()
        if rel in ignored:
            continue
        for stale in stale_path_patterns:
            if stale in rel:
                errors.append(f"stale release artifact path {stale} found in {rel}")
        try:
            file_text = path.read_text(encoding="utf-8")
        except Exception:
            continue
        for marker in internal_registry_patterns:
            if marker in file_text:
                errors.append(f"internal build registry reference found in {rel}: {marker}")
        if rel.endswith(ignored_suffixes):
            continue
        for stale in stale_text_patterns:
            if stale in file_text:
                errors.append(f"stale release identifier {stale} found in {rel}")
except Exception as exc:
    errors.append(f"v23.3 release hygiene validation failed: {exc}")

# v23.3 release manifest checks: prevent add-on manifests from keeping old application versions.
try:
    release_manifest_checks = {
        "infrastructure/helm/addons/nebulaops-spring-mvc-addon/Chart.yaml": ["appVersion: \"23.3.0\""],
        "infrastructure/kubernetes/addons/spring-mvc-service.yaml": ["app.kubernetes.io/version: \"23.3.0\""],
    }
    for rel, expected_terms in release_manifest_checks.items():
        target = ROOT / rel
        if not target.exists():
            errors.append(f"missing release manifest: {rel}")
            continue
        text = target.read_text(encoding="utf-8")
        for term in expected_terms:
            if term not in text:
                errors.append(f"release manifest {rel} missing expected term: {term}")
except Exception as exc:
    errors.append(f"v23.3 release manifest validation failed: {exc}")

if errors:
    print("Package validation FAILED")
    for err in errors:
        print(" -", err)
    sys.exit(1)

print("Package validation OK")
print("v9 Go/Redis/RabbitMQ files present")
print("v10 GitLab/Argo CD files present")
print("v23.3 runtime stability and release hygiene checks aligned")
