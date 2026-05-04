# NebulaOps v23.3 runtime image for progressive-delivery-service.
# JARs are produced by ./scripts/wsl/build-backend-jars.sh before Docker image build.
FROM eclipse-temurin:21-jre
ENV PATH="/opt/java/openjdk/bin:/opt/nebula-tools:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
WORKDIR /app
COPY backend/progressive-delivery-service/target/*.jar app.jar
EXPOSE 8102
ENTRYPOINT ["/opt/java/openjdk/bin/java","-jar","/app/app.jar"]
