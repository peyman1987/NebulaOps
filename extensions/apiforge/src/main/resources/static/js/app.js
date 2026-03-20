// ============================================================
//  APIForge – Complete Postman-compatible API client  v2.0
// ============================================================

// ── State ─────────────────────────────────────────────────────
let collections = [], envs = [], history = [], activeEnv = {},
    collectionVars = {}, globals = {},
    selectedCollectionId = null;
let tabs = [], activeTabId = null;
let ws = null, dragReq = null, consoleLogs = [], editingEnvId = null;
let currentCodeLang = 'curl', currentCodeRequest = null;
let allHistory = [];

const $ = s => document.querySelector(s);
const $$ = s => [...document.querySelectorAll(s)];

// ── API helper ────────────────────────────────────────────────
async function api(p, o = {}) {
    const r = await fetch('/apiforge/api' + p, {headers: {'Content-Type': 'application/json'}, ...o});
    if (!r.ok) throw new Error(await r.text());
    return r.json();
}

// ── Toast ──────────────────────────────────────────────────────
function toast(msg, type = '') {
    const t = $('#toast');
    t.textContent = msg;
    t.className = 'toast show' + (type ? ' ' + type : '');
    clearTimeout(toast._t);
    toast._t = setTimeout(() => t.classList.remove('show'), 2400);
}

// ── Variable substitution ──────────────────────────────────────
function subst(s) {
    return (s || '').replace(/{{\s*([A-Z0-9_a-z]+)\s*}}/g, (_, k) =>
        collectionVars[k] ?? globals[k] ?? activeEnv[k] ?? `{{${k}}}`);
}

function esc(s) {
    return String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}

// ── JSON syntax highlighting ───────────────────────────────────
function highlightJson(str) {
    try { str = JSON.stringify(JSON.parse(str), null, 2); } catch {}
    return str
        .replace(/[&<>]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]))
        .replace(/(\"(?:\\u[a-fA-F0-9]{4}|\\[^u]|[^\\"])*\"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+-]?\d+)?)/g, m => {
            let c = 'json-number';
            if (/^"/.test(m)) c = /:$/.test(m) ? 'json-key' : 'json-string';
            else if (/true|false/.test(m)) c = 'json-bool';
            else if (/null/.test(m)) c = 'json-null';
            return `<span class="${c}">${m}</span>`;
        });
}

// ── Method colors ──────────────────────────────────────────────
const METHOD_COLORS = {GET:'#86efac',POST:'#7dd3fc',PUT:'#fde68a',PATCH:'#c4b5fd',DELETE:'#fda4af',HEAD:'#94a3b8',OPTIONS:'#94a3b8',GRAPHQL:'#f0abfc',WEBSOCKET:'#6ee7b7'};

// ── Tab state ──────────────────────────────────────────────────
function defaultTab(id) {
    return {
        id: id || crypto.randomUUID(), name: 'Untitled',
        method: 'GET', url: '',
        headers: [{enabled: true, key: 'Accept', value: 'application/json'}],
        queryParams: [],
        auth: {type: 'none', values: {}},
        bodyType: 'none', body: '', formData: [],
        preScript: '', testScript: `pm.test('Status is 2xx', () => {\n  pm.expect(pm.response.code).to.be.above(199).and.below(300);\n});`,
        gqlQuery: 'query {\n  __typename\n}', gqlVars: '', gqlOperation: '',
        timeout: 30
    };
}

function createTab(data = {}) {
    const t = {...defaultTab(), ...data};
    tabs.push(t);
    activeTabId = t.id;
    return t;
}

function getActiveTab() { return tabs.find(t => t.id === activeTabId); }

function saveTabState() {
    const tab = getActiveTab();
    if (!tab) return;
    Object.assign(tab, {
        method: $('#method').value, url: $('#url').value,
        headers: readKV('headerRows'), queryParams: readKV('paramsRows'),
        auth: readAuth(), bodyType: $('#bodyType').value, body: $('#bodyText').value,
        formData: readMultipart(),
        preScript: $('#preScript').value, testScript: $('#testScript').value,
        gqlQuery: $('#gqlQuery').value, gqlVars: $('#gqlVars').value,
        gqlOperation: $('#gqlOperation').value,
        timeout: parseInt($('#timeout').value) || 30
    });
}

function loadTabState(tab) {
    if (!tab) return;
    $('#method').value = tab.method || 'GET';
    $('#url').value = tab.url || '';
    $('#bodyText').value = tab.body || '';
    $('#bodyType').value = tab.bodyType || 'none';
    $('#headerRows').innerHTML = '';
    (tab.headers || []).forEach(x => addKV('headerRows', x.key, x.value, x.enabled));
    $('#paramsRows').innerHTML = '';
    (tab.queryParams || []).forEach(x => addKV('paramsRows', x.key, x.value, x.enabled));
    $('#preScript').value = tab.preScript || '';
    $('#testScript').value = tab.testScript || '';
    $('#gqlQuery').value = tab.gqlQuery || 'query {\n  __typename\n}';
    $('#gqlVars').value = tab.gqlVars || '';
    $('#gqlOperation').value = tab.gqlOperation || '';
    $('#authType').value = tab.auth?.type || 'none';
    $('#timeout').value = tab.timeout || 30;
    renderAuth(tab.auth?.values);
    // Multipart
    $('#multipartRows').innerHTML = '';
    (tab.formData || []).forEach(f => addMultipartRow(f.key, f.value, f.enabled));
    onBodyTypeChange(true);
    updateUrlHint();
    renderTabs();
}

function switchTab(id) {
    if (id === activeTabId) return;
    saveTabState();
    activeTabId = id;
    loadTabState(getActiveTab());
}

function closeTab(id) {
    if (tabs.length === 1) { newTab(); return; }
    const idx = tabs.findIndex(t => t.id === id);
    tabs.splice(idx, 1);
    if (activeTabId === id) {
        activeTabId = tabs[Math.max(0, idx - 1)].id;
        loadTabState(getActiveTab());
    } else renderTabs();
}

function newTab(data) {
    saveTabState();
    createTab(data);
    loadTabState(getActiveTab());
    toast('New tab');
}

function dupTab() {
    saveTabState();
    const t = getActiveTab();
    if (t) newTab({...t, id: null, name: (t.name || 'Tab') + ' copy'});
}

// ── KV rows ────────────────────────────────────────────────────
function addKV(id, k = '', v = '', on = true) {
    const d = document.createElement('div');
    d.className = 'kv';
    d.draggable = true;
    d.innerHTML = `<input type=checkbox ${on ? 'checked' : ''}><input placeholder="key" value="${esc(k)}"><input placeholder="value" value="${esc(v)}"><button title="Remove" onclick="this.parentElement.remove()">×</button>`;
    $('#' + id).appendChild(d);
    d.addEventListener('dragstart', () => d.classList.add('dragging'));
    d.addEventListener('dragend', () => d.classList.remove('dragging'));
}

function readKV(id) {
    return $$('#' + id + ' .kv').map(r => ({
        enabled: r.children[0].checked, key: r.children[1].value, value: r.children[2].value
    })).filter(x => x.key);
}

// ── Multipart form ─────────────────────────────────────────────
function addMultipartRow(k = '', v = '', on = true) {
    const d = document.createElement('div');
    d.className = 'kv multipart-kv';
    d.innerHTML = `<input type=checkbox ${on ? 'checked' : ''}><input placeholder="field name" value="${esc(k)}"><input placeholder="value" value="${esc(v)}"><button title="Remove" onclick="this.parentElement.remove()">×</button>`;
    $('#multipartRows').appendChild(d);
}

function readMultipart() {
    return $$('#multipartRows .multipart-kv').map(r => ({
        enabled: r.children[0].checked, key: r.children[1].value, value: r.children[2].value
    })).filter(x => x.key);
}

// ── Body type change ───────────────────────────────────────────
function onBodyTypeChange(silent = false) {
    const t = $('#bodyType').value;
    const showText = ['json','xml','text','form','none'].includes(t);
    const showMulti = t === 'multipart';
    const showBinary = t === 'binary';
    $('#bodyText').style.display = showText ? '' : 'none';
    $('#multipartForm').style.display = showMulti ? '' : 'none';
    $('#binaryUpload').style.display = showBinary ? '' : 'none';
    if (!silent && showMulti && $('#multipartRows').children.length === 0) addMultipartRow();
}

// ── Auth ───────────────────────────────────────────────────────
function renderAuth(vals = {}) {
    const t = $('#authType').value, f = $('#authFields');
    const inp = (n, p = '', type = 'text') =>
        `<div class="auth-field"><label>${p || n}</label><input data-auth="${n}" type="${type}" placeholder="${p || n}" value="${esc(vals?.[n] || '')}"></div>`;
    let h = '';
    if (['bearer','jwt'].includes(t)) h = inp('token','Bearer token');
    else if (t === 'basic') h = inp('username','Username') + inp('password','Password','password');
    else if (t === 'digest') h = inp('username','Username') + inp('password','Password','password') + inp('realm','Realm (optional)');
    else if (t === 'apiKeyHeader') h = inp('key','Header name (e.g. X-API-Key)') + inp('value','API key value');
    else if (t === 'apiKeyQuery') h = inp('key','Query param (e.g. api_key)') + inp('value','API key value');
    else if (t === 'oauth2') h = inp('accessToken','Access token') + inp('clientId','Client ID') + inp('scope','Scope');
    else if (t === 'aws') h = inp('accessKey','Access Key') + inp('secretKey','Secret Key','password') + inp('region','Region (e.g. us-east-1)') + inp('service','Service (e.g. execute-api)');
    f.innerHTML = h || '<p class="empty-state">No authentication required</p>';
}

function readAuth() {
    const vals = {};
    $$('[data-auth]').forEach(i => vals[i.dataset.auth] = i.value);
    return {type: $('#authType').value, values: vals};
}

// ── Preset headers ─────────────────────────────────────────────
function addPresetHeader(key, value) {
    const existing = $$('#headerRows .kv').find(r => r.children[1].value.toLowerCase() === key.toLowerCase());
    if (existing) { existing.children[2].value = value; toast(`Updated ${key}`); return; }
    addKV('headerRows', key, value, true);
    toast(`Added ${key}: ${value}`);
}

// ── Build request ──────────────────────────────────────────────
function buildRequest() {
    let method = $('#method').value;
    let body = $('#bodyText').value;
    const bodyType = $('#bodyType').value;
    const formData = readMultipart();

    if (bodyType === 'form') body = formEncode(parseMaybe(body));
    if (method === 'GRAPHQL') {
        body = JSON.stringify({
            query: $('#gqlQuery').value,
            variables: parseMaybe($('#gqlVars').value),
            operationName: $('#gqlOperation').value || null
        });
    }
    const tab = getActiveTab();
    return {
        id: tab?.id || crypto.randomUUID(), name: tab?.name || 'Request',
        method, url: subst($('#url').value),
        headers: readKV('headerRows'), queryParams: readKV('paramsRows'),
        auth: readAuth(), bodyType, body, formData, form: {},
        scripts: {preRequest: $('#preScript').value, tests: $('#testScript').value},
        timeoutSeconds: parseInt($('#timeout').value) || 30
    };
}

function parseMaybe(s) { try { return s ? JSON.parse(s) : {}; } catch { return {raw: s}; } }
function formEncode(obj) {
    if (!obj || typeof obj !== 'object' || obj.raw) return String(obj?.raw || '');
    return Object.entries(obj).map(([k,v]) => encodeURIComponent(k) + '=' + encodeURIComponent(v ?? '')).join('&');
}
function pretty(x) { try { return JSON.stringify(JSON.parse(x), null, 2); } catch { return x; } }

// ── cURL export ────────────────────────────────────────────────
function toCurl(req) {
    const parts = ['curl', '-X', req.method === 'GRAPHQL' ? 'POST' : req.method, JSON.stringify(req.url)];
    req.headers.forEach(h => { if (h.enabled && h.key) parts.push('-H', JSON.stringify(h.key + ': ' + h.value)); });
    const at = req.auth?.type, av = req.auth?.values || {};
    if (['bearer','jwt'].includes(at)) parts.push('-H', JSON.stringify('Authorization: Bearer ' + (av.token || '')));
    if (at === 'basic') parts.push('-u', JSON.stringify((av.username||'') + ':' + (av.password||'')));
    if (at === 'oauth2' && av.accessToken) parts.push('-H', JSON.stringify('Authorization: Bearer ' + av.accessToken));
    if (req.body) parts.push('--data-raw', JSON.stringify(req.body));
    return parts.join(' \\\n  ');
}

// ── pm sandbox ─────────────────────────────────────────────────
function pmSandbox(response = null) {
    const tests = [];
    return {
        pm: {
            environment: {
                set: (k,v) => { activeEnv[k] = String(v); },
                get: k => activeEnv[k],
                unset: k => { delete activeEnv[k]; },
                has: k => k in activeEnv,
                toObject: () => ({...activeEnv})
            },
            collectionVariables: {
                set: (k,v) => { collectionVars[k] = String(v); },
                get: k => collectionVars[k],
                unset: k => { delete collectionVars[k]; },
                has: k => k in collectionVars,
                toObject: () => ({...collectionVars})
            },
            globals: {
                set: (k,v) => { globals[k] = String(v); },
                get: k => globals[k],
                unset: k => { delete globals[k]; },
                has: k => k in globals,
                toObject: () => ({...globals})
            },
            variables: {
                set: (k,v) => { collectionVars[k] = String(v); },
                get: k => collectionVars[k] ?? activeEnv[k] ?? globals[k],
                has: k => k in collectionVars || k in activeEnv || k in globals
            },
            response: response ? {
                status: response.statusText,
                code: response.status,
                statusCode: response.status,
                headers: { get: k => response.headers?.[k.toLowerCase()]?.[0] },
                responseTime: response.timeMs,
                size: { total: () => response.sizeBytes },
                json: () => JSON.parse(response.body || '{}'),
                text: () => response.body || '',
                to: { have: { status: (s) => { if (response.status !== s) throw new Error(`Expected status ${s}, got ${response.status}`); } } }
            } : null,
            expect: (val) => ({
                to: {
                    equal: (e) => { if (val !== e) throw new Error(`Expected ${JSON.stringify(val)} to equal ${JSON.stringify(e)}`); },
                    eql: (e) => { if (JSON.stringify(val) !== JSON.stringify(e)) throw new Error(`Deep equality failed`); },
                    include: (s) => { if (!String(val).includes(s)) throw new Error(`"${val}" does not include "${s}"`); },
                    have: {
                        property: (k) => { if (!(k in Object(val))) throw new Error(`Missing property "${k}"`); },
                        status: (s) => { if (val !== s) throw new Error(`Expected status ${s}`); }
                    },
                    be: {
                        ok: () => { if (!val) throw new Error(`Expected ${val} to be truthy`); },
                        a: (t) => { if (typeof val !== t) throw new Error(`Expected ${typeof val} to be ${t}`); },
                        above: (n) => { if (val <= n) throw new Error(`Expected ${val} > ${n}`); },
                        below: (n) => { if (val >= n) throw new Error(`Expected ${val} < ${n}`); },
                        within: (lo, hi) => { if (val < lo || val > hi) throw new Error(`Expected ${val} in [${lo},${hi}]`); }
                    },
                    and: {
                        be: {
                            below: (n) => { if (val >= n) throw new Error(`Expected ${val} < ${n}`); },
                            above: (n) => { if (val <= n) throw new Error(`Expected ${val} > ${n}`); }
                        }
                    },
                    not: {
                        equal: (e) => { if (val === e) throw new Error(`Expected not ${JSON.stringify(e)}`); },
                        have: { property: (k) => { if (k in Object(val)) throw new Error(`Should not have property "${k}"`); } }
                    }
                }
            }),
            test: (name, fn) => {
                try { fn(); tests.push({name, pass: true}); }
                catch (e) { tests.push({name, pass: false, error: e.message}); }
            }
        },
        console: {
            log: (...a) => consoleLogs.push({level:'log', msg: a.map(x => typeof x==='object' ? JSON.stringify(x,null,2) : String(x)).join(' ')}),
            warn: (...a) => consoleLogs.push({level:'warn', msg: a.map(String).join(' ')}),
            error: (...a) => consoleLogs.push({level:'error', msg: a.map(String).join(' ')}),
            info: (...a) => consoleLogs.push({level:'info', msg: a.map(String).join(' ')})
        },
        tests
    };
}

// ── Send request ───────────────────────────────────────────────
async function send() {
    if ($('#method').value === 'WEBSOCKET') { connectWs(); return; }
    consoleLogs = [];
    const pre = pmSandbox();
    try { new Function('pm', 'console', $('#preScript').value)(pre.pm, pre.console); }
    catch (e) { consoleLogs.push({level:'error', msg: 'Pre-script: ' + e.message}); toast('Pre-script error', 'error'); }
    const req = buildRequest();
    $('#meta').innerHTML = '<span class="pill sending">⟳ Sending…</span>';
    $('#send').disabled = true;
    const t0 = performance.now();
    try {
        const res = await api('/send', {method:'POST', body: JSON.stringify(req)});
        if (res.error) {
            $('#meta').textContent = 'Error';
            $('#resBody').innerHTML = `<span class="error-text">${esc(res.error)}</span>`;
            return;
        }
        renderResponse(res);
        await loadAll(false);
        toast(`${res.status} · ${res.timeMs}ms`, res.status < 400 ? 'success' : 'error');
    } catch (e) {
        $('#meta').textContent = 'Request failed';
        $('#resBody').innerHTML = `<span class="error-text">${esc(e.message)}</span>`;
        toast('Request failed', 'error');
    } finally {
        $('#send').disabled = false;
        renderTabs();
    }
}

function statusClass(code) {
    if (code >= 200 && code < 300) return 'status-2xx';
    if (code >= 300 && code < 400) return 'status-3xx';
    if (code >= 400 && code < 500) return 'status-4xx';
    return 'status-5xx';
}

function renderResponse(res) {
    const sc = statusClass(res.status);
    const size = res.sizeBytes >= 1024 ? (res.sizeBytes/1024).toFixed(1)+' KB' : res.sizeBytes+' B';
    $('#meta').innerHTML =
        `<span class="pill ${sc}">${res.status} ${esc(res.statusText)}</span>` +
        `<span class="meta-chip">${res.timeMs} ms</span>` +
        `<span class="meta-chip">${size}</span>` +
        `<span class="meta-chip muted">${new Date(res.timestamp).toLocaleTimeString()}</span>`;

    // Body
    const ct = Object.entries(res.headers || {})
        .find(([k]) => k.toLowerCase() === 'content-type')?.[1]?.[0] || '';
    if (ct.includes('json') || (() => { try { JSON.parse(res.body); return true; } catch { return false; } })()) {
        $('#resBody').innerHTML = `<code class="json-hl">${highlightJson(res.body)}</code>`;
    } else if (ct.includes('xml') || ct.includes('html')) {
        $('#resBody').textContent = res.body || '(empty)';
    } else {
        $('#resBody').textContent = res.body || '(empty)';
    }

    // Headers
    $('#resHeaders').innerHTML =
        Object.entries(res.headers || {}).map(([k,v]) =>
            `<div class="header-row"><span class="hkey">${esc(k)}</span><span class="hval">${esc(Array.isArray(v)?v.join(', '):v)}</span></div>`
        ).join('') || '<div class="empty-state">No headers</div>';

    // Cookies
    $('#resCookies').textContent = (res.cookies || []).join('\n') || '(no cookies)';

    // Tests
    const box = pmSandbox(res);
    try { new Function('pm', 'console', $('#testScript').value)(box.pm, box.console); }
    catch (e) { box.tests.push({name:'Script error', pass:false, error:e.message}); consoleLogs.push({level:'error', msg:'Test: '+e.message}); }

    const pass = box.tests.filter(t=>t.pass).length, total = box.tests.length;
    const summClass = total > 0 ? (pass === total ? 'all-pass' : 'some-fail') : '';
    $('#resTests').innerHTML =
        `<div class="${summClass}">` +
        (total > 0 ? `<div class="test-summary">${pass}/${total} passed</div>` : '') +
        (box.tests.map(t =>
            `<div class="test-row ${t.pass?'pass':'fail'}"><span>${t.pass?'✓':'✕'}</span><span>${esc(t.name)}</span>${t.error?`<span class="test-err">${esc(t.error)}</span>`:''}</div>`
        ).join('') || '<div class="empty-state">No tests defined</div>') +
        `</div>`;

    renderConsole();
    currentCodeRequest = buildRequest();
}

function renderConsole() {
    $('#resConsole').innerHTML = consoleLogs.length
        ? consoleLogs.map(l => `<div class="log-row ${l.level}"><span class="log-level">${l.level.toUpperCase()}</span><span>${esc(l.msg)}</span></div>`).join('')
        : '<div class="empty-state">No console output · use <code>console.log()</code> in scripts</div>';
}

// ── Data loading ───────────────────────────────────────────────
async function loadAll(keepEnv = true) {
    [collections, envs, allHistory] = await Promise.all([api('/collections'), api('/environments'), api('/history')]);
    history = allHistory;
    if (!selectedCollectionId || !collections.some(c => c.id === selectedCollectionId))
        selectedCollectionId = collections[0]?.id || null;
    renderEnvs(keepEnv);
    renderCollections();
    filterHistory();
}

function renderEnvs(keep = true) {
    const sel = $('#envSelect'), prev = sel.value;
    sel.innerHTML = envs.map(e => `<option value="${e.id}">${esc(e.name)}</option>`).join('');
    const e = (keep && envs.find(x => x.id === prev)) || envs.find(x => x.active) || envs[0];
    if (e) { sel.value = e.id; activeEnv = {...(e.variables||{})}; }
}

function selectCollection(id) { selectedCollectionId = id; renderCollections(); }
function selectedCollection() { return collections.find(c => c.id === selectedCollectionId) || collections[0]; }

function methodBadge(m) {
    return `<span class="method" style="color:${METHOD_COLORS[m]||'#94a3b8'}">${esc(m)}</span>`;
}

// ── Sidebar search ─────────────────────────────────────────────
function filterCollections() {
    const q = ($('#collectionSearchInput').value || '').toLowerCase().trim();
    renderCollections(q);
}

function renderCollections(query = '') {
    const el = $('#collections');
    if (!collections.length) {
        el.innerHTML = '<div class="empty-state">No collections yet.<br>Create one or import a Postman / OpenAPI JSON.</div>';
        return;
    }
    el.innerHTML = collections.map(c => {
        let reqs = c.requests || [];
        if (query) reqs = reqs.filter(r => r.name.toLowerCase().includes(query) || (r.request?.url||'').toLowerCase().includes(query));
        const visible = !query || reqs.length > 0;
        if (!visible) return '';
        return `<div class="collection ${c.id===selectedCollectionId?'selected':''}" data-cid="${c.id}">
          <h4 onclick="selectCollection('${c.id}')">
            <span class="collection-title"><span class="collection-dot"></span>${esc(c.name)}</span>
            <span class="collection-actions">
              <button title="Run collection" onclick="event.stopPropagation();openRunnerFor('${c.id}')">▶</button>
              <button title="Duplicate" onclick="event.stopPropagation();dupCol('${c.id}')">⧉</button>
              <button title="Export" onclick="event.stopPropagation();exportCol('${c.id}')">↓</button>
              <button title="Delete" onclick="event.stopPropagation();delCol('${c.id}')">🗑</button>
            </span>
          </h4>
          <div class="collection-meta" onclick="selectCollection('${c.id}')">${reqs.length} request${reqs.length!==1?'s':''}</div>
          <div class="req-list" data-cid="${c.id}">
            ${reqs.map((r,i) => `<div class="req-item" draggable="true" data-cid="${c.id}" data-ridx="${i}"
              onclick='loadReq(${JSON.stringify(r.request).replaceAll("'","&#39;")})'>
              <span class="drag-handle">⋮⋮</span>${methodBadge(r.request.method)}<span class="req-name">${esc(r.name)}</span>
              <div class="req-item-actions">
                <button title="Duplicate in collection" onclick="event.stopPropagation();dupReqInCol('${c.id}',${i})">⧉</button>
                <button title="Delete" onclick="event.stopPropagation();delReqFromCol('${c.id}',${i})">×</button>
              </div>
            </div>`).join('')}
          </div>
        </div>`;
    }).join('');
    wireRequestDragDrop();
}

// ── History ────────────────────────────────────────────────────
function filterHistory() {
    const q = ($('#historySearchInput').value || '').toLowerCase().trim();
    const el = $('#history');
    let items = allHistory;
    if (q) items = items.filter(h => (h.request?.url||'').toLowerCase().includes(q) || (h.request?.method||'').toLowerCase().includes(q));
    if (!items.length) { el.innerHTML = '<div class="empty-state">No history yet.</div>'; return; }
    el.innerHTML = `<div class="history-toolbar">
      <span class="muted" style="font-size:11px">${items.length} requests</span>
      <button class="ghost" style="padding:4px 8px;font-size:11px" onclick="clearHistory()">Clear</button>
    </div>` +
    items.map(h => {
        const sc = statusClass(h.response.status);
        const url = h.request.url.replace(/^https?:\/\/[^/]+/, '');
        return `<div class="req-item" onclick='loadReq(${JSON.stringify(h.request).replaceAll("'","&#39;")})'>
          ${methodBadge(h.request.method)}
          <span class="req-name" title="${esc(h.request.url)}">${esc(url||h.request.url)}</span>
          <span class="pill ${sc}">${h.response.status}</span>
        </div>`;
    }).join('');
}

async function clearHistory() {
    if (!confirm('Clear all history?')) return;
    await fetch('/apiforge/api/history', {method:'DELETE'});
    allHistory = []; history = [];
    filterHistory();
    toast('History cleared');
}

// ── Tabs render ────────────────────────────────────────────────
function renderTabs() {
    const t = $('#tabs');
    t.innerHTML = tabs.map(tab => {
        const mc = METHOD_COLORS[tab.method]||'#94a3b8';
        const isActive = tab.id === activeTabId;
        const urlPart = tab.url ? tab.url.split('?')[0].split('/').filter(Boolean).slice(-1)[0] || tab.url.split('/')[2] || '' : '';
        const label = (tab.name !== 'Untitled' ? tab.name : urlPart || 'Untitled').substring(0,22);
        return `<div class="tab ${isActive?'active':''}" onclick="switchTab('${tab.id}')">
          <span class="tab-method" style="color:${mc}">${tab.method||'GET'}</span>
          <span class="tab-name">${esc(label)}</span>
          <button class="tab-close" title="Close" onclick="event.stopPropagation();closeTab('${tab.id}')">×</button>
        </div>`;
    }).join('') + `<button class="tab-new" onclick="newTab()" title="New tab (Ctrl+T)">+</button>`;
}

// ── Load / save requests ───────────────────────────────────────
function loadReq(r) {
    saveTabState();
    const tab = getActiveTab() || createTab();
    Object.assign(tab, {
        name: r.name||'Request', method: r.method||'GET', url: r.url||'',
        headers: r.headers||[], queryParams: r.queryParams||[],
        auth: r.auth||{type:'none',values:{}}, bodyType: r.bodyType||'none', body: r.body||'',
        formData: r.formData||[], preScript: r.scripts?.preRequest||'',
        testScript: r.scripts?.tests||'', timeout: r.timeoutSeconds||30
    });
    loadTabState(tab);
    toast('Request loaded');
}

async function saveReq() {
    saveTabState();
    const tab = getActiveTab();
    const name = prompt('Request name', tab?.name||'My Request');
    if (!name) return;
    if (tab) tab.name = name;
    let c = selectedCollection();
    if (!c) c = {name:'My Collection', description:'', requests:[]};
    c.requests = c.requests||[];
    const req = buildRequest();
    req.name = name;
    c.requests.push({id: crypto.randomUUID(), name, request: req, createdAt: new Date().toISOString()});
    const saved = await api('/collections', {method:'POST', body: JSON.stringify(c)});
    selectedCollectionId = saved.id||c.id||selectedCollectionId;
    await loadAll();
    renderTabs();
    toast('Saved to collection', 'success');
}

async function dupReqInCol(cid, idx) {
    const c = collections.find(x=>x.id===cid);
    if (!c) return;
    const orig = c.requests[idx];
    if (!orig) return;
    c.requests.splice(idx+1, 0, {...orig, id: crypto.randomUUID(), name: orig.name+' copy'});
    await api('/collections', {method:'POST', body: JSON.stringify(c)});
    await loadAll();
    toast('Request duplicated');
}

async function delReqFromCol(cid, idx) {
    const c = collections.find(x=>x.id===cid);
    if (!c || !confirm('Delete this request?')) return;
    c.requests.splice(idx, 1);
    await api('/collections', {method:'POST', body: JSON.stringify(c)});
    await loadAll();
    toast('Request deleted');
}

async function dupCol(id) {
    const c = collections.find(x=>x.id===id);
    const saved = await api('/collections', {method:'POST', body: JSON.stringify({...c, id:null, name:c.name+' copy'})});
    selectedCollectionId = saved.id;
    loadAll();
    toast('Collection duplicated');
}

async function delCol(id) {
    if (confirm('Delete this collection?')) {
        await fetch('/apiforge/api/collections/' + id, {method:'DELETE'});
        if (selectedCollectionId === id) selectedCollectionId = null;
        loadAll();
        toast('Collection deleted');
    }
}

function exportCol(id) { location.href = '/apiforge/api/export/' + id; }

// ── Environment editor ─────────────────────────────────────────
function openEnvEditor() {
    editingEnvId = $('#envSelect').value;
    const env = envs.find(e => e.id === editingEnvId);
    if (!env) { toast('No environment selected'); return; }
    $('#envEditorTitle').textContent = 'Edit: ' + env.name;
    $('#envNameInput').value = env.name;
    const tbody = $('#envVarRows');
    tbody.innerHTML = '';
    Object.entries(env.variables||{}).forEach(([k,v]) => addEnvRow(k, v));
    $('#envModal').classList.add('open');
}

function addEnvRow(k='', v='') {
    const tr = document.createElement('tr');
    tr.innerHTML = `<td><input value="${esc(k)}" placeholder="VARIABLE_NAME" style="font-family:ui-monospace,monospace"></td><td><input value="${esc(v)}" placeholder="initial value"></td><td><input value="${esc(v)}" placeholder="current value"></td><td><button onclick="this.closest('tr').remove()">×</button></td>`;
    $('#envVarRows').appendChild(tr);
}

async function saveEnvEditor() {
    const name = $('#envNameInput').value.trim();
    if (!name) { toast('Name required', 'error'); return; }
    const variables = {};
    $$('#envVarRows tr').forEach(r => {
        const [ki, , vi] = r.querySelectorAll('input');
        if (ki.value.trim()) variables[ki.value.trim()] = vi.value;
    });
    const env = envs.find(e => e.id === editingEnvId);
    if (!env) return;
    await api('/environments', {method:'POST', body: JSON.stringify({...env, name, variables})}).catch(()=>{});
    await loadAll();
    closeEnvEditor();
    toast('Environment saved', 'success');
}

function closeEnvEditor() { $('#envModal').classList.remove('open'); }

async function newEnv() {
    const name = prompt('Environment name', 'New Environment');
    if (!name) return;
    const saved = await api('/environments', {method:'POST', body: JSON.stringify({id:null, name, variables:{}, active:false})}).catch(()=>null);
    await loadAll();
    if (saved?.id) { $('#envSelect').value = saved.id; activeEnv = {}; }
    toast('Environment created', 'success');
}

async function deleteEnv() {
    const env = envs.find(e => e.id === editingEnvId);
    if (!env || !confirm(`Delete "${env.name}"?`)) return;
    await fetch('/apiforge/api/environments/' + editingEnvId, {method:'DELETE'}).catch(()=>{});
    closeEnvEditor();
    await loadAll();
    toast('Environment deleted');
}

// ── URL hint ───────────────────────────────────────────────────
function updateUrlHint() {
    const url = $('#url').value;
    const hasVars = /{{[^}]+}}/.test(url);
    $('#url').classList.toggle('has-vars', hasVars);
}

// ── WebSocket ──────────────────────────────────────────────────
function connectWs() {
    const u = subst($('#url').value);
    try {
        ws = new WebSocket(u);
        const s = $('#wsStatus');
        ws.onopen = () => { logWs('✓ Connected to ' + u); s.style.color='#86efac'; s.className='ws-status connected'; };
        ws.onmessage = e => logWs('← ' + e.data);
        ws.onerror = () => { logWs('✕ Connection error'); s.style.color='#fda4af'; };
        ws.onclose = () => { logWs('⊘ Disconnected'); s.style.color=''; s.className='ws-status'; };
    } catch(e) { toast(e.message, 'error'); }
}

function logWs(s) {
    const log = $('#wsLog');
    log.value += `[${new Date().toLocaleTimeString()}] ${s}\n`;
    log.scrollTop = log.scrollHeight;
}

// ── Drag & drop reorder ────────────────────────────────────────
function wireRequestDragDrop() {
    $$('.req-item[draggable=true]').forEach(item => {
        item.addEventListener('dragstart', e => {
            dragReq = {cid: item.dataset.cid, idx: +item.dataset.ridx};
            item.classList.add('dragging');
            e.dataTransfer.effectAllowed = 'move';
        });
        item.addEventListener('dragend', () => item.classList.remove('dragging'));
    });
    $$('.req-list').forEach(list => {
        list.addEventListener('dragover', e => {
            if (!dragReq) return;
            e.preventDefault();
            const after = getDragAfterElement(list, e.clientY);
            list.classList.add('drag-over-list');
            const dragging = document.querySelector('.req-item.dragging');
            if (dragging) after ? list.insertBefore(dragging, after) : list.appendChild(dragging);
        });
        list.addEventListener('dragleave', () => list.classList.remove('drag-over-list'));
        list.addEventListener('drop', async e => {
            if (!dragReq) return;
            e.preventDefault();
            list.classList.remove('drag-over-list');
            const targetCid = list.dataset.cid;
            const targetIdx = [...list.querySelectorAll('.req-item')].findIndex(x => x.classList.contains('dragging'));
            await moveRequest(dragReq.cid, dragReq.idx, targetCid, targetIdx);
            dragReq = null;
        });
    });
}

function getDragAfterElement(container, y) {
    return [...container.querySelectorAll('.req-item:not(.dragging)')].reduce((closest, child) => {
        const offset = y - child.getBoundingClientRect().top - child.getBoundingClientRect().height/2;
        return offset < 0 && offset > closest.offset ? {offset, element: child} : closest;
    }, {offset: Number.NEGATIVE_INFINITY}).element;
}

async function moveRequest(fromCid, idx, toCid, targetIdx = 999) {
    const from = collections.find(c=>c.id===fromCid), to = collections.find(c=>c.id===toCid);
    if (!from || !to) return;
    from.requests = from.requests||[]; to.requests = to.requests||[];
    const [req] = from.requests.splice(idx, 1);
    if (!req) return;
    if (fromCid===toCid && targetIdx>idx) targetIdx--;
    to.requests.splice(Math.max(0, Math.min(targetIdx, to.requests.length)), 0, req);
    await api('/collections', {method:'POST', body: JSON.stringify(from)});
    if (fromCid !== toCid) await api('/collections', {method:'POST', body: JSON.stringify(to)});
    selectedCollectionId = toCid;
    await loadAll();
    toast('Request moved');
}

// ── File import (Postman v2, v2.1, OpenAPI) ────────────────────
async function importFile(file) {
    try {
        const raw = JSON.parse(await file.text());
        let col;
        if (raw.openapi || raw.swagger) {
            col = normalizeOpenApi(raw);
        } else {
            col = normalizePostman(raw);
        }
        const saved = await api('/collections', {method:'POST', body: JSON.stringify(col)});
        selectedCollectionId = saved.id;
        await loadAll();
        toast(`Imported: ${col.name} (${col.requests.length} requests)`, 'success');
    } catch(err) { toast('Import failed: ' + err.message, 'error'); }
}

function normalizePostman(raw) {
    const items = raw.item || raw.requests || [];
    return {
        name: raw.info?.name || raw.name || 'Imported Collection',
        description: raw.info?.description || raw.description || '',
        requests: items.flatMap(flattenPostmanItem)
    };
}

function flattenPostmanItem(it) {
    if (it.item) return it.item.flatMap(flattenPostmanItem);
    const req = it.request || it;
    const method = req.method || 'GET';
    const url = typeof req.url === 'string' ? req.url : (req.url?.raw||'');
    const headers = (req.header||[]).map(h => ({enabled: !h.disabled, key: h.key, value: h.value}));
    const queryParams = (req.url?.query||[]).map(q => ({enabled: !q.disabled, key: q.key, value: q.value||''}));
    const body = req.body?.raw || '';
    const bodyType = req.body?.mode === 'raw' ? (req.body?.options?.raw?.language || 'text') : (req.body?.mode || 'none');
    const auth = (() => {
        if (!req.auth) return {type:'none', values:{}};
        const t = req.auth.type||'none', vals = {};
        (req.auth[t]||[]).forEach(i => { vals[i.key] = i.value; });
        return {type: t, values: vals};
    })();
    return [{
        id: crypto.randomUUID(), name: it.name || url || 'Imported',
        request: {
            id: crypto.randomUUID(), name: it.name, method, url,
            headers, queryParams, auth, bodyType, body, formData: [], form: {},
            scripts: {
                preRequest: it.event?.find(e=>e.listen==='prerequest')?.script?.exec?.join('\n') || '',
                tests: it.event?.find(e=>e.listen==='test')?.script?.exec?.join('\n') || ''
            }
        },
        createdAt: new Date().toISOString()
    }];
}

function normalizeOpenApi(spec) {
    const name = spec.info?.title || 'OpenAPI Import';
    const baseUrl = (() => {
        if (spec.servers?.[0]?.url) return spec.servers[0].url;
        if (spec.host) return (spec.schemes?.[0] || 'https') + '://' + spec.host + (spec.basePath || '');
        return '';
    })();
    const requests = [];
    const paths = spec.paths || {};
    for (const [path, methods] of Object.entries(paths)) {
        for (const [method, op] of Object.entries(methods)) {
            if (!['get','post','put','patch','delete','head','options'].includes(method)) continue;
            const url = baseUrl + path;
            const headers = [];
            const queryParams = [];
            (op.parameters || spec.paths?.[path]?.parameters || []).forEach(p => {
                if (p.in === 'header') headers.push({enabled:true, key:p.name, value:''});
                if (p.in === 'query') queryParams.push({enabled:true, key:p.name, value:''});
            });
            const consumes = op.requestBody?.content ? Object.keys(op.requestBody.content)[0] : '';
            let body = '', bodyType = 'none';
            if (consumes?.includes('json')) { bodyType='json'; body = '{}'; }
            else if (consumes?.includes('form')) bodyType = 'form';
            requests.push({
                id: crypto.randomUUID(),
                name: op.summary || op.operationId || method.toUpperCase() + ' ' + path,
                request: {
                    id: crypto.randomUUID(),
                    name: op.summary || op.operationId || '',
                    method: method.toUpperCase(), url,
                    headers, queryParams,
                    auth: {type:'none', values:{}},
                    bodyType, body, formData: [], form: {},
                    scripts: {preRequest:'', tests:''}
                },
                createdAt: new Date().toISOString()
            });
        }
    }
    return {name, description: spec.info?.description || '', requests};
}

// ── Import from cURL ───────────────────────────────────────────
function openCurlImport() { $('#curlImportModal').classList.add('open'); }
function closeCurlImport() { $('#curlImportModal').classList.remove('open'); }

function importFromCurl() {
    const raw = $('#curlInput').value.trim();
    if (!raw) return;
    try {
        const req = parseCurl(raw);
        saveTabState();
        const tab = getActiveTab() || createTab();
        Object.assign(tab, req);
        loadTabState(tab);
        closeCurlImport();
        toast('cURL imported', 'success');
    } catch(e) { toast('Parse error: ' + e.message, 'error'); }
}

function parseCurl(raw) {
    const clean = raw.replace(/\\\n/g, ' ').replace(/\s+/g, ' ').trim();
    const tokens = tokenize(clean);
    let method = 'GET', url = '', headers = [], body = '', bodyType = 'none';
    let i = 1; // skip 'curl'
    while (i < tokens.length) {
        const t = tokens[i];
        if ((t === '-X' || t === '--request') && tokens[i+1]) { method = tokens[++i].toUpperCase(); }
        else if ((t === '-H' || t === '--header') && tokens[i+1]) {
            const h = tokens[++i];
            const ci = h.indexOf(':');
            if (ci > 0) headers.push({enabled:true, key:h.slice(0,ci).trim(), value:h.slice(ci+1).trim()});
        }
        else if ((t === '-d' || t === '--data' || t === '--data-raw' || t === '--data-binary') && tokens[i+1]) {
            body = tokens[++i]; bodyType = 'json';
            try { JSON.parse(body); } catch { bodyType = 'text'; }
        }
        else if (t === '--form' && tokens[i+1]) { bodyType = 'form'; i++; }
        else if (!t.startsWith('-') && !url) { url = t; }
        i++;
    }
    if (method === 'GET' && body) method = 'POST';
    return {method, url, headers, queryParams: [], auth:{type:'none',values:{}}, bodyType, body, formData:[], form:{}, preScript:'', testScript:''};
}

function tokenize(s) {
    const tokens = [];
    let cur = '', inQ = null;
    for (let i = 0; i < s.length; i++) {
        const c = s[i];
        if (inQ) {
            if (c === inQ) { inQ = null; }
            else cur += c;
        } else if (c === '"' || c === "'") {
            inQ = c;
        } else if (c === ' ') {
            if (cur) { tokens.push(cur); cur = ''; }
        } else {
            cur += c;
        }
    }
    if (cur) tokens.push(cur);
    return tokens;
}

// ── Drop-zone import ───────────────────────────────────────────
function wireDropImport() {
    const overlay = $('#dropOverlay');
    ['dragenter','dragover'].forEach(ev => document.addEventListener(ev, e => {
        if (e.dataTransfer?.types?.includes('Files')) { e.preventDefault(); overlay.classList.add('show'); }
    }));
    ['dragleave','drop'].forEach(ev => document.addEventListener(ev, e => {
        if (ev==='drop') e.preventDefault();
        overlay.classList.remove('show');
    }));
    document.addEventListener('drop', async e => {
        const file = e.dataTransfer?.files?.[0];
        if (file) await importFile(file);
    });
    $('#importInput').addEventListener('change', async e => {
        const file = e.target.files?.[0];
        if (file) { await importFile(file); e.target.value = ''; }
    });
}

// ── Collection Runner ──────────────────────────────────────────
function openRunner() {
    const sel = $('#runnerColSelect');
    sel.innerHTML = collections.map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join('');
    $('#runnerResults').style.display = 'none';
    $('#runnerRunBtn').disabled = false;
    $('#runnerRunBtn').textContent = '▶ Run Collection';
    $('#runnerModal').classList.add('open');
}

function openRunnerFor(cid) {
    openRunner();
    $('#runnerColSelect').value = cid;
}

function closeRunner() { $('#runnerModal').classList.remove('open'); }

async function startRunner() {
    const cid = $('#runnerColSelect').value;
    const delay = parseInt($('#runnerDelay').value) || 0;
    const stop = $('#runnerStopOnError').checked;
    if (!cid) { toast('Select a collection', 'error'); return; }

    $('#runnerRunBtn').disabled = true;
    $('#runnerRunBtn').textContent = '⟳ Running…';
    $('#runnerResults').style.display = 'block';
    $('#runnerSummary').innerHTML = '<span class="runner-loading">Running…</span>';
    $('#runnerRows').innerHTML = '';

    try {
        const result = await api('/runner', {method:'POST', body: JSON.stringify({collectionId:cid, delayMs:delay, stopOnError:stop})});
        const {total, passed, failed, totalMs, results} = result;
        const allPassed = failed === 0;
        $('#runnerSummary').innerHTML =
            `<div class="runner-stats ${allPassed?'all-pass':'some-fail'}">
              <span><b>${passed}</b> passed</span>
              <span><b>${failed}</b> failed</span>
              <span><b>${total}</b> total</span>
              <span><b>${totalMs}</b> ms</span>
            </div>`;
        $('#runnerRows').innerHTML = results.map(r => {
            const sc = r.status ? statusClass(r.status) : '';
            return `<div class="runner-row ${r.passed?'pass':'fail'}">
              <span class="runner-check">${r.passed?'✓':'✕'}</span>
              ${methodBadge(r.method)}
              <span class="runner-name">${esc(r.name)}</span>
              <span class="runner-url muted">${esc(r.url)}</span>
              ${r.status ? `<span class="pill ${sc}">${r.status}</span>` : ''}
              ${r.timeMs ? `<span class="meta-chip">${r.timeMs}ms</span>` : ''}
              ${r.error ? `<div class="runner-error">${esc(r.error)}</div>` : ''}
            </div>`;
        }).join('');
        await loadAll();
    } catch(e) {
        toast('Runner error: ' + e.message, 'error');
        $('#runnerSummary').innerHTML = `<div class="runner-error">${esc(e.message)}</div>`;
    } finally {
        $('#runnerRunBtn').disabled = false;
        $('#runnerRunBtn').textContent = '▶ Run Again';
    }
}

// ── Code generation ────────────────────────────────────────────
function openCodeModal() {
    saveTabState();
    currentCodeRequest = buildRequest();
    currentCodeLang = 'curl';
    $$('.code-lang-tabs button').forEach(b => b.classList.toggle('active', b.dataset.lang === 'curl'));
    $('#codeModal').classList.add('open');
    loadCodeSnippet();
}

function closeCodeModal() { $('#codeModal').classList.remove('open'); }

function selectCodeLang(btn) {
    currentCodeLang = btn.dataset.lang;
    $$('.code-lang-tabs button').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    loadCodeSnippet();
}

async function loadCodeSnippet() {
    const el = $('#codeSnippet');
    el.textContent = 'Generating…';
    try {
        const result = await api('/codegen', {
            method:'POST',
            body: JSON.stringify({language: currentCodeLang, request: currentCodeRequest || buildRequest()})
        });
        el.textContent = result.snippet;
    } catch(e) { el.textContent = '// Error: ' + e.message; }
}

async function copyCodeSnippet() {
    await navigator.clipboard.writeText($('#codeSnippet').textContent || '');
    toast('Snippet copied!', 'success');
}

// ── Response search ────────────────────────────────────────────
function initResSearch() {
    $('#searchResBtn').onclick = () => {
        const s = $('#resSearch');
        s.style.display = s.style.display === 'none' ? 'flex' : 'none';
        if (s.style.display !== 'none') $('#resSearchInput').focus();
    };
    $('#resSearchInput').addEventListener('input', () => {
        const q = $('#resSearchInput').value.trim().toLowerCase();
        const text = $('#resBody').textContent;
        if (!q) { $('#resSearchCount').textContent = ''; return; }
        const matches = text.toLowerCase().split(q).length - 1;
        $('#resSearchCount').textContent = matches + ' match' + (matches !== 1 ? 'es' : '');
    });
}

// ── Save response ──────────────────────────────────────────────
function initSaveResponse() {
    $('#saveResBtn').onclick = () => {
        const text = $('#resBody').textContent;
        if (!text || text === '(empty)') { toast('No response to save', 'error'); return; }
        const blob = new Blob([text], {type:'application/octet-stream'});
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = 'response.json';
        a.click();
        toast('Response saved');
    };
}

// ── Word wrap toggle ───────────────────────────────────────────
let wrapEnabled = true;
function initWrapToggle() {
    $('#wrapBtn').onclick = () => {
        wrapEnabled = !wrapEnabled;
        $$('.rpanel').forEach(p => p.style.whiteSpace = wrapEnabled ? 'pre-wrap' : 'pre');
        $('#wrapBtn').textContent = wrapEnabled ? '⇔' : '⟺';
    };
}

// ── Splitter ───────────────────────────────────────────────────
function wireSplitter() {
    const sp = $('#splitter');
    let down = false;
    sp.addEventListener('pointerdown', e => { down = true; sp.setPointerCapture(e.pointerId); });
    sp.addEventListener('pointermove', e => {
        if (!down) return;
        const pct = Math.max(20, Math.min(70, 100 - e.clientY/window.innerHeight*100));
        document.documentElement.style.setProperty('--response', pct + '%');
    });
    sp.addEventListener('pointerup', () => down = false);
}

// ── Panel switchers ────────────────────────────────────────────
$$('.subtabs button[data-tab]').forEach(b => b.onclick = () => {
    if (document.startViewTransition) document.startViewTransition(() => switchPanel(b));
    else switchPanel(b);
});

function switchPanel(b) {
    $$('.panel').forEach(p => p.classList.remove('active'));
    $$('[data-tab]').forEach(x => x.classList.remove('active'));
    b.classList.add('active');
    $('#' + b.dataset.tab).classList.add('active');
}

$$('[data-rtab]').forEach(b => b.onclick = () => {
    $$('.rpanel').forEach(p => p.classList.remove('active'));
    $$('[data-rtab]').forEach(x => x.classList.remove('active'));
    b.classList.add('active');
    $('#' + b.dataset.rtab).classList.add('active');
});

$$('.side-tabs button').forEach(b => b.onclick = () => {
    $$('.side-panel').forEach(p => p.classList.add('hidden'));
    $$('[class*=sidebar-search]').forEach(p => p.classList.add('hidden'));
    $$('.side-tabs button').forEach(x => x.classList.remove('active'));
    b.classList.add('active');
    const panel = b.dataset.side;
    $('#' + panel).classList.remove('hidden');
    if (panel === 'collections') $('#collectionsSearch').classList.remove('hidden');
    if (panel === 'history') $('#historySearch').classList.remove('hidden');
});

// ── Keyboard shortcuts ─────────────────────────────────────────
document.addEventListener('keydown', e => {
    const mod = e.metaKey || e.ctrlKey;
    if (mod && e.key === 'Enter') { send(); e.preventDefault(); }
    if (mod && e.key === 's') { saveReq(); e.preventDefault(); }
    if (mod && e.key === 't') { newTab(); e.preventDefault(); }
    if (mod && e.key === 'd') { dupTab(); e.preventDefault(); }
    if (mod && e.key === '/') { $('#url').focus(); e.preventDefault(); }
    if (mod && e.key === 'w') { if (tabs.length > 1) { closeTab(activeTabId); e.preventDefault(); } }
    if (e.key === 'Escape') {
        closeEnvEditor(); closeCurlImport(); closeCodeModal(); closeRunner();
        $('#shortcutsModal').classList.remove('open');
    }
});

// ── Event bindings ─────────────────────────────────────────────
$('#authType').onchange = () => renderAuth();
$('#send').onclick = send;
$('#copyCurl').onclick = async () => {
    saveTabState();
    await navigator.clipboard.writeText(toCurl(buildRequest()));
    toast('cURL copied');
};
$('#codeGenBtn').onclick = openCodeModal;
$('#copyResponse').onclick = async () => { await navigator.clipboard.writeText($('#resBody').textContent||''); toast('Body copied'); };
$('#saveReq').onclick = saveReq;
$('#beautify').onclick = () => { $('#bodyText').value = pretty($('#bodyText').value); };
$('#prettifyGql').onclick = () => {
    let q = $('#gqlQuery').value;
    q = q.replace(/\s+/g,' ').replace(/{\s*/g,'{\n  ').replace(/\s*}/g,'\n}').replace(/,\s*/g,',\n  ').trim();
    $('#gqlQuery').value = q;
};
$('#wsConnect').onclick = connectWs;
$('#wsClose').onclick = () => ws?.close();
$('#wsSend').onclick = () => {
    const msg = $('#wsMsg').value;
    const type = $('#wsMsgType').value;
    const data = type === 'json' ? pretty(msg) : msg;
    ws?.send(data);
    logWs('→ ' + data);
};
$('#newCollection').onclick = async () => {
    const name = prompt('Collection name', 'My Collection');
    if (name) {
        const saved = await api('/collections', {method:'POST', body: JSON.stringify({name, description:'', requests:[]})});
        selectedCollectionId = saved.id;
        await loadAll();
        toast('Collection created', 'success');
    }
};
$('#envSelect').onchange = () => {
    const e = envs.find(x => x.id === $('#envSelect').value);
    if (e) { activeEnv = {...(e.variables||{})}; toast('Environment: ' + e.name); }
};
$('#editEnvBtn').onclick = openEnvEditor;
$('#newEnvBtn').onclick = newEnv;
$('#themePulse').onclick = () => {
    document.body.classList.toggle('focus');
    toast('Focus mode ' + (document.body.classList.contains('focus') ? 'on' : 'off'));
};
$('#clearUi').onclick = newTab;
$('#importBtn').onclick = () => $('#importInput').click();
$('#shortcutsBtn').onclick = () => $('#shortcutsModal').classList.add('open');
$('#url').addEventListener('input', updateUrlHint);

// ── Boot ───────────────────────────────────────────────────────
renderAuth();
wireDropImport();
wireSplitter();
initResSearch();
initWrapToggle();
initSaveResponse();
addKV('headerRows','Accept','application/json',true);
createTab();
renderTabs();
loadAll();
