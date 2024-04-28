FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
COPY backend/gateway-service/pom.xml backend/gateway-service/pom.xml
COPY backend/gateway-service/src backend/gateway-service/src
WORKDIR /workspace/backend/gateway-service
RUN mvn -q -DskipTests package
FROM eclipse-temurin:21-jre
USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates docker.io jq \
    && curl -fsSL -o /usr/local/bin/kubectl https://dl.k8s.io/release/v1.30.6/bin/linux/amd64/kubectl \
    && chmod +x /usr/local/bin/kubectl \
    && curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/backend/gateway-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
