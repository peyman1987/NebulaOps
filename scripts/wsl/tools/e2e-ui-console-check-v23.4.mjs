#!/usr/bin/env node
/* NebulaOps v23.4 UI E2E console smoke.
 * Opens selected UI routes in a real headless Chromium/Chrome instance through
 * the Chrome DevTools Protocol and fails on runtime exceptions or console.error.
 * No npm dependencies are required; Node 22+ is recommended for global WebSocket.
 */

import { spawn, spawnSync } from 'node:child_process';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import net from 'node:net';

const baseUrl = process.env.NEBULAOPS_BASE_URL || process.env.BASE_URL || 'http://nebulaops.localhost';
const timeoutMs = Number(process.env.NEBULAOPS_UI_E2E_TIMEOUT_MS || 30000);
const holdMs = Number(process.env.NEBULAOPS_UI_E2E_HOLD_MS || 6500);
const routeEnv = process.env.NEBULAOPS_UI_E2E_ROUTES || '/remotes/docker-desktop/,/remotes/openlens-kubernetes/';
const targetRoutes = routeEnv.split(',').map((value) => value.trim()).filter(Boolean);
const ignoredConsoleFragments = [
  '[NebulaOps auth bridge] dev login failed',
  'favicon.ico',
];

if (typeof WebSocket === 'undefined') {
  console.error('Node global WebSocket is unavailable. Use Node 22+ for the browser console E2E smoke check.');
  process.exit(2);
}

function findBrowser() {
  const explicit = process.env.CHROME_BIN || process.env.CHROMIUM_BIN || '';
  if (explicit) return explicit;
  const candidates = [
    'chromium',
    'chromium-browser',
    'google-chrome',
    'google-chrome-stable',
    'microsoft-edge',
    'msedge',
  ];
  for (const candidate of candidates) {
    const found = spawnSync('bash', ['-lc', `command -v ${candidate}`], { encoding: 'utf8' });
    if (found.status === 0 && found.stdout.trim()) return found.stdout.trim();
  }
  return '';
}

async function freePort() {
  return await new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      const port = address && typeof address === 'object' ? address.port : 0;
      server.close(() => resolve(port));
    });
  });
}

async function sleep(ms) {
  return await new Promise((resolve) => setTimeout(resolve, ms));
}

async function fetchJson(url, init = {}) {
  const response = await fetch(url, init);
  if (!response.ok) throw new Error(`${url} returned HTTP ${response.status}`);
  return await response.json();
}

async function waitForVersion(port, proc) {
  const deadline = Date.now() + timeoutMs;
  let lastError = '';
  while (Date.now() < deadline) {
    if (proc.exitCode !== null) throw new Error(`Browser exited before DevTools was ready: ${proc.exitCode}`);
    try {
      return await fetchJson(`http://127.0.0.1:${port}/json/version`);
    } catch (error) {
      lastError = error.message;
      await sleep(250);
    }
  }
  throw new Error(`Timed out waiting for Chrome DevTools: ${lastError}`);
}

function wsConnect(url) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(url);
    const timer = setTimeout(() => reject(new Error('Timed out opening CDP websocket')), 10000);
    ws.addEventListener('open', () => { clearTimeout(timer); resolve(ws); }, { once: true });
    ws.addEventListener('error', (event) => { clearTimeout(timer); reject(new Error(String(event.message || 'CDP websocket error'))); }, { once: true });
  });
}

function createCdp(ws) {
  let seq = 0;
  const pending = new Map();
  const listeners = new Map();
  ws.addEventListener('message', (event) => {
    const msg = JSON.parse(event.data);
    if (msg.id && pending.has(msg.id)) {
      const slot = pending.get(msg.id);
      pending.delete(msg.id);
      if (msg.error) slot.reject(new Error(`${slot.method}: ${msg.error.message}`));
      else slot.resolve(msg.result || {});
      return;
    }
    if (msg.method && listeners.has(msg.method)) {
      for (const handler of listeners.get(msg.method)) handler(msg.params || {});
    }
  });
  return {
    send(method, params = {}) {
      const id = ++seq;
      ws.send(JSON.stringify({ id, method, params }));
      return new Promise((resolve, reject) => pending.set(id, { method, resolve, reject }));
    },
    on(method, handler) {
      if (!listeners.has(method)) listeners.set(method, []);
      listeners.get(method).push(handler);
    },
    close() {
      try { ws.close(); } catch (_) {}
    },
  };
}

function stringifyConsoleArg(arg) {
  if (!arg) return '';
  if (typeof arg.value !== 'undefined') return String(arg.value);
  if (typeof arg.description !== 'undefined') return String(arg.description);
  return JSON.stringify(arg);
}

function shouldIgnoreConsole(message) {
  return ignoredConsoleFragments.some((fragment) => message.includes(fragment));
}

async function openRoute(port, route) {
  const url = new URL(route, baseUrl).toString();
  const tab = await fetchJson(`http://127.0.0.1:${port}/json/new?${encodeURIComponent(url)}`, { method: 'PUT' });
  const ws = await wsConnect(tab.webSocketDebuggerUrl);
  const cdp = createCdp(ws);
  const failures = [];
  let loaded = false;

  cdp.on('Runtime.exceptionThrown', (params) => {
    const details = params.exceptionDetails || {};
    failures.push(`Runtime exception: ${details.text || details.exception?.description || 'unknown exception'}`);
  });
  cdp.on('Runtime.consoleAPICalled', (params) => {
    const type = String(params.type || '').toLowerCase();
    const message = (params.args || []).map(stringifyConsoleArg).join(' ');
    if ((type === 'error' || type === 'assert') && !shouldIgnoreConsole(message)) {
      failures.push(`console.${type}: ${message}`);
    }
  });
  cdp.on('Log.entryAdded', (params) => {
    const entry = params.entry || {};
    const level = String(entry.level || '').toLowerCase();
    const text = String(entry.text || '');
    if (level === 'error' && !shouldIgnoreConsole(text)) failures.push(`browser log error: ${text}`);
  });
  cdp.on('Network.responseReceived', (params) => {
    const type = String(params.type || '');
    const response = params.response || {};
    const status = Number(response.status || 0);
    if (status >= 500 && ['Document', 'Script', 'Stylesheet'].includes(type)) {
      failures.push(`${type} ${response.url} returned HTTP ${status}`);
    }
  });
  cdp.on('Network.loadingFailed', (params) => {
    const type = String(params.type || '');
    if (['Document', 'Script', 'Stylesheet'].includes(type)) {
      failures.push(`${type} request failed: ${params.errorText || 'unknown'} ${params.blockedReason || ''}`.trim());
    }
  });
  cdp.on('Page.loadEventFired', () => { loaded = true; });

  await cdp.send('Runtime.enable');
  await cdp.send('Log.enable');
  await cdp.send('Network.enable');
  await cdp.send('Page.enable');
  await cdp.send('Page.navigate', { url });

  const deadline = Date.now() + timeoutMs;
  while (!loaded && Date.now() < deadline) await sleep(250);
  if (!loaded) failures.push(`Page load timeout after ${timeoutMs}ms`);
  await sleep(holdMs);

  const evaluation = await cdp.send('Runtime.evaluate', {
    returnByValue: true,
    expression: `(() => {
      function collect(root) {
        let text = root && root.innerText ? root.innerText : '';
        const nodes = root && root.querySelectorAll ? Array.from(root.querySelectorAll('*')) : [];
        for (const node of nodes) {
          if (node.shadowRoot) text += '\\n' + collect(node.shadowRoot);
        }
        return text;
      }
      const text = collect(document.body).slice(0, 20000);
      return {
        title: document.title,
        hasBody: !!document.body,
        bodyLength: text.length,
        text,
        customElements: Array.from(document.querySelectorAll('*')).filter(x => x.tagName.toLowerCase().startsWith('nebulaops-mfe-')).map(x => x.tagName.toLowerCase())
      };
    })()`,
  });
  const value = evaluation.result?.value || {};
  if (!value.hasBody || Number(value.bodyLength || 0) < 20) failures.push('Rendered body is empty or too small');
  const renderedText = String(value.text || '');
  if (/Cannot read properties|TypeError|ReferenceError|Unhandled Promise/i.test(renderedText)) {
    failures.push('Rendered UI contains a JavaScript error message');
  }

  cdp.close();
  if (failures.length) throw new Error(`${route}\n - ${failures.join('\n - ')}`);
  console.log(`✓ ${route} opened without console/runtime errors`);
}

const browser = findBrowser();
if (!browser) {
  const message = 'No Chromium/Chrome executable found. Set CHROME_BIN or install chromium for UI console E2E smoke tests.';
  if (process.env.NEBULAOPS_ALLOW_BROWSER_SKIP === '1') {
    console.warn(`⚠ ${message}`);
    process.exit(0);
  }
  console.error(`✗ ${message}`);
  process.exit(2);
}

const port = await freePort();
const userDataDir = mkdtempSync(join(tmpdir(), 'nebulaops-chrome-'));
const browserArgs = [
  `--remote-debugging-port=${port}`,
  `--user-data-dir=${userDataDir}`,
  '--headless=new',
  '--disable-gpu',
  '--disable-dev-shm-usage',
  '--no-first-run',
  '--no-default-browser-check',
  '--no-sandbox',
  'about:blank',
];
const proc = spawn(browser, browserArgs, { stdio: ['ignore', 'ignore', 'pipe'] });
let stderr = '';
proc.stderr.on('data', (chunk) => { stderr += chunk.toString(); });

try {
  await waitForVersion(port, proc);
  console.log(`▶ NebulaOps v23.4 UI console E2E smoke via ${browser}`);
  console.log(`  baseUrl=${baseUrl}`);
  for (const route of targetRoutes) await openRoute(port, route);
} catch (error) {
  console.error(`✗ UI console E2E smoke failed: ${error.message}`);
  if (stderr.trim()) console.error(stderr.trim().split('\n').slice(-20).join('\n'));
  process.exitCode = 1;
} finally {
  try { proc.kill('SIGTERM'); } catch (_) {}
  rmSync(userDataDir, { recursive: true, force: true });
}
