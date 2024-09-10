# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy only Maven descriptors first to maximize Docker layer and BuildKit cache reuse.
COPY backend/pom.xml backend/pom.xml
COPY backend/gateway-service/pom.xml backend/gateway-service/pom.xml

# Pre-fetch dependencies with retries to avoid Maven Central transient timeout failures.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -f backend/gateway-service/pom.xml \
    -Dmaven.wagon.http.retryHandler.count=5 \
    -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
    -DskipTests -Dmaven.test.skip=true dependency:go-offline

COPY backend/gateway-service/src backend/gateway-service/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -f backend/gateway-service/pom.xml \
    -Dmaven.wagon.http.retryHandler.count=5 \
    -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
    -DskipTests -Dmaven.test.skip=true clean package

FROM eclipse-temurin:21-jre
USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates docker.io jq unzip \
    && curl -fsSL -o /usr/local/bin/kubectl https://dl.k8s.io/release/v1.30.6/bin/linux/amd64/kubectl \
    && chmod +x /usr/local/bin/kubectl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/backend/gateway-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
