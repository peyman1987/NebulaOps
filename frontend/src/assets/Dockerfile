FROM node:20 AS build

WORKDIR /app

COPY package*.json ./
RUN npm install --registry=https://registry.npmjs.org/

COPY . .
RUN npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/nebulaops/browser /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]