import {existsSync, readFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const toolDir = dirname(fileURLToPath(import.meta.url));
const frontendRoot = join(toolDir, '..');
const sharedCore = join(frontendRoot, 'libs', 'nebulaops-mfe-core', 'src', 'public-api.ts');

const remotes = [
  ['docker-desktop', 'nebulaops-mfe-docker-desktop'],
  ['infra-hub', 'nebulaops-mfe-infra-hub'],
  ['openlens-kubernetes', 'nebulaops-mfe-openlens-kubernetes'],
  ['task-management', 'nebulaops-mfe-task-management'],
  ['observability', 'nebulaops-mfe-observability'],
  ['cicd-gitops', 'nebulaops-mfe-cicd-gitops'],
  ['terraform-studio', 'nebulaops-mfe-terraform-studio'],
  ['devsecops', 'nebulaops-mfe-devsecops'],
  ['ai-ops', 'nebulaops-mfe-ai-ops'],
  ['finops-cost', 'nebulaops-mfe-finops-cost'],
];

let failures = 0;

if (!existsSync(sharedCore)) {
  console.error(`Missing shared MFE core: ${sharedCore}`);
  failures += 1;
} else {
  const core = readFileSync(sharedCore, 'utf8');
  for (const required of ['bootstrapNebulaOpsMfe', 'nebulaOpsJwtInterceptor', 'nebulaops.v22_2.jwt']) {
    if (!core.includes(required)) {
      console.error(`Shared MFE core does not contain ${required}`);
      failures += 1;
    }
  }
}

for (const [name, tag] of remotes) {
  const dir = join(frontendRoot, 'remotes', name);
  const entry = join(dir, 'remoteEntry.js');
  const manifest = join(dir, 'manifest.json');
  const dockerfile = join(dir, 'Dockerfile');
  const nginx = join(dir, 'nginx.conf');
  const main = join(dir, 'src', 'main.ts');
  const tsconfig = join(dir, 'tsconfig.json');

  for (const file of [entry, manifest, dockerfile, nginx, main, tsconfig]) {
    if (!existsSync(file)) {
      console.error(`Missing ${file}`);
      failures += 1;
    }
  }

  if (existsSync(main)) {
    const content = readFileSync(main, 'utf8');
    if (!content.includes(`tagName: '${tag}'`)) {
      console.error(`Remote ${name} bootstrap does not register expected tag ${tag}`);
      failures += 1;
    }
    if (!content.includes('@nebulaops/mfe-core')) {
      console.error(`Remote ${name} does not use the shared MFE core library`);
      failures += 1;
    }
  }

  if (existsSync(tsconfig)) {
    const content = readFileSync(tsconfig, 'utf8');
    if (!content.includes('@nebulaops/mfe-core')) {
      console.error(`Remote ${name} tsconfig does not expose @nebulaops/mfe-core path alias`);
      failures += 1;
    }
  }

  if (existsSync(manifest)) {
    const manifestJson = JSON.parse(readFileSync(manifest, 'utf8'));
    if (manifestJson.version !== '22.2.0') {
      console.error(`Remote ${name} manifest version is ${manifestJson.version}, expected 22.2.0`);
      failures += 1;
    }
  }
  console.log(`OK ${name} -> ${tag}`);
}

if (failures > 0) {
  console.error(`NebulaOps v22.2 remote verification failed: ${failures} issue(s)`);
  process.exit(1);
}

console.log('NebulaOps v22.2 micro frontends verified. Shared MFE core owns bootstrap and JWT propagation.');
