# APIForge

Clone Postman in Spring MVC + HTML/CSS/JS, con UI dark moderna, pannelli ridimensionabili, animazioni e drag & drop.

## Avvio rapido

```bash
docker compose up --build
```

Apri `http://localhost:8080`.

## Funzionalità incluse

- Richieste REST: GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS.
- GraphQL: editor query, variabili, operation name e prettify.
- WebSocket: connessione, invio messaggi e log live.
- Auth reale lato backend: Bearer/JWT, Basic, API Key Header, OAuth2 access token.
- UI per Digest e AWS Signature pronta da estendere.
- Body: none, JSON, XML, Text, form urlencoded, multipart form-data and binary body.
- Collections CRUD con export `.postman_collection.json`.
- Drag & drop:
    - trascina request tra collections;
    - trascina file JSON/Postman collection nella finestra per importarlo;
    - trascina lo splitter tra request/response per ridimensionare i pannelli.
- Environments con variabili `{{BASE_URL}}`, `{{TOKEN}}`, `{{API_KEY}}`.
- Pre-request scripts e test scripts stile `pm.*` lato browser.
- History persistente delle richieste.
- UI dark glassmorphism, animazioni, toast, tema Focus.

## Struttura

```text
src/main/java/com/apiforge
  controller/       REST API e pagina MVC
  service/          storage JSON e proxy HTTP
  websocket/        handler WebSocket
src/main/resources
  templates/index.html
  static/css/app.css
  static/js/app.js
```

## Persistenza dati

I dati vengono salvati in `./data` tramite volume Docker:

```yaml
volumes:
  - ./data:/data
```

## Note tecniche

Digest Auth e AWS Signature sono presenti come UI/configurazione ma non firmano ancora realmente la richiesta:
richiedono algoritmi specifici per server/provider.

## Migliorie v2

- UI dark più spaziosa con animazioni, focus mode e splitter verticale.
- Drag & drop migliorato: spostamento tra collection e riordino dentro la stessa collection.
- Import drag & drop di collection Postman JSON con parsing di header, query e body raw.
- API Key supportata sia come header sia come query parameter.
- Content-Type automatico per JSON, XML, text e x-www-form-urlencoded.
- Pulsante cURL per copiare la request corrente.
- Pulsante Copy Body per copiare la risposta.
- Shortcut `Ctrl/Cmd + Enter` per inviare la request.

Vedi `TEST_REPORT.md` per i controlli eseguiti.

## Data policy

APIForge starts with empty collections, environments and history. Requests are created by the operator or imported from a real collection; no default external demo endpoint is inserted.
