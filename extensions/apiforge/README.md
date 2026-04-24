# APIForge

Postman-style Spring MVC + HTML/CSS/JS tool with a modern dark UI, resizable panels, animations and drag and drop.

## Quick start

```bash
docker compose up --build
```

Open `http://localhost:8080`.

## Included capabilities

- REST requests: GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS.
- GraphQL: query editor, variables, operation name and prettify.
- WebSocket: connection, message sending and live log.
- Real backend-side auth options: Bearer/JWT, Basic, API Key Header and OAuth2 access token.
- Digest and AWS Signature UI/configuration ready for provider-specific signing extensions.
- Body modes: none, JSON, XML, text, form URL encoded, multipart form-data and binary body.
- Collections CRUD with `.postman_collection.json` export.
- Drag and drop:
  - move requests across collections;
  - drop JSON/Postman collection files into the window for import;
  - drag the request/response splitter to resize panels.
- Environments with `{{BASE_URL}}`, `{{TOKEN}}`, `{{API_KEY}}` variables.
- Browser-side pre-request scripts and `pm.*` style test scripts.
- Persistent request history.
- Dark glassmorphism UI, animations, toasts and focus theme.

## Structure

```text
src/main/java/com/apiforge
  controller/       REST API and MVC page
  service/          JSON storage and HTTP proxy
  websocket/        WebSocket handler
src/main/resources
  templates/index.html
  static/css/app.css
  static/js/app.js
```

## Data persistence

Data is saved in `./data` through a Docker volume:

```yaml
volumes:
  - ./data:/data
```

## Technical notes

Digest Auth and AWS Signature are present as UI/configuration options but do not sign the request yet; they require provider/server-specific algorithms.

## Improvements v2

- More spacious dark UI with animations, focus mode and vertical splitter.
- Improved drag and drop: move across collections and reorder inside the same collection.
- Drag-and-drop import of Postman collection JSON with header, query and raw body parsing.
- API Key supported both as header and as query parameter.
- Automatic Content-Type for JSON, XML, text and x-www-form-urlencoded.
- cURL copy button for the current request.
- Copy Body button for the response.
- `Ctrl/Cmd + Enter` shortcut to send the request.

See `TEST_REPORT.md` for executed checks.

## Data policy

APIForge starts with empty collections, environments and history. Requests are created by the operator or imported from a real collection; no default external demo endpoint is inserted.
