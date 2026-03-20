(() => {
  'use strict';

  const TOKEN_KEYS = ['nebulaops.v23_1.jwt', 'nebulaops.jwt', 'nebulaops.token'];
  const ACTIONS = ['start', 'stop', 'restart', 'status', 'open'];
  const FORBIDDEN_EXTENSION_TITLES = new Set([
    'Runbook Center',
    'Extension Registry',
    'EventOps Center',
    'Observability Lens',
    'GitOps Center',
    'Secrets & Config Center',
    'SLO Center',
    'Backup & Recovery Center'
  ]);
  const OPEN_FALLBACK = {
    apiforge: '/apiforge/',
    kubebridge: '/kubebridge/',
    'contract-hub': '/contract-hub/'
  };

  let modal = null;
  let items = [];
  let selected = 'apiforge';
  let busy = false;

  function token() {
    for (const key of TOKEN_KEYS) {
      const value = localStorage.getItem(key);
      if (value) return value;
    }
    return '';
  }

  function headers(json = false) {
    const h = json ? { 'Content-Type': 'application/json' } : {};
    const jwt = token();
    if (jwt) h.Authorization = `Bearer ${jwt}`;
    return h;
  }

  function esc(value) {
    return String(value ?? '').replace(/[&<>"']/g, (c) => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
  }

  function injectStyles() {
    if (document.getElementById('nebulaops-extension-control-panel-style')) return;
    const style = document.createElement('style');
    style.id = 'nebulaops-extension-control-panel-style';
    style.textContent = `
      .sidebar-footer.neb-footer-split{align-items:stretch!important;flex-direction:column!important;gap:10px!important;}
      .neb-footer-actions{display:grid;grid-template-columns:1fr 1fr;gap:8px;width:100%;}
      .neb-footer-actions .launcher-trigger{justify-content:center;width:100%;}
      .neb-extensions-trigger{border-color:rgba(122,223,255,.30)!important;color:#7adfff!important;background:rgba(122,223,255,.08)!important;}
      .neb-ext-overlay{position:fixed;inset:0;z-index:220;background:rgba(0,0,0,.72);display:grid;place-items:center;padding:24px;}
      .neb-ext-panel{width:min(1180px,100%);max-height:min(820px,calc(100vh - 48px));overflow:hidden;border-radius:28px;border:1px solid rgba(130,170,255,.18);background:linear-gradient(145deg,rgba(14,23,51,.96),rgba(5,8,22,.98));box-shadow:0 28px 90px rgba(0,0,0,.55),inset 0 1px 0 rgba(255,255,255,.07);color:#eef6ff;font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;display:flex;flex-direction:column;}
      .neb-ext-head{display:flex;justify-content:space-between;gap:18px;align-items:flex-start;padding:24px 24px 0;}
      .neb-ext-kicker{margin:0 0 7px;color:#7adfff;font-size:11px;font-weight:900;letter-spacing:.22em;text-transform:uppercase;}
      .neb-ext-head h2{margin:0 0 5px;font-size:clamp(20px,2.2vw,30px);line-height:1.08;font-weight:950;}
      .neb-ext-head small{color:#92a3ca;font-size:12.5px;line-height:1.5;display:block;max-width:760px;}
      .neb-ext-close{width:38px;height:38px;border-radius:12px;border:1px solid rgba(255,255,255,.12);background:rgba(255,255,255,.07);color:#dbeafe;cursor:pointer;font-size:22px;line-height:1;}
      .neb-ext-close:hover{background:rgba(255,255,255,.12);color:#fff;}
      .neb-ext-toolbar{display:flex;align-items:center;gap:10px;margin:18px 24px;padding:11px 14px;background:rgba(255,255,255,.05);border:1px solid rgba(140,180,255,.16);border-radius:16px;}
      .neb-ext-toolbar input{flex:1;background:transparent;border:0;outline:none;color:#eef6ff;font:inherit;font-size:14px;}
      .neb-ext-toolbar button{border:1px solid rgba(122,223,255,.24);background:rgba(122,223,255,.08);border-radius:10px;padding:8px 12px;color:#7adfff;font-weight:900;cursor:pointer;}
      .neb-ext-toolbar button:disabled{opacity:.45;cursor:wait;}
      .neb-ext-content{display:grid;grid-template-columns:330px minmax(0,1fr);gap:14px;padding:0 24px 24px;overflow:hidden;min-height:0;}
      .neb-ext-list,.neb-ext-detail{border:1px solid rgba(140,180,255,.14);background:rgba(255,255,255,.045);border-radius:22px;padding:16px;min-height:0;overflow:auto;}
      .neb-ext-list-inner{display:grid;gap:10px;}
      .neb-ext-item{width:100%;text-align:left;border:1px solid rgba(140,180,255,.13);background:rgba(255,255,255,.055);color:#eef6ff;border-radius:18px;padding:14px;cursor:pointer;}
      .neb-ext-item:hover,.neb-ext-item.active{border-color:rgba(122,223,255,.45);background:rgba(122,223,255,.08);}
      .neb-ext-item b{display:flex;align-items:center;gap:8px;font-size:14px;}
      .neb-ext-item small{display:block;color:#8ea0c3;margin-top:5px;line-height:1.4;}
      .neb-ext-state{display:inline-flex;align-items:center;gap:7px;border-radius:999px;padding:5px 9px;font-size:10px;font-weight:950;letter-spacing:.05em;text-transform:uppercase;border:1px solid rgba(255,255,255,.1);background:rgba(255,255,255,.06);color:#b7c7e8;margin-top:9px;}
      .neb-ext-state:before{content:'';width:8px;height:8px;border-radius:50%;background:#ffcf8a;box-shadow:0 0 14px rgba(255,207,138,.65);}
      .neb-ext-state.running:before{background:#70f0b4;box-shadow:0 0 16px rgba(112,240,180,.85);}
      .neb-ext-state.stopped:before,.neb-ext-state.unavailable:before{background:#ff8fa3;box-shadow:0 0 16px rgba(255,143,163,.75);}
      .neb-ext-metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin:12px 0 16px;}
      .neb-ext-metric{border:1px solid rgba(140,180,255,.14);background:rgba(255,255,255,.055);border-radius:16px;padding:13px;}
      .neb-ext-metric small{display:block;color:#6b7fa8;font-size:10px;font-weight:900;text-transform:uppercase;letter-spacing:.08em;margin-bottom:6px;}
      .neb-ext-metric b{display:block;font-size:15px;word-break:break-word;}
      .neb-ext-actions{display:flex;gap:9px;flex-wrap:wrap;margin:0 0 16px;}
      .neb-ext-action{border:1px solid rgba(122,223,255,.25);border-radius:13px;padding:10px 13px;background:rgba(122,223,255,.08);color:#7adfff;font-weight:950;cursor:pointer;text-decoration:none;display:inline-flex;align-items:center;gap:7px;}
      .neb-ext-action.primary{background:linear-gradient(135deg,#00d9ff,#7b61ff);border-color:transparent;color:#fff;}
      .neb-ext-action.warn{border-color:rgba(255,207,138,.34);background:rgba(255,207,138,.09);color:#ffcf8a;}
      .neb-ext-action.danger{border-color:rgba(255,95,130,.32);background:rgba(255,95,130,.09);color:#ff9db2;}
      .neb-ext-action:disabled{opacity:.45;cursor:wait;}
      .neb-ext-log{white-space:pre-wrap;word-break:break-word;background:rgba(0,0,0,.38);border:1px solid rgba(255,255,255,.08);border-radius:16px;padding:14px;color:#b7c7e8;min-height:210px;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px;line-height:1.45;}
      .neb-ext-hidden-card{display:none!important;}
      @media(max-width:920px){.neb-ext-content{grid-template-columns:1fr}.neb-ext-metrics{grid-template-columns:1fr 1fr}.neb-footer-actions{grid-template-columns:1fr;}}
      @media(max-width:560px){.neb-ext-metrics{grid-template-columns:1fr}.neb-ext-overlay{padding:10px}.neb-ext-head{padding:18px 18px 0}.neb-ext-toolbar{margin:14px 18px}.neb-ext-content{padding:0 18px 18px;}}
    `;
    document.head.appendChild(style);
  }

  function stateClass(state) {
    const s = String(state || '').toUpperCase();
    if (s === 'RUNNING' || s === 'UP') return 'running';
    if (s === 'STOPPED' || s === 'DOWN') return 'stopped';
    if (s === 'UNAVAILABLE' || s === 'FAILED') return 'unavailable';
    return 'checking';
  }

  function stateText(item) {
    return item?.state || item?.status || 'UNKNOWN';
  }

  function selectedItem() {
    return items.find((item) => item.id === selected) || items[0] || null;
  }

  function log(value) {
    const el = modal?.querySelector('[data-ext-log]');
    if (!el) return;
    el.textContent = typeof value === 'string' ? value : JSON.stringify(value, null, 2);
  }

  function setBusy(value) {
    busy = value;
    modal?.querySelectorAll('[data-ext-action], [data-ext-refresh]').forEach((btn) => {
      if (btn.tagName === 'BUTTON') btn.disabled = value;
    });
  }

  function renderList() {
    const box = modal?.querySelector('[data-ext-list]');
    if (!box) return;
    const q = (modal?.querySelector('[data-ext-search]')?.value || '').trim().toLowerCase();
    const filtered = items.filter((item) => !q || [item.title, item.id, item.category, item.state].join(' ').toLowerCase().includes(q));
    if (!filtered.length) {
      box.innerHTML = '<div class="neb-ext-log">No installed extension matched the current filter.</div>';
      return;
    }
    box.innerHTML = filtered.map((item) => {
      const state = stateText(item);
      return `<button class="neb-ext-item ${item.id === selected ? 'active' : ''}" data-ext-id="${esc(item.id)}">
        <b><span>${esc(item.icon || '◈')}</span>${esc(item.title || item.id)}</b>
        <small>${esc(item.category || 'NebulaOps Extension')} · ${esc(item.id)}</small>
        <span class="neb-ext-state ${stateClass(state)}">${esc(state)}</span>
      </button>`;
    }).join('');
    box.querySelectorAll('[data-ext-id]').forEach((btn) => {
      btn.addEventListener('click', () => {
        selected = btn.getAttribute('data-ext-id') || selected;
        render();
        refreshStatus(selected).catch((error) => log(String(error.message || error)));
      });
    });
  }

  function renderDetail(item) {
    const detail = modal?.querySelector('[data-ext-detail]');
    if (!detail) return;
    if (!item) {
      detail.innerHTML = '<p class="neb-ext-kicker">EXTENSIONS</p><div class="neb-ext-log">Extension control is unavailable. Verify gateway-service and /api/extensions.</div>';
      return;
    }
    const state = stateText(item);
    const openUrl = item.openUrl || OPEN_FALLBACK[item.id] || `/${item.id}/`;
    detail.innerHTML = `
      <p class="neb-ext-kicker">EXTENSION CONTROL</p>
      <h2 style="margin:0 0 8px;font-size:26px;line-height:1.1">${esc(item.icon || '◈')} ${esc(item.title || item.id)}</h2>
      <span class="neb-ext-state ${stateClass(state)}">${esc(state)}</span>
      <div class="neb-ext-metrics">
        <div class="neb-ext-metric"><small>Deployment</small><b>${esc(item.deployment || 'UNKNOWN')}</b></div>
        <div class="neb-ext-metric"><small>Ready</small><b>${esc((item.readyReplicas ?? 0) + '/' + (item.replicas ?? 0))}</b></div>
        <div class="neb-ext-metric"><small>Service</small><b>${esc(item.service || 'UNKNOWN')}</b></div>
        <div class="neb-ext-metric"><small>Gateway proxy</small><b>${esc(item.gatewayProxy || 'UNKNOWN')}</b></div>
      </div>
      <div class="neb-ext-actions">
        <button class="neb-ext-action primary" data-ext-action="start">▶ Start</button>
        <button class="neb-ext-action danger" data-ext-action="stop">■ Stop</button>
        <button class="neb-ext-action warn" data-ext-action="restart">↻ Restart</button>
        <button class="neb-ext-action" data-ext-action="status">● Status</button>
        <a class="neb-ext-action" href="${esc(openUrl)}" target="_blank" rel="noopener noreferrer">↗ Open</a>
      </div>
      <div class="neb-ext-log" data-ext-log>${esc(JSON.stringify(item, null, 2))}</div>
    `;
    detail.querySelectorAll('[data-ext-action]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const action = btn.getAttribute('data-ext-action');
        if (action === 'status') refreshStatus(item.id).catch((error) => log(String(error.message || error)));
        else runAction(item.id, action).catch((error) => log(String(error.message || error)));
      });
    });
  }

  function render() {
    renderList();
    renderDetail(selectedItem());
  }

  async function loadExtensions() {
    const response = await fetch('/api/extensions', { headers: headers() });
    if (!response.ok) throw new Error(`Extension control returned HTTP ${response.status}`);
    const body = await response.json();
    items = Array.isArray(body.items) ? body.items : [];
    if (!items.find((item) => item.id === selected) && items[0]) selected = items[0].id;
    render();
    log(body);
    return body;
  }

  async function refreshStatus(id = selected) {
    const response = await fetch(`/api/extensions/${encodeURIComponent(id)}/status`, { headers: headers() });
    if (!response.ok) throw new Error(`Status returned HTTP ${response.status}`);
    const body = await response.json();
    const index = items.findIndex((item) => item.id === id);
    if (index >= 0) items[index] = body;
    else items.push(body);
    selected = id;
    render();
    log(body);
    return body;
  }

  async function runAction(id, action) {
    if (!ACTIONS.includes(action) || action === 'open') return;
    setBusy(true);
    log(`Executing ${action} for ${id} ...`);
    try {
      const response = await fetch(`/api/extensions/${encodeURIComponent(id)}/${action}`, {
        method: 'POST',
        headers: headers(true),
        body: '{}'
      });
      const body = await response.json().catch(() => ({}));
      log(body);
      await loadExtensions();
      if (!response.ok || body.ok === false) throw new Error(body.error || body.code || `HTTP ${response.status}`);
      return body;
    } finally {
      setBusy(false);
    }
  }

  function openPanel() {
    injectStyles();
    if (modal) modal.remove();
    modal = document.createElement('section');
    modal.className = 'neb-ext-overlay';
    modal.innerHTML = `
      <article class="neb-ext-panel" role="dialog" aria-modal="true" aria-label="NebulaOps Extensions">
        <header class="neb-ext-head">
          <div>
            <p class="neb-ext-kicker">NEBULAOPS EXTENSIONS</p>
            <h2>Extension Control Center</h2>
            <small>View live status for installed extensions. All extensions are disabled by default and start only through an explicit Start/Deploy action. No mock data is rendered: status comes from Kubernetes and the gateway.</small>
          </div>
          <button class="neb-ext-close" type="button" aria-label="Close">×</button>
        </header>
        <div class="neb-ext-toolbar">
          <input data-ext-search placeholder="Search extension, state or runtime..." />
          <button type="button" data-ext-refresh>Refresh</button>
        </div>
        <div class="neb-ext-content">
          <aside class="neb-ext-list"><p class="neb-ext-kicker">INSTALLED</p><div class="neb-ext-list-inner" data-ext-list></div></aside>
          <main class="neb-ext-detail" data-ext-detail><div class="neb-ext-log">Loading extensions from /api/extensions ...</div></main>
        </div>
      </article>`;
    document.body.appendChild(modal);
    modal.addEventListener('click', (event) => {
      if (event.target === modal) closePanel();
    });
    modal.querySelector('.neb-ext-close')?.addEventListener('click', closePanel);
    modal.querySelector('[data-ext-refresh]')?.addEventListener('click', () => loadExtensions().catch((error) => log(String(error.message || error))));
    modal.querySelector('[data-ext-search]')?.addEventListener('input', renderList);
    loadExtensions().catch((error) => {
      items = [];
      render();
      log(`EXTENSION_CONTROL_UNAVAILABLE\n${String(error.message || error)}`);
    });
  }

  function closePanel() {
    if (modal) modal.remove();
    modal = null;
  }

  function installSidebarButton() {
    injectStyles();
    const footer = document.querySelector('.sidebar-footer');
    if (!footer) return;
    const launcher = footer.querySelector('.launcher-trigger:not(.neb-extensions-trigger)');
    if (!launcher) return;
    footer.classList.add('neb-footer-split');
    let actions = footer.querySelector('.neb-footer-actions');
    if (!actions) {
      actions = document.createElement('div');
      actions.className = 'neb-footer-actions';
      footer.appendChild(actions);
    }
    if (!actions.contains(launcher)) actions.appendChild(launcher);
    let btn = footer.querySelector('.neb-extensions-trigger');
    if (!btn) {
      btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'launcher-trigger neb-extensions-trigger';
      btn.innerHTML = '<span aria-hidden="true">🧩</span><span>Extensions</span><kbd>UI</kbd>';
      actions.appendChild(btn);
    } else if (!actions.contains(btn)) {
      actions.appendChild(btn);
    }
    if (!btn.dataset.nebExtBound) {
      btn.dataset.nebExtBound = 'true';
      btn.addEventListener('click', (event) => {
        event.preventDefault();
        event.stopPropagation();
        openPanel();
      });
    }
  }

  function titleOf(card) {
    return (card.querySelector('.launcher-card-body b')?.textContent || card.querySelector('b')?.textContent || '').trim();
  }

  function scrubAppBar() {
    document.querySelectorAll('.launcher-card').forEach((card) => {
      const group = (card.querySelector('.launcher-group-tag')?.textContent || '').trim().toLowerCase();
      const title = titleOf(card);
      if (group === 'extensions' || FORBIDDEN_EXTENSION_TITLES.has(title)) {
        card.classList.add('neb-ext-hidden-card');
        card.setAttribute('aria-hidden', 'true');
      }
    });
  }

  function tick() {
    installSidebarButton();
    scrubAppBar();
  }

  const observer = new MutationObserver(tick);
  observer.observe(document.documentElement, { childList: true, subtree: true });
  document.addEventListener('DOMContentLoaded', tick);
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && modal) closePanel();
  });
  setInterval(tick, 1200);
})();
