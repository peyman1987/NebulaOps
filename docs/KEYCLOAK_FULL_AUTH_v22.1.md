# NebulaOps v22.1 — Keycloak full authentication provider

Questa patch configura Keycloak come authentication provider centrale della piattaforma.

## Cosa è protetto

- Frontend Angular: login OIDC Authorization Code + PKCE con client `nebulaops-frontend`.
- Gateway Spring Boot: resource server JWT; propaga `Authorization: Bearer ...` ai servizi downstream.
- Tutti i servizi Spring Boot: resource server JWT, validazione JWKS Keycloak, CORS e ruoli realm convertiti in authority Spring.
- Go cache service: middleware JWT RS256/JWKS sulle API HTTP, con `/health` pubblico.
- AI engine FastAPI: validazione Bearer JWT su `/analyze`, con `/health` pubblico.
- GitLab: login OIDC tramite client Keycloak `gitlab`.
- Grafana: Generic OAuth tramite client Keycloak `grafana`.

## Utenti di sviluppo

- `admin / admin` con ruolo `nebula-admin`
- `peyman / admin` con ruolo `nebula-operator`

## Client Keycloak principali

- `nebulaops-frontend`: client pubblico con PKCE.
- `nebulaops-gateway`: client confidential per gateway/service account.
- `nebulaops-api`: client confidential per smoke test e automazioni API.
- `gitlab`: client confidential per GitLab OIDC.
- `grafana`: client confidential per Grafana Generic OAuth.
- `nebulaops-oauth2-proxy`: client predisposto per proteggere tool non-OIDC dietro oauth2-proxy.

## Avvio

```bash
docker compose down --remove-orphans
docker compose up -d --build
```

Aprire:

- Frontend: http://localhost:4200
- Keycloak: http://localhost:8180
- Grafana: http://localhost:3000
- GitLab: http://localhost:8929

## Smoke test

```bash
./scripts/keycloak-smoke-test.sh
```

Il test richiede un token da Keycloak, verifica che una chiamata senza token venga rifiutata e poi prova una chiamata autenticata verso il gateway.

## Nota sui tool esterni

GitLab e Grafana hanno integrazione OIDC nativa. Tool come Mongo Express, Redis Commander, Prometheus e RabbitMQ Management possono essere messi dietro `oauth2-proxy` usando il client `nebulaops-oauth2-proxy`; alcuni di questi tool non espongono un'integrazione OIDC nativa completa nella loro immagine Docker standard.
