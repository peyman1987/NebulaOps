import {existsSync, readFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const toolDir = dirname(fileURLToPath(import.meta.url));
const root = join(toolDir, '..');
const checks = [
  ['API client', 'libs/nebulaops-api-client/src/index.ts', ['NebulaOpsApiClient', 'Authorization', 'X-Correlation-Id', 'realDataOnly', 'AbortController']],
  ['Live source state', 'libs/nebulaops-live-state/src/index.ts', ['SourceState', 'READY', 'DEGRADED', 'UNAVAILABLE', 'NOT_CONFIGURED']],
  ['MFE runtime bootstrap', 'libs/nebulaops-mfe-runtime/src/index.ts', ['bootstrapNebulaRemote', 'assertRealDataOnly', 'customElements.define']],
  ['UI Kit v2 TypeScript', 'libs/nebulaops-ui-kit/src/index.ts', ['NEBULAOPS_UI_KIT_VERSION', 'NebulaActionBarAction', 'NebulaSidePanelSection']],
  ['UI Kit v2 CSS', 'libs/nebulaops-ui-kit/src/nebulaops-ui-kit.css', ['nebula-action-bar', 'nebula-side-panel', 'nebula-source-state', 'nebula-density-compact']],
  ['Live-only remote template', 'tools/live-only-remote.template.js', ['realDataOnly', 'NOT_CONFIGURED', 'UNAVAILABLE', 'safePlan', 'showDetail']]
];
let failures = 0;
for (const [label, rel, needles] of checks) {
  const file = join(root, rel);
  if (!existsSync(file)) {
    console.error(`Missing ${label}: ${rel}`);
    failures += 1;
    continue;
  }
  const text = readFileSync(file, 'utf8');
  for (const needle of needles) {
    if (!text.includes(needle)) {
      console.error(`${label} missing architecture marker ${needle} in ${rel}`);
      failures += 1;
    }
  }
  console.log(`OK ${label}`);
}
if (failures) {
  console.error(`NebulaOps v24.1 frontend architecture verification failed: ${failures} issue(s)`);
  process.exit(1);
}
console.log('NebulaOps v24.1 frontend architecture contract verified: UI Kit v2, API client, source-state and MFE bootstrap are present.');
