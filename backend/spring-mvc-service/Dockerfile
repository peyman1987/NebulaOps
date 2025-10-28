# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
COPY backend/spring-mvc-service/pom.xml backend/spring-mvc-service/pom.xml
COPY backend/spring-mvc-service/src backend/spring-mvc-service/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -f backend/spring-mvc-service/pom.xml \
    -Dmaven.wagon.http.retryHandler.count=5 \
    -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
    -DskipTests -Dmaven.test.skip=true clean package

FROM eclipse-temurin:21-jre
ENV PATH="/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
WORKDIR /app

COPY --from=build /workspace/backend/spring-mvc-service/target/*.jar app.jar

RUN cat > /app/Healthcheck.java <<'EOF'
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
public class Healthcheck {
  public static void main(String[] args) throws Exception {
    String url = args.length == 0 ? "http://localhost:8099/actuator/health" : args[0];
    var client = HttpClient.newHttpClient();
    var request = HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(3)).GET().build();
    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 400 || !response.body().contains("UP")) {
      System.exit(1);
    }
  }
}
EOF

EXPOSE 8099
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=5 \
  CMD ["/opt/java/openjdk/bin/java", "/app/Healthcheck.java", "http://localhost:8099/actuator/health"]

ENTRYPOINT ["/opt/java/openjdk/bin/java","-jar","/app/app.jar"]
