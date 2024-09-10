# NebulaOps v19.3 Release Notes

## Highlight

NebulaOps v19.3 introduce il **DevSecOps Module**: una console visuale per sicurezza, compliance e vulnerabilità,
pensata per mostrare competenze senior Cloud/DevOps/Security.

## Nuove feature

- Tab `SECURITY`, `COMPLIANCE`, `VULNERABILITIES`.
- Scanner dashboard per Trivy, Docker image scan, SAST, dependency vulnerabilities e secrets detection.
- Animated risk score.
- Radar animations e threat map.
- Critical alerts pulsanti.
- CVE dashboard con remediation/fix version.
- Compliance board CIS, NIST, SOC2, ISO 27001.
- Nuovo `devsecops-service` Spring Boot.
- Nuovo SVG architetturale DevSecOps.

## Note

La v19.3 mantiene la modalità demo-safe local-first. Gli scanner sono modellati come dashboard e API pronte da collegare
a tool reali come Trivy, Gitleaks, Semgrep, Dependency-Check, Snyk, Kyverno o OPA Gatekeeper.

## v19.3 Corrected - Home Feature Launcher

La home ora include un Command Center con tasti grandi per aprire rapidamente Grafana, ArgoCD, Prometheus e i moduli
interni AI OPS, Kubernetes Visual Cluster, Security, Helm e Observability.

Documentazione: `docs/V19_3_HOME_FEATURE_LAUNCHER.md`.
