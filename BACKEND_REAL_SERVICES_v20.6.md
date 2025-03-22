# NebulaOps v20.6 Corrected — Real Services Backend

Questa versione corregge la v20.6 per rispettare il modello:

```text
Controller
  ↓
Service
  ↓
Client / Adapter
  ↓
Tool reale / API / CLI / Repository
```

## Regole applicate

- I controller non contengono script inline.
- I controller delegano solo ai service.
- I service delegano a client/adapter.
- Nessun controller deve restituire dati statistici, mock, sample o numeri inventati.
- Se un tool non è disponibile, la risposta deve essere `live:false` con `toolStatus`.
- I Dockerfile non scaricano Trivy, kubectl, Helm, ArgoCD o Terraform durante la build.
- I tool runtime devono essere disponibili tramite mount, immagine base custom o sidecar.

## OpenAPI 3

Ogni servizio espone automaticamente:

- `/v3/api-docs`
- `/swagger-ui.html`

Specifiche statiche di riferimento:

```text
docs/openapi/*.openapi.yaml
```

## Tool runtime richiesti

Per funzionalità complete installare o montare:

- `docker`
- `kubectl`
- `helm`
- `argocd`
- `terraform`
- `trivy`
- `git`
- `grep`

## Esempio mount runtime

```yaml
volumes:
  - /var/run/docker.sock:/var/run/docker.sock
  - ./.kube/config:/root/.kube/config:ro
  - ./infrastructure:/workspace/infrastructure
```

## Nota importante

Questa release evita download pesanti in Docker build. Per avere tool preinstallati usare una base image interna, ad
esempio:

```dockerfile
FROM eclipse-temurin:21-jre
COPY kubectl helm argocd terraform trivy /usr/local/bin/
```
