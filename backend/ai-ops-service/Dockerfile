# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy only Maven descriptors first to maximize Docker layer and BuildKit cache reuse.
COPY backend/pom.xml backend/pom.xml
COPY backend/ai-ops-service/pom.xml backend/ai-ops-service/pom.xml

# Pre-fetch dependencies with retries to avoid Maven Central transient timeout failures.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -f backend/ai-ops-service/pom.xml \
    -Dmaven.wagon.http.retryHandler.count=5 \
    -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
    -DskipTests -Dmaven.test.skip=true dependency:go-offline

COPY backend/ai-ops-service/src backend/ai-ops-service/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -f backend/ai-ops-service/pom.xml \
    -Dmaven.wagon.http.retryHandler.count=5 \
    -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
    -DskipTests -Dmaven.test.skip=true clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/ai-ops-service/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java","-jar","/app/app.jar"]
