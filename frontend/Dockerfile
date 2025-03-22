FROM node:22-alpine AS build
WORKDIR /app
COPY package.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm install --include=dev --legacy-peer-deps --no-audit --no-fund --registry=https://registry.npmjs.org/
COPY . .
RUN npx ng build --configuration production

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
RUN rm -rf /usr/share/nginx/html/*
COPY --from=build /app/dist/nebulaops/browser /usr/share/nginx/html
EXPOSE 80
