import {existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {dirname, join, relative} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';

const toolDir = dirname(fileURLToPath(import.meta.url));
const frontendRoot = join(toolDir, '..');
const cacheDir = join(frontendRoot, '.build');
const cachePath = join(cacheDir, 'frontend-build-cache-v24.1.json');
const forceAll = process.argv.includes('--all') || process.env.NEBULAOPS_FORCE_FRONTEND_DIST_BUILD === 'true';
const forceNpmCi = process.env.NEBULAOPS_FORCE_NPM_CI === 'true';
const npmCmd = process.platform === 'win32' ? 'npm.cmd' : 'npm';

const excludedNames = new Set(['node_modules', 'dist', '.angular', '.cache', '.build', 'coverage']);
const excludedFiles = new Set(['package-lock.json']);

function readJson(file, fallback) {
  try { return JSON.parse(readFileSync(file, 'utf8')); } catch { return fallback; }
}

function listFiles(root, files = []) {
  if (!existsSync(root)) return files;
  const entries = readdirSync(root).sort();
  for (const entry of entries) {
    if (excludedNames.has(entry)) continue;
    const file = join(root, entry);
    const st = statSync(file);
    if (st.isDirectory()) listFiles(file, files);
    else if (!excludedFiles.has(entry)) files.push(file);
  }
  return files;
}

function hashPaths(paths) {
  const h = createHash('sha256');
  for (const path of paths.flatMap((p) => existsSync(p) && statSync(p).isDirectory() ? listFiles(p) : [p]).filter(existsSync).sort()) {
    const rel = relative(frontendRoot, path);
    h.update(rel);
    h.update('\0');
    h.update(readFileSync(path));
    h.update('\0');
  }
  return h.digest('hex');
}

function run(cmd, args, cwd) {
  const env = {
    ...process.env,
    PATH: `${join(frontendRoot, 'node_modules', '.bin')}:${process.env.PATH || ''}`,
    NODE_PATH: join(frontendRoot, 'node_modules'),
    npm_config_audit: 'false',
    npm_config_fund: 'false',
    npm_config_legacy_peer_deps: 'true'
  };
  console.log(`\n==> ${cmd} ${args.join(' ')} (${relative(frontendRoot, cwd) || '.'})`);
  const res = spawnSync(cmd, args, {cwd, env, stdio: 'inherit'});
  if (res.status !== 0) process.exit(res.status ?? 1);
}

function ensureRootInstall() {
  const ngBin = join(frontendRoot, 'node_modules', '.bin', process.platform === 'win32' ? 'ng.cmd' : 'ng');
  if (!forceNpmCi && existsSync(ngBin)) {
    console.log('[NebulaOps] Reusing root frontend node_modules. Set NEBULAOPS_FORCE_NPM_CI=true to reinstall.');
    return;
  }
  if (existsSync(join(frontendRoot, 'package-lock.json'))) {
    run(npmCmd, ['ci', '--legacy-peer-deps', '--no-audit', '--no-fund'], frontendRoot);
  } else {
    run(npmCmd, ['install', '--legacy-peer-deps', '--no-audit', '--no-fund'], frontendRoot);
  }
}

function shellDistValid() {
  return existsSync(join(frontendRoot, 'dist', 'nebulaops', 'browser', 'index.html')) &&
         existsSync(join(frontendRoot, 'dist', 'nebulaops', 'browser', 'nebulaops-auth-bridge.js'));
}

function remoteDistValid(slug) {
  const dist = join(frontendRoot, 'remotes', slug, 'dist', 'browser');
  return existsSync(join(dist, 'index.html')) &&
         existsSync(join(dist, 'remoteEntry.js')) &&
         existsSync(join(dist, 'nebulaops-auth-bridge.js'));
}

function remoteSlugs() {
  const remotesDir = join(frontendRoot, 'remotes');
  return readdirSync(remotesDir)
    .filter((name) => existsSync(join(remotesDir, name, 'package.json')))
    .sort();
}

mkdirSync(cacheDir, {recursive: true});
const cache = readJson(cachePath, {shell: {}, remotes: {}});
ensureRootInstall();

const sharedInputs = [
  join(frontendRoot, 'shared'),
  join(frontendRoot, 'libs'),
  join(frontendRoot, 'tools', 'generate-live-only-remote.cjs'),
  join(frontendRoot, 'tools', 'live-only-remote.template.js')
];

const shellHash = hashPaths([
  join(frontendRoot, 'package.json'),
  join(frontendRoot, 'angular.json'),
  join(frontendRoot, 'tsconfig.json'),
  join(frontendRoot, 'tsconfig.app.json'),
  join(frontendRoot, 'src'),
  ...sharedInputs
]);
if (forceAll || cache.shell.hash !== shellHash || !shellDistValid()) {
  run(npmCmd, ['run', 'build:shell'], frontendRoot);
  cache.shell = {hash: shellHash, builtAt: new Date().toISOString()};
} else {
  console.log('[NebulaOps] Shell dist is unchanged; skipping shell build.');
}

for (const slug of remoteSlugs()) {
  const dir = join(frontendRoot, 'remotes', slug);
  const hash = hashPaths([
    join(dir, 'package.json'),
    join(dir, 'angular.json'),
    join(dir, 'tsconfig.json'),
    join(dir, 'tsconfig.app.json'),
    join(dir, 'src'),
    join(dir, 'manifest.json'),
    join(dir, 'live-endpoints.json'),
    join(dir, 'build-remote-entry.cjs'),
    ...sharedInputs
  ]);
  const previous = cache.remotes?.[slug]?.hash;
  if (forceAll || previous !== hash || !remoteDistValid(slug)) {
    run(npmCmd, ['run', 'build', '--', '--progress=false'], dir);
    cache.remotes = cache.remotes || {};
    cache.remotes[slug] = {hash, builtAt: new Date().toISOString()};
  } else {
    console.log(`[NebulaOps] MFE ${slug} unchanged; skipping build.`);
  }
}

writeFileSync(cachePath, JSON.stringify(cache, null, 2) + '\n');
run(process.execPath, [join(frontendRoot, 'tools', 'verify-remotes.mjs')], frontendRoot);
console.log('\n[NebulaOps] v24.1 selective frontend build completed.');
