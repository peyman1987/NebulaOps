# NebulaOps v22.3 runtime image for spring-mvc-service.
# Java compilation is intentionally executed once before Docker image build by:
#   ./scripts/wsl/build-backend-jars.sh
# This avoids downloading Maven dependencies separately in every service image.
FROM eclipse-temurin:21-jre
ENV PATH="/opt/java/openjdk/bin:/opt/nebula-tools:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
WORKDIR /app
COPY backend/spring-mvc-service/target/*.jar app.jar
EXPOSE 8099
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
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=5 \
  CMD ["/opt/java/openjdk/bin/java", "/app/Healthcheck.java", "http://localhost:8099/actuator/health"]

ENTRYPOINT ["/opt/java/openjdk/bin/java","-jar","/app/app.jar"]
