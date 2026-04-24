# APIForge v3 - Test report

## Requested fixes

- Fixed collection selection: clicking a collection card now makes it active/selected.
- Saving a request now uses the selected collection instead of automatically using the first one.
- After create, duplicate or drag-and-drop import, the new collection is selected automatically.
- Added a selected visual state to make the request target clear.
- Reworked the dark theme with an Angular Material-inspired style: surface layers, elevation/shadow, pill tabs, Material-like buttons, clean inputs and a more restrained blue/purple palette.

## Checks executed

- `node --check src/main/resources/static/js/app.js` OK.
- Base HTML parsing with `html.parser` OK.
- Manual flow review for `selectedCollectionId`, `selectCollection`, `saveReq`, `newCollection`, `dupCol`, `wireDropImport`, `moveRequest`.

## Not executed in this environment

- Maven build: `mvn` command not available.
- Docker build: `docker` command not available.

## Recommended start

```bash
unzip apiforge_v3_material_fixed.zip
cd apiforge
# or use the extracted directory if the name is different
docker compose up --build
```

Then open `http://localhost:8080`.
