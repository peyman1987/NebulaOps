# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
COPY backend/cost-analytics-service/pom.xml backend/cost-analytics-service/pom.xml
COPY backend/cost-analytics-service/src backend/cost-analytics-service/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -f backend/cost-analytics-service/pom.xml \
    -Dmaven.wagon.http.retryHandler.count=5 \
    -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
    -DskipTests -Dmaven.test.skip=true clean package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/backend/cost-analytics-service/target/*.jar app.jar
EXPOSE 8097
ENTRYPOINT ["java","-jar","app.jar"]
