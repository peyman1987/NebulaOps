# NebulaOps v23.1 MFE runtime image.
# IMPORTANT: npm install/build is intentionally NOT executed inside Docker.
# Build dist locally first with: ./scripts/wsl/build-frontend-local.sh
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
RUN rm -rf /usr/share/nginx/html/*
COPY dist/browser/ /usr/share/nginx/html/

EXPOSE 80
