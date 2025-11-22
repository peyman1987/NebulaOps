# NebulaOps v22.2 — Docker build fix

## Fix applicata

Il servizio `cost-analytics-service` usava ancora un Dockerfile single-stage con:

```dockerfile
COPY target/*.jar app.jar
```

Con il build context impostato alla root del progetto e con `.dockerignore` che esclude `target`, `docker compose up --build` falliva con:

```text
failed to solve: lstat /target: no such file or directory
```

Il Dockerfile è stato allineato agli altri servizi Spring Boot ed è ora multi-stage:

1. build del modulo Maven dentro l'immagine `maven:3.9.9-eclipse-temurin-21`;
2. copia del JAR generato nello stage runtime `eclipse-temurin:21-jre-alpine`;
3. avvio con `java -jar app.jar`.

## Comandi consigliati dopo l'aggiornamento

```bash
docker compose down --remove-orphans
docker compose build --no-cache cost-analytics-service
docker compose up -d --build
```
