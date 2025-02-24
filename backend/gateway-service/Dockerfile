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
    && apt-get install -y --no-install-recommends curl ca-certificates docker.io jq unzip git grep procps \
    && curl -fsSL -o /usr/local/bin/kubectl https://dl.k8s.io/release/v1.30.6/bin/linux/amd64/kubectl \
    && chmod +x /usr/local/bin/kubectl \
    && (curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash || true) \
    && (curl -fsSL -o /tmp/trivy.deb https://github.com/aquasecurity/trivy/releases/download/v0.56.2/trivy_0.56.2_Linux-64bit.deb && dpkg -i /tmp/trivy.deb || true) \
    && (curl -fsSL -o /tmp/argocd https://github.com/argoproj/argo-cd/releases/download/v2.12.6/argocd-linux-amd64 && install -m 555 /tmp/argocd /usr/local/bin/argocd || true) \
    && (curl -fsSL -o /tmp/terraform.zip https://releases.hashicorp.com/terraform/1.9.8/terraform_1.9.8_linux_amd64.zip && unzip -o /tmp/terraform.zip -d /usr/local/bin || true) \
    && rm -rf /var/lib/apt/lists/* /tmp/trivy.deb /tmp/argocd /tmp/terraform.zip
WORKDIR /app
COPY --from=build /workspace/backend/gateway-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
