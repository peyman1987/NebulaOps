# NebulaOps v20.4.1 Live Only Fix

Questa patch rimuove i principali dati statici/mock dal frontend.

## Cambiamenti

- Observability inizializzata vuota: si popola solo da `/api/platform/observability`.
- GitOps inizializzato come `Unavailable`: si popola solo da `/api/platform/gitops`.
- DevSecOps inizializzato vuoto: scansioni, CVE, compliance e threat map arrivano da `/api/platform/devsecops`.
- OpenLens Network/Storage/Endpoints non usa più righe hardcoded.
- Docker Desktop Builds/Dev Environments usa immagini Docker e namespace live invece di esempi statici.
- I pulsanti `Run`, `Sync`, `Scan`, `Restart` ora ricaricano dati live invece di modificare numeri random nel browser.

Se Docker socket, kubectl, Trivy o ArgoCD non sono disponibili, la UI mostra liste vuote o stato `Unavailable` invece di
dati falsi.
