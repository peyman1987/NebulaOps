FROM node:20.19.0 AS build

WORKDIR /app

COPY package*.json ./
RUN rm -f package-lock.json && \
    npm install --include=dev --legacy-peer-deps

COPY . .
RUN npx ng build --configuration production

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
RUN rm -rf /usr/share/nginx/html/*
COPY --from=build /app/dist/nebulaops/browser /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]