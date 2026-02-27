let collections = [], envs = [], history = [], activeEnv = {}, selectedCollectionId = null,
    currentTab = {id: crypto.randomUUID(), name: 'Untitled'};
let ws = null, dragReq = null;
const $ = s => document.querySelector(s);
const $$ = s => [...document.querySelectorAll(s)];

async function api(p, o = {}) {
    const r = await fetch('/apiforge/api' + p, {headers: {'Content-Type': 'application/json'}, ...o});
    if (!r.ok) throw new Error(await r.text());
    return r.json();
}

function toast(msg) {
    const t = $('#toast');
    t.textContent = msg;
    t.classList.add('show');
    clearTimeout(toast.t);
    toast.t = setTimeout(() => t.classList.remove('show'), 2200)
}

function subst(s) {
    return (s || '').replace(/{{\s*([A-Z0-9_]+)\s*}}/gi, (_, k) => activeEnv[k] ?? `{{${k}}}`)
}

function esc(s) {
    return String(s || '').replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
}

function addKV(id, k = '', v = '', on = true) {
    const d = document.createElement('div');
    d.className = 'kv';
    d.draggable = true;
    d.innerHTML = `<input type=checkbox ${on ? 'checked' : ''}><input placeholder="key" value="${esc(k)}"><input placeholder="value" value="${esc(v)}"><button title="Rimuovi" onclick="this.parentElement.remove()">×</button>`;
    $('#' + id).appendChild(d);
    wireKvDrag(d)
}

function wireKvDrag(row) {
    row.addEventListener('dragstart', () => row.classList.add('dragging'));
    row.addEventListener('dragend', () => row.classList.remove('dragging'))
}

function readKV(id) {
    return $$('#' + id + ' .kv').map(r => ({
        enabled: r.children[0].checked,
        key: r.children[1].value,
        value: r.children[2].value
    })).filter(x => x.key)
}

function pretty(x) {
    try {
        return JSON.stringify(JSON.parse(x), null, 2)
    } catch {
        return x
    }
}

function renderAuth() {
    const t = $('#authType').value;
    const f = $('#authFields');
    const input = (n, p = '', type = 'text') => `<input data-auth="${n}" type="${type}" placeholder="${p || n}">`;
    let html = '';
    if (['bearer', 'jwt'].includes(t)) html = input('token', 'Token');
    if (t === 'basic' || t === 'digest') html = input('username', 'Username') + input('password', 'Password', 'password') + input('realm', 'Realm opzionale');
    if (t === 'apiKeyHeader') html = input('key', 'Header name es. X-API-Key') + input('value', 'API key');
    if (t === 'apiKeyQuery') html = input('key', 'Query name es. api_key') + input('value', 'API key');
    if (t === 'oauth2') html = input('accessToken', 'Access Token') + input('clientId', 'Client ID') + input('scope', 'Scope');
    if (t === 'aws') html = input('accessKey', 'Access Key') + input('secretKey', 'Secret Key', 'password') + input('region', 'Region') + input('service', 'Service');
    f.innerHTML = html || '<p class="empty-state">Nessuna autenticazione</p>';
}

function readAuth() {
    let values = {};
    $$('[data-auth]').forEach(i => values[i.dataset.auth] = i.value);
    return {type: $('#authType').value, values}
}

function buildRequest() {
    let method = $('#method').value;
    let body = $('#bodyText').value;
    if ($('#bodyType').value === 'form') body = formEncode(parseMaybe(body));
    if (method === 'GRAPHQL') {
        body = JSON.stringify({
            query: $('#gqlQuery').value,
            variables: parseMaybe($('#gqlVars').value),
            operationName: $('#gqlOperation').value || null
        })
    }
    return {
        id: currentTab.id,
        name: currentTab.name,
        method,
        url: subst($('#url').value),
        headers: readKV('headerRows'),
        queryParams: readKV('paramsRows'),
        auth: readAuth(),
        bodyType: $('#bodyType').value,
        body,
        form: {},
        scripts: {preRequest: $('#preScript').value, tests: $('#testScript').value}
    }
}

function parseMaybe(s) {
    try {
        return s ? JSON.parse(s) : {}
    } catch {
        return {raw: s}
    }
}

function formEncode(obj) {
    if (!obj || typeof obj !== 'object' || obj.raw) return String(obj?.raw || '');
    return Object.entries(obj).map(([k, v]) => encodeURIComponent(k) + '=' + encodeURIComponent(v ?? '')).join('&')
}

function toCurl(req) {
    let parts = ['curl', '-X', req.method === 'GRAPHQL' ? 'POST' : req.method, JSON.stringify(req.url)];
    [...req.headers].forEach(h => {
        if (h.enabled && h.key) parts.push('-H', JSON.stringify(h.key + ': ' + h.value))
    });
    if (req.auth?.type === 'bearer' || req.auth?.type === 'jwt') parts.push('-H', JSON.stringify('Authorization: Bearer ' + (req.auth.values.token || '')));
    if (req.body) parts.push('--data-raw', JSON.stringify(req.body));
    return parts.join(' ')
}

function pmSandbox(response = null) {
    let tests = [];
    return {
        pm: {
            environment: {
                set: (k, v) => {
                    activeEnv[k] = String(v)
                }, get: k => activeEnv[k]
            },
            response: {
                status: response?.status,
                headers: response?.headers,
                json: () => JSON.parse(response?.body || '{}'),
                text: () => response?.body || ''
            },
            test: (name, fn) => {
                try {
                    fn();
                    tests.push({name, pass: true})
                } catch (e) {
                    tests.push({name, pass: false, error: e.message})
                }
            }
        }, tests
    }
}

async function send() {
    if ($('#method').value === 'WEBSOCKET') {
        connectWs();
        return
    }
    const pre = pmSandbox();
    try {
        new Function('pm', $('#preScript').value)(pre.pm)
    } catch (e) {
        toast('Pre-script: ' + e.message)
    }
    const req = buildRequest();
    $('#meta').innerHTML = '<span class="pill">Sending...</span>';
    $('#send').disabled = true;
    try {
        const res = await api('/send', {method: 'POST', body: JSON.stringify(req)});
        if (res.error) {
            $('#meta').textContent = 'Error';
            $('#resBody').textContent = res.error;
            return
        }
        renderResponse(res);
        await loadAll(false);
        toast('Request completata')
    } catch (e) {
        $('#meta').textContent = 'Request fallita';
        $('#resBody').textContent = e.message;
        toast('Errore request')
    } finally {
        $('#send').disabled = false
    }
}

function renderResponse(res) {
    $('#meta').innerHTML = `<span class="pill">Status <b>${res.status} ${esc(res.statusText)}</b></span><span>${res.timeMs} ms</span><span>${res.sizeBytes} B</span><span>${new Date(res.timestamp).toLocaleString()}</span>`;
    $('#resBody').textContent = pretty(res.body);
    $('#resHeaders').textContent = JSON.stringify(res.headers, null, 2);
    $('#resCookies').textContent = (res.cookies || []).join('\n');
    const box = pmSandbox(res);
    try {
        new Function('pm', $('#testScript').value)(box.pm)
    } catch (e) {
        box.tests.push({name: 'Script error', pass: false, error: e.message})
    }
    $('#resTests').innerHTML = box.tests.map(t => `<div class="${t.pass ? 'pass' : 'fail'}">${t.pass ? '✓' : '✕'} ${esc(t.name)}${t.error ? ' - ' + esc(t.error) : ''}</div>`).join('') || 'No tests';
}

async function loadAll(keepEnv = true) {
    collections = await api('/collections');
    envs = await api('/environments');
    history = await api('/history');
    if (!selectedCollectionId || !collections.some(c => c.id === selectedCollectionId)) selectedCollectionId = collections[0]?.id || null;
    renderEnvs(keepEnv);
    renderCollections();
    renderHistory();
    renderTabs();
}

function renderEnvs(keep = true) {
    let sel = $('#envSelect'), prev = sel.value;
    sel.innerHTML = envs.map(e => `<option value="${e.id}">${esc(e.name)}</option>`).join('');
    let e = (keep && envs.find(x => x.id === prev)) || envs.find(x => x.active) || envs[0];
    if (e) {
        sel.value = e.id;
        activeEnv = {...e.variables};
    }
}

function selectCollection(id) {
    selectedCollectionId = id;
    renderCollections();
    toast('Collection selezionata')
}

function selectedCollection() {
    return collections.find(c => c.id === selectedCollectionId) || collections[0]
}

function renderCollections() {
    let el = $('#collections');
    el.innerHTML = collections.map(c => `<div class="collection ${c.id === selectedCollectionId ? 'selected' : ''}" data-cid="${c.id}"><h4 onclick="selectCollection('${c.id}')"><span class="collection-title"><span class="collection-dot"></span>${esc(c.name)}</span><span class="collection-actions"><button title="Duplica" onclick="event.stopPropagation();dupCol('${c.id}')">⧉</button><button title="Esporta" onclick="event.stopPropagation();exportCol('${c.id}')">⇩</button><button title="Elimina" onclick="event.stopPropagation();delCol('${c.id}')">🗑</button></span></h4><div class="collection-meta" onclick="selectCollection('${c.id}')">${(c.requests || []).length} request · clicca per salvare qui</div><div class="req-list" data-cid="${c.id}">${(c.requests || []).map((r, i) => `<div class="req-item" draggable="true" data-cid="${c.id}" data-ridx="${i}" onclick='loadReq(${JSON.stringify(r.request).replaceAll("'", "&#39;")})'><span class="drag-handle">⋮⋮</span><span class="method">${esc(r.request.method)}</span><span>${esc(r.name)}</span></div>`).join('')}</div></div>`).join('') || '<div class="empty-state">Nessuna collection. Creane una o trascina un file Postman JSON.</div>';
    wireRequestDragDrop()
}

function renderHistory() {
    let el = $('#history');
    el.innerHTML = history.map(h => `<div class="req-item" onclick='loadReq(${JSON.stringify(h.request).replaceAll("'", "&#39;")})'><span class="method">${esc(h.request.method)}</span><span>${esc(h.request.url)}</span><span class="pill">${h.response.status}</span></div>`).join('') || '<div class="empty-state">La history apparirà dopo la prima request.</div>'
}

function renderTabs() {
    let t = $('#tabs');
    t.innerHTML = `<div class="tab active">${esc(currentTab.name)} <button onclick="newTab()">+</button></div>`
}

function loadReq(r) {
    currentTab = {id: crypto.randomUUID(), name: r.name || 'Request'};
    $('#method').value = r.method || 'GET';
    $('#url').value = r.url || '';
    $('#bodyText').value = r.body || '';
    $('#headerRows').innerHTML = '';
    (r.headers || []).forEach(x => addKV('headerRows', x.key, x.value, x.enabled));
    $('#paramsRows').innerHTML = '';
    (r.queryParams || []).forEach(x => addKV('paramsRows', x.key, x.value, x.enabled));
    renderTabs();
    toast('Request caricata')
}

function newTab() {
    currentTab = {id: crypto.randomUUID(), name: 'Untitled'};
    $('#url').value = '';
    $('#bodyText').value = '';
    $('#headerRows').innerHTML = '';
    $('#paramsRows').innerHTML = '';
    renderTabs();
    toast('Nuovo tab')
}

async function saveReq() {
    let name = prompt('Nome request', currentTab.name) || currentTab.name;
    currentTab.name = name;
    let c = selectedCollection();
    if (!c) {
        c = {name: 'My Collection', description: '', requests: []};
        selectedCollectionId = null
    }
    c.requests = c.requests || [];
    const req = buildRequest();
    req.name = name;
    c.requests.push({id: crypto.randomUUID(), name, request: req, createdAt: new Date().toISOString()});
    const saved = await api('/collections', {method: 'POST', body: JSON.stringify(c)});
    selectedCollectionId = saved.id || c.id || selectedCollectionId;
    await loadAll();
    toast('Request salvata nella collection selezionata')
}

async function dupCol(id) {
    let c = collections.find(x => x.id === id);
    let saved = await api('/collections', {
        method: 'POST',
        body: JSON.stringify({...c, id: null, name: c.name + ' copy'})
    });
    selectedCollectionId = saved.id;
    loadAll();
    toast('Collection duplicata')
}

async function delCol(id) {
    if (confirm('Eliminare collection?')) {
        await fetch('/apiforge/api/collections/' + id, {method: 'DELETE'});
        if (selectedCollectionId === id) selectedCollectionId = null;
        loadAll();
        toast('Collection eliminata')
    }
}

function exportCol(id) {
    location.href = '/apiforge/api/export/' + id
}

function connectWs() {
    let u = subst($('#url').value);
    try {
        ws = new WebSocket(u);
        ws.onopen = () => logWs('connected ' + u);
        ws.onmessage = e => logWs('← ' + e.data);
        ws.onerror = () => logWs('error');
        ws.onclose = () => logWs('closed')
    } catch (e) {
        toast(e.message)
    }
}

function logWs(s) {
    $('#wsLog').value += `[${new Date().toLocaleTimeString()}] ${s}\n`;
    $('#wsLog').scrollTop = $('#wsLog').scrollHeight
}

function wireRequestDragDrop() {
    $$('.req-item[draggable=true]').forEach(item => {
        item.addEventListener('dragstart', e => {
            dragReq = {cid: item.dataset.cid, idx: +item.dataset.ridx};
            item.classList.add('dragging');
            e.dataTransfer.effectAllowed = 'move'
        });
        item.addEventListener('dragend', () => item.classList.remove('dragging'))
    });
    $$('.req-list').forEach(list => {
        list.addEventListener('dragover', e => {
            if (!dragReq) return;
            e.preventDefault();
            const after = getDragAfterElement(list, e.clientY);
            list.classList.add('drag-over-list');
            const dragging = document.querySelector('.req-item.dragging');
            if (!dragging) return;
            if (after == null) list.appendChild(dragging); else list.insertBefore(dragging, after)
        });
        list.addEventListener('dragleave', () => list.classList.remove('drag-over-list'));
        list.addEventListener('drop', async e => {
            if (!dragReq) return;
            e.preventDefault();
            list.classList.remove('drag-over-list');
            let targetCid = list.dataset.cid;
            let targetIdx = [...list.querySelectorAll('.req-item')].findIndex(x => x.classList.contains('dragging'));
            await moveRequest(dragReq.cid, dragReq.idx, targetCid, targetIdx);
            dragReq = null
        })
    });
    $$('.collection').forEach(col => {
        col.addEventListener('dragover', e => {
            if (dragReq) {
                e.preventDefault();
                col.classList.add('drag-over')
            }
        });
        col.addEventListener('dragleave', () => col.classList.remove('drag-over'));
        col.addEventListener('drop', async e => {
            col.classList.remove('drag-over')
        })
    })
}

function getDragAfterElement(container, y) {
    const els = [...container.querySelectorAll('.req-item:not(.dragging)')];
    return els.reduce((closest, child) => {
        const box = child.getBoundingClientRect();
        const offset = y - box.top - box.height / 2;
        return offset < 0 && offset > closest.offset ? {offset, element: child} : closest
    }, {offset: Number.NEGATIVE_INFINITY}).element
}

async function moveRequest(fromCid, idx, toCid, targetIdx = 999) {
    let from = collections.find(c => c.id === fromCid), to = collections.find(c => c.id === toCid);
    if (!from || !to) return;
    from.requests = from.requests || [];
    to.requests = to.requests || [];
    let [req] = from.requests.splice(idx, 1);
    if (!req) return;
    if (fromCid === toCid && targetIdx > idx) targetIdx--;
    to.requests.splice(Math.max(0, Math.min(targetIdx, to.requests.length)), 0, req);
    await api('/collections', {method: 'POST', body: JSON.stringify(from)});
    if (fromCid !== toCid) await api('/collections', {method: 'POST', body: JSON.stringify(to)});
    selectedCollectionId = toCid;
    await loadAll();
    toast('Request spostata')
}

function wireDropImport() {
    let overlay = $('#dropOverlay');
    ['dragenter', 'dragover'].forEach(ev => document.addEventListener(ev, e => {
        if (e.dataTransfer?.types?.includes('Files')) {
            e.preventDefault();
            overlay.classList.add('show')
        }
    }));
    ['dragleave', 'drop'].forEach(ev => document.addEventListener(ev, e => {
        if (ev === 'drop') e.preventDefault();
        overlay.classList.remove('show')
    }));
    document.addEventListener('drop', async e => {
        const file = e.dataTransfer?.files?.[0];
        if (!file) return;
        try {
            const text = await file.text();
            const raw = JSON.parse(text);
            const col = normalizeImportedCollection(raw);
            const saved = await api('/collections', {method: 'POST', body: JSON.stringify(col)});
            selectedCollectionId = saved.id;
            await loadAll();
            toast('Collection importata e selezionata')
        } catch (err) {
            toast('Import non riuscito: ' + err.message)
        }
    })
}

function normalizeImportedCollection(raw) {
    const items = raw.item || raw.requests || [];
    return {
        name: raw.info?.name || raw.name || 'Imported Collection',
        description: raw.info?.description || raw.description || '',
        requests: items.flatMap(flattenPostmanItem)
    }
}

function flattenPostmanItem(it) {
    if (it.item) return it.item.flatMap(flattenPostmanItem);
    let req = it.request || it;
    let method = req.method || 'GET';
    let url = typeof req.url === 'string' ? req.url : (req.url?.raw || '');
    let headers = (req.header || []).map(h => ({enabled: !h.disabled, key: h.key, value: h.value}));
    let queryParams = (req.url?.query || []).map(q => ({enabled: !q.disabled, key: q.key, value: q.value || ''}));
    let body = req.body?.raw || '';
    return [{
        id: crypto.randomUUID(),
        name: it.name || url || 'Imported request',
        request: {
            id: crypto.randomUUID(),
            name: it.name,
            method,
            url,
            headers,
            queryParams,
            auth: {type: 'none', values: {}},
            bodyType: req.body?.mode || 'none',
            body,
            form: {},
            scripts: {preRequest: '', tests: ''}
        },
        createdAt: new Date().toISOString()
    }]
}

function wireSplitter() {
    const sp = $('#splitter');
    let down = false;
    sp.addEventListener('pointerdown', e => {
        down = true;
        sp.setPointerCapture(e.pointerId)
    });
    sp.addEventListener('pointermove', e => {
        if (!down) return;
        const h = window.innerHeight;
        const pct = Math.max(25, Math.min(65, 100 - (e.clientY / h * 100)));
        document.documentElement.style.setProperty('--response', pct + '%')
    });
    sp.addEventListener('pointerup', () => down = false)
}

$$('.subtabs button[data-tab]').forEach(b => b.onclick = () => {
    if (document.startViewTransition) document.startViewTransition(() => switchPanel(b)); else switchPanel(b)
});

function switchPanel(b) {
    $$('.panel').forEach(p => p.classList.remove('active'));
    $$('[data-tab]').forEach(x => x.classList.remove('active'));
    b.classList.add('active');
    $('#' + b.dataset.tab).classList.add('active')
}

$$('[data-rtab]').forEach(b => b.onclick = () => {
    $$('.rpanel').forEach(p => p.classList.remove('active'));
    $$('[data-rtab]').forEach(x => x.classList.remove('active'));
    b.classList.add('active');
    $('#' + b.dataset.rtab).classList.add('active')
});
$$('.side-tabs button').forEach(b => b.onclick = () => {
    $$('.side-panel').forEach(p => p.classList.add('hidden'));
    $$('.side-tabs button').forEach(x => x.classList.remove('active'));
    b.classList.add('active');
    $('#' + b.dataset.side).classList.remove('hidden')
});
$('#authType').onchange = renderAuth;
$('#send').onclick = send;
$('#copyCurl').onclick = async () => {
    await navigator.clipboard.writeText(toCurl(buildRequest()));
    toast('cURL copiato')
};
$('#copyResponse').onclick = async () => {
    await navigator.clipboard.writeText($('#resBody').textContent || '');
    toast('Body copiato')
};
document.addEventListener('keydown', e => {
    if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') send()
});
$('#saveReq').onclick = saveReq;
$('#beautify').onclick = () => $('#bodyText').value = pretty($('#bodyText').value);
$('#prettifyGql').onclick = () => $('#gqlQuery').value = $('#gqlQuery').value.replace(/\s+/g, ' ').replace(/{/g, '{\n  ').replace(/}/g, '\n}');
$('#wsConnect').onclick = connectWs;
$('#wsClose').onclick = () => ws?.close();
$('#wsSend').onclick = () => {
    ws?.send($('#wsMsg').value);
    logWs('→ ' + $('#wsMsg').value)
};
$('#newCollection').onclick = async () => {
    let name = prompt('Nome collection', 'My Collection');
    if (name) {
        const saved = await api('/collections', {
            method: 'POST',
            body: JSON.stringify({name, description: '', requests: []})
        });
        selectedCollectionId = saved.id;
        await loadAll();
        toast('Collection creata e selezionata')
    }
};
$('#envSelect').onchange = () => {
    let e = envs.find(x => x.id === $('#envSelect').value);
    activeEnv = {...e.variables};
    toast('Environment: ' + e.name)
};
$('#themePulse').onclick = () => {
    document.body.classList.toggle('focus');
    toast('Tema focus ' + (document.body.classList.contains('focus') ? 'attivo' : 'disattivo'))
};
$('#clearUi').onclick = () => newTab();
addKV('headerRows', 'Accept', 'application/json', true);
renderAuth();
wireDropImport();
wireSplitter();
loadAll();
