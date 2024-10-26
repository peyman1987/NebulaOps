# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
COPY backend/terraform-studio-service/pom.xml backend/terraform-studio-service/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -f backend/terraform-studio-service/pom.xml -DskipTests -Dmaven.test.skip=true dependency:go-offline
COPY backend/terraform-studio-service/src backend/terraform-studio-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -f backend/terraform-studio-service/pom.xml -DskipTests -Dmaven.test.skip=true clean package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/terraform-studio-service/target/*.jar app.jar
EXPOSE 8096
ENTRYPOINT ["java","-jar","/app/app.jar"]
