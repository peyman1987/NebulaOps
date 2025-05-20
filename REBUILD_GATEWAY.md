# Come applicare i fix al gateway

Il gateway Dockerfile è stato aggiornato con un multi-stage build
che installa docker CLI, kubectl e helm direttamente nell'immagine.

## Dopo aver estratto questo zip, esegui:

```bash
# Ferma lo stack
./scripts/wsl/stop.sh

# Rebuilda il gateway con --no-cache (obbligatorio)
./scripts/wsl/restart-gateway.sh

# Oppure riparti da zero
./scripts/wsl/start.sh --rebuild-gateway
```

## Perché i container/immagini mostravano 0

Il binary `docker` in `.runtime-tools/` è compilato per l'host ma
potrebbe non funzionare dentro il container eclipse-temurin:21-jre
se mancano librerie condivise. Il nuovo Dockerfile copia il binary
direttamente dall'immagine ufficiale `docker:cli` che è compatibile.

## Dopo il rebuild

- `GET /api/runtime/docker/containers` restituirà i container reali
- `GET /api/runtime/docker/images` restituirà le immagini reali
- `GET /api/kubernetes/snapshot` restituirà le risorse del cluster se
  kubectl è configurato con `.kube/config`
