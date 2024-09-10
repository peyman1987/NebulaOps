# NebulaOps v19.3 — DevSecOps Module

Versione: **19.3**

La v19.3 aggiunge un cockpit DevSecOps completo per portfolio senior: SECURITY, COMPLIANCE e VULNERABILITIES diventano
tab operativi con UI cyber, radar animation, threat map, critical alerts pulsanti e animated risk score.

## FE

- Tab principali: `SECURITY`, `COMPLIANCE`, `VULNERABILITIES`.
- Radar animato con sweep e threat points.
- Threat map per secrets leak, image CVE, ingress risk e dependency drift.
- Risk score animato.
- Critical alerts pulsanti.
- Scanner cards per Trivy, Docker image scan, SAST, secrets detection e dependency vulnerabilities.
- CVE dashboard con severity e fix version.
- Compliance board CIS Kubernetes, NIST, SOC2 e ISO 27001.

## BE

Nuovo microservice:

- `backend/devsecops-service`
- Spring Boot 3.3 + Actuator + Prometheus metrics
- endpoint demo-safe:
    - `GET /api/devsecops/dashboard`
    - `POST /api/devsecops/scan`

## Production wiring consigliato

Per renderlo reale al 100% collegare:

- Trivy CLI/API per image e filesystem scan.
- OWASP Dependency-Check o Snyk per dipendenze.
- Gitleaks per secrets detection.
- Semgrep/SonarQube per SAST.
- SBOM CycloneDX/SPDX.
- Policy-as-code con OPA/Gatekeeper o Kyverno.

## File aggiornati

- `frontend/src/app/app.component.ts`
- `frontend/src/app/app.component.html`
- `frontend/src/app/app.component.css`
- `frontend/src/assets/nebulaops-v19-3-devsecops-module.svg`
- `docs/diagrams/nebulaops-v19-3-devsecops-module.svg`
- `backend/devsecops-service/**`
- `docker-compose.yml`
- `backend/pom.xml`
