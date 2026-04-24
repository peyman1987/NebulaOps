# NebulaOps v23.2 runtime image for cost-analytics-service.
# Java compilation is intentionally executed once before Docker image build by:
#   ./scripts/wsl/build-backend-jars.sh
# This avoids downloading Maven dependencies separately in every service image.
FROM eclipse-temurin:21-jre
ENV PATH="/opt/java/openjdk/bin:/opt/nebula-tools:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
WORKDIR /app
COPY backend/cost-analytics-service/target/*.jar app.jar
EXPOSE 8097
ENTRYPOINT ["/opt/java/openjdk/bin/java","-jar","/app/app.jar"]
