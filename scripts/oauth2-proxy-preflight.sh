#!/usr/bin/env bash
# v23.4 — Check/pull the images required by OAuth2 Proxy protected tool UIs.
set -euo pipefail

OAUTH2_IMAGE="${OAUTH2_PROXY_IMAGE:-quay.io/oauth2-proxy/oauth2-proxy:v7.6.0}"
NGINX_IMAGE="${NEBULAOPS_SSO_NGINX_IMAGE:-nginx:1.27-alpine}"

pull_or_ok() {
  local image="$1"
  if docker image inspect "$image" >/dev/null 2>&1; then
    echo "OK image already present: $image"
    return 0
  fi

  echo "Pulling image: $image"
  docker pull "$image"
}

if pull_or_ok "$OAUTH2_IMAGE" && pull_or_ok "$NGINX_IMAGE"; then
  echo "OK OAuth2 Proxy prerequisites are ready"
  exit 0
fi

cat >&2 <<TIP

[error] Unable to pull one of the SSO images.

Immagini richieste:
  - $OAUTH2_IMAGE
  - $NGINX_IMAGE

The most likely WSL/Docker issue is DNS resolution toward quay.io.
Puoi provare una registry alternativa per OAuth2 Proxy:

  export OAUTH2_PROXY_IMAGE=docker.io/bitnami/oauth2-proxy:7.6.0
  ./scripts/wsl/start.sh --with-sso-proxy

Oppure pre-scarica manualmente le immagini e rilancia lo start.
TIP
exit 1
