# NebulaOps v20.3 Full Platform

## UI

- Sidebar laterale collassabile/apribile.
- Albero OpenLens con Cluster, Nodes, Workloads, Config, Network, Storage, Namespaces, Events, Helm.
- Albero Docker Desktop con Containers, Images, Volumes, Builds, Dev Environments, Docker Scout.

## Build fixes

- Frontend Dockerfile usa `node:22-alpine`, immagine reale e compatibile con Angular.
- Nginx pulisce la default page e copia la build Angular in `/usr/share/nginx/html`.
