import {spawnSync} from 'node:child_process';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';
const toolDir = dirname(fileURLToPath(import.meta.url));
const script = join(toolDir, 'build-changed-remotes.mjs');
const res = spawnSync(process.execPath, [script, '--all'], {stdio: 'inherit', env: {...process.env, NEBULAOPS_FORCE_FRONTEND_DIST_BUILD: 'true'}});
process.exit(res.status ?? 1);
