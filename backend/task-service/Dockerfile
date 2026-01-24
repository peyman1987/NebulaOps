# NebulaOps v22.4 runtime image for task-service.
# Java compilation is intentionally executed once before Docker image build by:
#   ./scripts/wsl/build-backend-jars.sh
# This avoids downloading Maven dependencies separately in every service image.
FROM eclipse-temurin:21-jre
ENV PATH="/opt/java/openjdk/bin:/opt/nebula-tools:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
WORKDIR /app
COPY backend/task-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["/opt/java/openjdk/bin/java","-jar","/app/app.jar"]
