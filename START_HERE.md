# Start Here — NebulaOps v20.2

1. Controlla Terraform:

```bash
ls terraform
./scripts/terraform/plan-local.sh
```

2. Genera i file locali:

```bash
./scripts/terraform/apply-local.sh
```

3. Avvia la piattaforma:

```bash
./scripts/local-up.sh
```

4. Entra nel frontend con `admin/admin` e visita i tab `TERRAFORM`, `KUBERNETES`, `OBSERVABILITY`, `FINOPS`, `BACKUPS`.

5. Consulta la documentazione:

```bash
cat docs/README_V19_3_INDEX.md
```

## v20.2 AI Ops Center

- New `AI OPS` tab with futuristic cockpit UI.
- Spring Boot `ai-ops-service` plus Python FastAPI `ai-engine`.
- Visual RCA, realtime timeline, animated dependency graph and safe `AUTO FIX` remediation staging.
- See `docs/V19_1_AI_OPS_CENTER.md` and `docs/V19_1_RELEASE_NOTES.md`.

## v20.2 docs aggiornati

- `docs/README_V19_3_INDEX.md`
- `docs/V19_3_RELEASE_NOTES.md`
- `docs/V19_3_DEVSECOPS_MODULE.md`
- `docs/V19_3_KUBERNETES_VISUAL_CLUSTER.md`
- `docs/V19_3_AI_OPS_CENTER.md`
- `docs/V19_3_DIAGRAMS.md`
- `docs/diagrams/README.md`
