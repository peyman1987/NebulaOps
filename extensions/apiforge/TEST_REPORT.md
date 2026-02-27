# APIForge v3 - Test report

## Correzioni richieste

- Corretta la selezione della collection: ora cliccando sulla card della collection questa diventa attiva/selected.
- Il salvataggio di una request ora usa la collection selezionata, non più automaticamente la prima.
- Dopo creazione, duplicazione o import via drag & drop, la nuova collection viene selezionata automaticamente.
- Aggiunto stato visivo selected per capire subito dove verrà salvata la request.
- Rifatto tema dark in stile Angular Material: superfici `surface`, elevation/shadow, pill tabs, bottoni Material-like,
  input puliti e palette blu/viola più sobria.

## Controlli eseguiti

- `node --check src/main/resources/static/js/app.js` OK.
- Parsing HTML base con `html.parser` OK.
- Controllo manuale del flusso
  JS: `selectedCollectionId`, `selectCollection`, `saveReq`, `newCollection`, `dupCol`, `wireDropImport`, `moveRequest`.

## Non eseguiti in questo ambiente

- Build Maven: comando `mvn` non disponibile.
- Build Docker: comando `docker` non disponibile.

## Avvio consigliato

```bash
unzip apiforge_v3_material_fixed.zip
cd apiforge
# oppure nella cartella estratta, se il nome è diverso
docker compose up --build
```

Poi aprire `http://localhost:8080`.
