import {existsSync, readFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const toolDir = dirname(fileURLToPath(import.meta.url));
const frontendRoot = join(toolDir, '..');

const remotes = [
  ['platform-catalog', 'nebulaops-mfe-platform-catalog'],
  ['incident-command-center', 'nebulaops-mfe-incident-command-center'],
  ['runtime-readiness', 'nebulaops-mfe-runtime-readiness'],
  ['docker-storage-cleanup', 'nebulaops-mfe-docker-storage-cleanup'],
  ['environment-configuration', 'nebulaops-mfe-environment-configuration'],
  ['dependency-impact', 'nebulaops-mfe-dependency-impact'],
  ['test-quality-dashboard', 'nebulaops-mfe-test-quality-dashboard'],
  ['docker-desktop', 'nebulaops-mfe-docker-desktop'],
  ['openlens-kubernetes', 'nebulaops-mfe-openlens-kubernetes'],
  ['task-management', 'nebulaops-mfe-task-management'],
  ['observability', 'nebulaops-mfe-observability'],
  ['cicd-gitops', 'nebulaops-mfe-cicd-gitops'],
  ['terraform-studio', 'nebulaops-mfe-terraform-studio'],
  ['devsecops', 'nebulaops-mfe-devsecops'],
  ['ai-ops', 'nebulaops-mfe-ai-ops'],
  ['finops-cost', 'nebulaops-mfe-finops-cost'],
  ['infra-hub', 'nebulaops-mfe-infra-hub'],
  ['release-center', 'nebulaops-mfe-release-center'],
  ['policy-center', 'nebulaops-mfe-policy-center'],
  ['progressive-delivery', 'nebulaops-mfe-progressive-delivery'],
  ['notification-center', 'nebulaops-mfe-notification-center'],
  ['identity-admin', 'nebulaops-mfe-identity-admin'],
];

let failures = 0;
for (const [name, tag] of remotes) {
  const dir = join(frontendRoot, 'remotes', name);
  const sourceEntry = join(dir, 'remoteEntry.js');
  const distEntry = join(dir, 'dist', 'browser', 'remoteEntry.js');
  const distIndex = join(dir, 'dist', 'browser', 'index.html');
  const manifest = join(dir, 'manifest.json');
  const dockerfile = join(dir, 'Dockerfile');
  const nginx = join(dir, 'nginx.conf');

  for (const file of [sourceEntry, distEntry, distIndex, manifest, dockerfile, nginx]) {
    if (!existsSync(file)) {
      console.error(`Missing ${file}`);
      failures += 1;
    }
  }

  if (existsSync(distEntry)) {
    const content = readFileSync(distEntry, 'utf8');
    const registersDirectly = content.includes('customElements.define(TAG,') ||
        content.includes(`customElements.define('${tag}'`) ||
        content.includes(`customElements.define("${tag}"`);
    const registersFromConfig = content.includes(`"tag": "${tag}"`) &&
        content.includes('customElements.define(CFG.tag,');
    if (!registersDirectly && !registersFromConfig) {
      console.error(`Remote ${name} does not register expected custom element ${tag}`);
      failures += 1;
    }
    if (!content.includes('nebulaops.v23_2.jwt')) {
      console.error(`Remote ${name} does not read the shared NebulaOps JWT key`);
      failures += 1;
    }
    if (/\bexport\s+(default|\{|class|function|const|let|var)/.test(content)) {
      console.error(`Remote ${name} contains ESM export syntax. remoteEntry.js must be classic JavaScript for shell injection.`);
      failures += 1;
    }
  }

  if (existsSync(distIndex)) {
    const index = readFileSync(distIndex, 'utf8');
    if (!index.includes(`<${tag}></${tag}>`)) {
      console.error(`Remote ${name} standalone index does not render ${tag}`);
      failures += 1;
    }
    if (/type=["']module["']/.test(index)) {
      console.error(`Remote ${name} standalone index still loads module scripts; expected classic remoteEntry.js only.`);
      failures += 1;
    }
    if (!index.includes('remoteEntry.js')) {
      console.error(`Remote ${name} standalone index does not load remoteEntry.js`);
      failures += 1;
    }
  }

  if (existsSync(manifest)) {
    const manifestJson = JSON.parse(readFileSync(manifest, 'utf8'));
    if (manifestJson.version !== '23.2.0') {
      console.error(`Remote ${name} manifest version is ${manifestJson.version}, expected 23.2.0`);
      failures += 1;
    }
  }
  console.log(`OK ${name} -> ${tag}`);
}

if (failures > 0) {
  console.error(`NebulaOps v23.2 remote verification failed: ${failures} issue(s)`);
  process.exit(1);
}

console.log('NebulaOps v23.2 classic micro frontends verified. Shell owns side navigation; each remote renders standalone content.');
