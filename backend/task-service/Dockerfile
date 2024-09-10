# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy only Maven descriptors first to maximize Docker layer and BuildKit cache reuse.
COPY backend/pom.xml backend/pom.xml
COPY backend/task-service/pom.xml backend/task-service/pom.xml

# Pre-fetch dependencies with retries to avoid Maven Central transient timeout failures.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -f backend/task-service/pom.xml \
    -Dmaven.wagon.http.retryHandler.count=5 \
    -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
    -DskipTests -Dmaven.test.skip=true dependency:go-offline

COPY backend/task-service/src backend/task-service/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -f backend/task-service/pom.xml \
    -Dmaven.wagon.http.retryHandler.count=5 \
    -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
    -DskipTests -Dmaven.test.skip=true clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/task-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java","-jar","/app/app.jar"]
