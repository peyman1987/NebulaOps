# ── Stage 1: Build Angular Element remote + shared MFE library ──────
# Build context must be ./frontend so the shared library under libs/ is available inside the remote workspace.
FROM node:20-alpine AS builder
WORKDIR /app/remotes/docker-desktop
COPY remotes/docker-desktop/package.json ./
RUN npm install --legacy-peer-deps
COPY remotes/docker-desktop/ ./
COPY libs/ ./libs/
# ng build + postbuild (generates remoteEntry.js from main.js + polyfills.js)
RUN npm run build

# ── Stage 2: Serve via nginx ───────────────────────────────
FROM nginx:1.27-alpine
COPY remotes/docker-desktop/nginx.conf /etc/nginx/conf.d/default.conf
RUN rm -rf /usr/share/nginx/html/*
COPY --from=builder /app/remotes/docker-desktop/dist/browser /usr/share/nginx/html
EXPOSE 80
