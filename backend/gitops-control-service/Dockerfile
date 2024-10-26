# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
COPY backend/gitops-control-service/pom.xml backend/gitops-control-service/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -f backend/gitops-control-service/pom.xml -DskipTests -Dmaven.test.skip=true dependency:go-offline
COPY backend/gitops-control-service/src backend/gitops-control-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -f backend/gitops-control-service/pom.xml -DskipTests -Dmaven.test.skip=true clean package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/gitops-control-service/target/*.jar app.jar
EXPOSE 8093
ENTRYPOINT ["java","-jar","/app/app.jar"]
