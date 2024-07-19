FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
COPY backend/ai-ops-service/pom.xml backend/ai-ops-service/pom.xml
COPY backend/ai-ops-service/src backend/ai-ops-service/src
WORKDIR /workspace/backend/ai-ops-service
RUN mvn -q -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/ai-ops-service/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java","-jar","/app/app.jar"]
