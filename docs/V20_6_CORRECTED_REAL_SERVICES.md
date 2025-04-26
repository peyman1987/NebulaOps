# NebulaOps v21.1 Corrected Real Services

## Cosa è stato corretto

- Backend riallineati al pattern Controller → Service → Client/Adapter → Tool.
- Dockerfile backend ripuliti da download runtime fragili.
- OpenAPI 3 aggiunto ai servizi Spring Boot tramite `springdoc-openapi`.
- Specifiche YAML in `docs/openapi`.
- Documentazione e diagrammi v21.1 aggiornati.
- Nessun dato statistico statico nei backend live platform.

## Endpoint documentazione

| Service                     | Swagger                               |
|-----------------------------|---------------------------------------|
| gateway-service             | http://localhost:8080/swagger-ui.html |
| auth-service                | http://localhost:8081/swagger-ui.html |
| task-service                | http://localhost:8082/swagger-ui.html |
| file-service                | http://localhost:8084/swagger-ui.html |
| ai-ops-service              | http://localhost:8085/swagger-ui.html |
| devsecops-service           | http://localhost:8086/swagger-ui.html |
| pipeline-engine-service     | http://localhost:8087/swagger-ui.html |
| observability-service       | http://localhost:8092/swagger-ui.html |
| gitops-control-service      | http://localhost:8093/swagger-ui.html |
| environment-manager-service | http://localhost:8094/swagger-ui.html |
| terraform-studio-service    | http://localhost:8096/swagger-ui.html |
