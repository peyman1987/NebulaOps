# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
COPY backend/observability-service/pom.xml backend/observability-service/pom.xml


COPY backend/observability-service/src backend/observability-service/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -f backend/observability-service/pom.xml \
    -Dmaven.wagon.http.retryHandler.count=5 \
    -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
    -DskipTests -Dmaven.test.skip=true clean package

FROM eclipse-temurin:21-jre
ENV PATH="/opt/java/openjdk/bin:/opt/nebula-tools:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
WORKDIR /app

# v20.6 corrected: no enterprise CLI downloads during image build.
# Runtime tools must be provided by Docker volume mounts or custom base image:
# docker, kubectl, helm, argocd, terraform, trivy.
COPY --from=build /workspace/backend/observability-service/target/*.jar app.jar

EXPOSE 8092
ENTRYPOINT ["/opt/java/openjdk/bin/java","-jar","/app/app.jar"]
