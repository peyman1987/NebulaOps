const fs = require('fs');
const path = require('path');
const root = process.argv[2] ? path.resolve(process.argv[2]) : process.cwd();
const manifestPath = path.join(root, 'manifest.json');
const endpointPath = path.join(root, 'live-endpoints.json');
if (!fs.existsSync(manifestPath)) throw new Error(`Missing manifest.json in ${root}`);
if (!fs.existsSync(endpointPath)) throw new Error(`Missing live-endpoints.json in ${root}`);
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
const cfg = JSON.parse(fs.readFileSync(endpointPath, 'utf8'));
const tag = manifest.tag || `nebulaops-mfe-${path.basename(root)}`;
const title = manifest.title || path.basename(root);
const scope = manifest.scope || '';
const port = manifest.port || '';
const version = 'v23.1.0-live-real-data-control-plane';
const dist = path.join(root, 'dist', 'browser');
const sharedAuth = path.join(root, '..', '..', 'shared', 'nebulaops-auth-bridge.js');
const authBridge = fs.existsSync(sharedAuth) ? fs.readFileSync(sharedAuth, 'utf8') : '';
const template = fs.readFileSync(path.join(__dirname, 'live-only-remote.template.js'), 'utf8');
const runtime = template
  .replace('__CFG_JSON__', JSON.stringify({ tag, title, scope, port, id: manifest.id, config: cfg }, null, 2))
  .replaceAll('__VERSION__', version);
const html = `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta http-equiv="Cache-Control" content="no-store, no-cache, must-revalidate, max-age=0">
  <meta http-equiv="Pragma" content="no-cache">
  <meta http-equiv="Expires" content="0">
  <title>${title} · NebulaOps v23.1</title>
  <link rel="icon" href="/favicon.ico">
  <style>*{box-sizing:border-box}html,body{margin:0;min-height:100%;background:#050816;color:#eef6ff}body{font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}</style>
</head>
<body>
  <${tag}></${tag}>
  <script src="./nebulaops-auth-bridge.js?v=${version}"></script>
  <script src="./remoteEntry.js?v=${version}" defer></script>
</body>
</html>
`;
fs.mkdirSync(dist, { recursive: true });
const out = (authBridge ? authBridge + '\n\n' : '') + runtime;
fs.writeFileSync(path.join(dist, 'remoteEntry.js'), out, 'utf8');
fs.writeFileSync(path.join(root, 'remoteEntry.js'), out, 'utf8');
fs.writeFileSync(path.join(dist, 'index.html'), html, 'utf8');
fs.writeFileSync(path.join(root, 'index.html'), html, 'utf8');
if (authBridge) {
  fs.writeFileSync(path.join(dist, 'nebulaops-auth-bridge.js'), authBridge, 'utf8');
  fs.writeFileSync(path.join(root, 'nebulaops-auth-bridge.js'), authBridge, 'utf8');
}
console.log(`✅ live-only remoteEntry.js generated for ${tag}`);
