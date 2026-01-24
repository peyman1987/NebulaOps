# NebulaOps v22.4 frontend shell + reverse proxy runtime image.
# IMPORTANT: npm install/build is intentionally NOT executed inside Docker.
# Build dist locally first with: ./scripts/wsl/build-frontend-local.sh
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
RUN rm -rf /usr/share/nginx/html/* && mkdir -p /usr/share/nginx/html/remotes
COPY dist/nebulaops/browser/ /usr/share/nginx/html/

# Serve shell-loaded MFE remoteEntry bundles as static same-origin assets.
# This avoids runtime 404/502 failures caused by proxying each remoteEntry.js
# through thirteen separate MFE containers during local startup.
COPY remotes/docker-desktop/dist/browser/ /usr/share/nginx/html/remotes/docker-desktop/
COPY remotes/openlens-kubernetes/dist/browser/ /usr/share/nginx/html/remotes/openlens-kubernetes/
COPY remotes/task-management/dist/browser/ /usr/share/nginx/html/remotes/task-management/
COPY remotes/observability/dist/browser/ /usr/share/nginx/html/remotes/observability/
COPY remotes/cicd-gitops/dist/browser/ /usr/share/nginx/html/remotes/cicd-gitops/
COPY remotes/terraform-studio/dist/browser/ /usr/share/nginx/html/remotes/terraform-studio/
COPY remotes/devsecops/dist/browser/ /usr/share/nginx/html/remotes/devsecops/
COPY remotes/ai-ops/dist/browser/ /usr/share/nginx/html/remotes/ai-ops/
COPY remotes/finops-cost/dist/browser/ /usr/share/nginx/html/remotes/finops-cost/
COPY remotes/infra-hub/dist/browser/ /usr/share/nginx/html/remotes/infra-hub/
COPY remotes/release-center/dist/browser/ /usr/share/nginx/html/remotes/release-center/
COPY remotes/policy-center/dist/browser/ /usr/share/nginx/html/remotes/policy-center/
COPY remotes/notification-center/dist/browser/ /usr/share/nginx/html/remotes/notification-center/
COPY remotes/identity-admin/dist/browser/ /usr/share/nginx/html/remotes/identity-admin/
COPY remotes/progressive-delivery/dist/browser/ /usr/share/nginx/html/remotes/progressive-delivery/

EXPOSE 80
