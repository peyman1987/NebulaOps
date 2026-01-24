/* NebulaOps v22.4 auth bridge
 * Makes shell-loaded and standalone MFE requests share a valid Bearer token.
 * - Shell origin: reuses localStorage token or bootstraps dev admin token.
 * - Standalone MFE route (/remotes/<mfe>/): uses the same nebulaops.localhost origin and the shared shell token.
 * - Patches fetch/XMLHttpRequest so the first API request waits for token availability.
 * - Rewrites legacy relative runtime endpoints to the gateway API path.
 */
(function nebulaopsAuthBridge() {
  'use strict';
  var VERSION = 'v22.4.9-restore-ui-live-api';
  var JWT_KEY = 'nebulaops.v22_4.jwt';
  var USER_KEY = 'nebulaops.v22_4.user';
  var LOGIN_URL = '/api/auth/login';
  var DEV_LOGIN_BODY = JSON.stringify({ email: 'admin', password: 'admin' });

  if (window.__NEBULAOPS_AUTH_BRIDGE_VERSION__ === VERSION) {
    return;
  }
  window.__NEBULAOPS_AUTH_BRIDGE_VERSION__ = VERSION;

  function safeGet(key) {
    try { return window.localStorage.getItem(key) || ''; } catch (_) { return ''; }
  }

  function safeSet(key, value) {
    try { if (value) window.localStorage.setItem(key, value); } catch (_) {}
  }

  function safeRemove(key) {
    try { window.localStorage.removeItem(key); } catch (_) {}
  }

  function clearToken() {
    window.__NEBULAOPS_ACCESS_TOKEN__ = '';
    safeRemove(JWT_KEY);
  }

  function tokenLooksExpired(token) {
    try {
      var parts = String(token || '').split('.');
      if (parts.length < 2) return false;
      var payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
      return !!payload.exp && (payload.exp * 1000) < (Date.now() + 30000);
    } catch (_) {
      return false;
    }
  }

  function getUrlToken() {
    try {
      var url = new URL(window.location.href);
      var candidates = [
        url.searchParams.get('access_token'),
        url.searchParams.get('token'),
        url.searchParams.get('nebulaops_token')
      ];
      if (url.hash) {
        var hash = new URLSearchParams(url.hash.replace(/^#/, ''));
        candidates.push(hash.get('access_token'), hash.get('token'), hash.get('nebulaops_token'));
      }
      for (var i = 0; i < candidates.length; i++) {
        if (candidates[i] && candidates[i].length > 16) return candidates[i];
      }
    } catch (_) {}
    return '';
  }

  function publishToken(token, user) {
    if (!token) return token;
    safeSet(JWT_KEY, token);
    safeSet(USER_KEY, user || safeGet(USER_KEY) || 'admin');
    window.__NEBULAOPS_ACCESS_TOKEN__ = token;
    try {
      window.dispatchEvent(new CustomEvent('nebulaops:auth-token', { detail: { token: token, source: 'auth-bridge' } }));
    } catch (_) {}
    return token;
  }

  function currentToken() {
    return window.__NEBULAOPS_ACCESS_TOKEN__ || safeGet(JWT_KEY) || '';
  }

  function isApiLikeUrl(url) {
    var text = String(url || '');
    if (!text) return false;
    if (text.indexOf('/api/') === 0) return true;
    if (/^https?:\/\/localhost:\d+\/api\//.test(text)) return true;
    if (/^https?:\/\/127\.0\.0\.1:\d+\/api\//.test(text)) return true;
    return !!legacyEndpointToGateway(text);
  }

  function isAuthEndpoint(url) {
    var text = String(url || '');
    return text.indexOf('/api/auth/login') !== -1 ||
           text.indexOf('/api/auth/register') !== -1 ||
           text.indexOf('/api/auth/refresh') !== -1 ||
           text.indexOf('/realms/') !== -1 ||
           text.indexOf('/protocol/openid-connect/') !== -1;
  }

  function legacyEndpointToGateway(url) {
    var text = String(url || '').replace(/^\.\//, '').replace(/^\//, '');
    text = text.split('?')[0].split('#')[0];
    var map = {
      'containers': '/api/runtime/docker/containers',
      'images': '/api/runtime/docker/images',
      'volumes': '/api/runtime/docker/volumes',
      'networks': '/api/runtime/docker/networks',
      'docker/containers': '/api/runtime/docker/containers',
      'docker/images': '/api/runtime/docker/images',
      'docker/volumes': '/api/runtime/docker/volumes',
      'docker/networks': '/api/runtime/docker/networks',
      'kubernetes/snapshot': '/api/kubernetes/snapshot',
      'helm/releases': '/api/runtime/helm/releases?namespace=all',
      'tasks': '/api/tasks?organizationId=default-org',
      'notifications': '/api/notifications/live',
      'events': '/api/events',
      'releases': '/api/releases',
      'policies': '/api/policies',
      'cost/summary': '/api/cost/summary'
    };
    return map[text] || '';
  }

  function normalizeUrl(url) {
    var replacement = legacyEndpointToGateway(url);
    if (!replacement) return url;
    try {
      var original = String(url || '');
      var query = original.indexOf('?') >= 0 ? original.slice(original.indexOf('?')) : '';
      if (replacement.indexOf('?') >= 0) query = '';
      return replacement + query;
    } catch (_) {
      return replacement;
    }
  }

  async function loginDevAdmin() {
    try {
      var response = await window.fetch(LOGIN_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
        body: DEV_LOGIN_BODY,
        cache: 'no-store',
        credentials: 'same-origin'
      });
      if (!response || !response.ok) return '';
      var payload = await response.json().catch(function () { return {}; });
      var token = payload.accessToken || payload.access_token || '';
      var user = payload.user && (payload.user.email || payload.user.displayName || payload.user.username);
      return publishToken(token, user || 'admin');
    } catch (err) {
      console.warn('[NebulaOps auth bridge] dev login failed:', err && err.message ? err.message : err);
      return '';
    }
  }

  async function ensureToken() {
    var tokenFromUrl = getUrlToken();
    if (tokenFromUrl) return publishToken(tokenFromUrl, 'admin');
    var existing = currentToken();
    if (existing && !tokenLooksExpired(existing)) return existing;
    if (existing) clearToken();
    return loginDevAdmin();
  }

  window.__NEBULAOPS_GET_ACCESS_TOKEN__ = currentToken;
  window.__NEBULAOPS_ENSURE_ACCESS_TOKEN__ = ensureToken;
  window.__NEBULAOPS_REFRESH_TOKEN__ = function(){ clearToken(); return loginDevAdmin(); };
  window.__NEBULAOPS_AUTH_READY__ = window.__NEBULAOPS_AUTH_READY__ || ensureToken();

  if (window.fetch && !window.__NEBULAOPS_FETCH_AUTH_PATCHED__) {
    var nativeFetch = window.fetch.bind(window);
    window.__NEBULAOPS_FETCH_AUTH_PATCHED__ = true;
    window.fetch = async function nebulaopsFetch(input, init) {
      var requestUrl = typeof input === 'string' ? input : (input && input.url) || '';
      var rewrittenUrl = normalizeUrl(requestUrl);
      var shouldAuth = isApiLikeUrl(rewrittenUrl) && !isAuthEndpoint(rewrittenUrl);
      if (!shouldAuth) {
        if (rewrittenUrl !== requestUrl && typeof input === 'string') return nativeFetch(rewrittenUrl, init);
        return nativeFetch(input, init);
      }
      var token = await ensureToken();
      var nextInit = Object.assign({}, init || {});
      var headers = new Headers(nextInit.headers || (input instanceof Request ? input.headers : undefined));
      if (token && !headers.has('Authorization')) headers.set('Authorization', 'Bearer ' + token);
      headers.set('X-NebulaOps-Auth-Bridge', VERSION);
      nextInit.headers = headers;
      var response;
      if (input instanceof Request) {
        input = new Request(rewrittenUrl !== requestUrl ? rewrittenUrl : input, nextInit);
        response = await nativeFetch(input);
      } else {
        response = await nativeFetch(rewrittenUrl, nextInit);
      }
      if (response && response.status === 401 && !nextInit.__nebulaopsAuthRetry) {
        clearToken();
        var freshToken = await loginDevAdmin();
        if (freshToken) {
          var retryInit = Object.assign({}, nextInit, { __nebulaopsAuthRetry: true });
          var retryHeaders = new Headers(retryInit.headers || {});
          retryHeaders.set('Authorization', 'Bearer ' + freshToken);
          retryHeaders.set('X-NebulaOps-Auth-Bridge', VERSION);
          retryInit.headers = retryHeaders;
          delete retryInit.__nebulaopsAuthRetry;
          if (input instanceof Request) {
            var retryRequest = new Request(rewrittenUrl !== requestUrl ? rewrittenUrl : input.url, retryInit);
            return nativeFetch(retryRequest);
          }
          return nativeFetch(rewrittenUrl, retryInit);
        }
      }
      return response;
    };
  }

  if (window.XMLHttpRequest && !window.__NEBULAOPS_XHR_AUTH_PATCHED__) {
    var XHR = window.XMLHttpRequest;
    var nativeOpen = XHR.prototype.open;
    var nativeSend = XHR.prototype.send;
    window.__NEBULAOPS_XHR_AUTH_PATCHED__ = true;

    XHR.prototype.open = function nebulaopsOpen(method, url) {
      var rewrittenUrl = normalizeUrl(url);
      this.__nebulaopsRequestUrl = rewrittenUrl;
      this.__nebulaopsNeedsAuth = isApiLikeUrl(rewrittenUrl) && !isAuthEndpoint(rewrittenUrl);
      var args = Array.prototype.slice.call(arguments);
      args[1] = rewrittenUrl;
      return nativeOpen.apply(this, args);
    };

    XHR.prototype.send = function nebulaopsSend(body) {
      var xhr = this;
      if (!xhr.__nebulaopsNeedsAuth) return nativeSend.call(xhr, body);
      window.__NEBULAOPS_REFRESH_TOKEN__ = function(){ clearToken(); return loginDevAdmin(); };
  window.__NEBULAOPS_AUTH_READY__ = window.__NEBULAOPS_AUTH_READY__ || ensureToken();
      window.__NEBULAOPS_AUTH_READY__.then(function (token) {
        try {
          if (token) xhr.setRequestHeader('Authorization', 'Bearer ' + token);
          xhr.setRequestHeader('X-NebulaOps-Auth-Bridge', VERSION);
        } catch (_) {}
        nativeSend.call(xhr, body);
      }).catch(function () {
        nativeSend.call(xhr, body);
      });
      return undefined;
    };
  }
})();


/* NebulaOps v22.4.6-live-real-data-observability-audit · live endpoint custom element.
   This bundle does not contain seeded records. It renders only responses returned by NebulaOps APIs. */
(function nebulaopsLiveRemote(){
  'use strict';
  const CFG = {
  "tag": "nebulaops-mfe-observability",
  "title": "Observability & Audit Center",
  "scope": "SRE · Metrics/Logs/Traces/Audit",
  "port": 4214,
  "id": "observability",
  "config": {
    "endpoints": [
      {
        "label": "Platform overview",
        "url": "/api/observability/overview?organizationId=default-org",
        "kind": "overview",
        "itemsPath": "items"
      },
      {
        "label": "Service health",
        "url": "/api/observability/services",
        "kind": "health",
        "itemsPath": "items"
      },
      {
        "label": "Observability stack",
        "url": "/api/observability/stack",
        "kind": "probe",
        "itemsPath": "items"
      },
      {
        "label": "Prometheus up",
        "url": "/api/observability/metrics/prometheus?query=up",
        "kind": "metric",
        "itemsPath": "items"
      },
      {
        "label": "HTTP request rate",
        "url": "/api/observability/metrics/prometheus?query=sum(rate(http_server_requests_seconds_count%5B5m%5D))",
        "kind": "metric",
        "itemsPath": "items"
      },
      {
        "label": "Loki logs",
        "url": "/api/observability/logs/loki?query=%7Bjob%3D~%22.%2B%22%7D",
        "kind": "log",
        "itemsPath": "items"
      },
      {
        "label": "Tempo traces",
        "url": "/api/observability/traces/tempo?limit=20",
        "kind": "trace",
        "itemsPath": "items"
      },
      {
        "label": "Audit trail",
        "url": "/api/observability/audit/events?limit=100",
        "kind": "audit",
        "itemsPath": "items"
      },
      {
        "label": "Task events",
        "url": "/api/observability/events/tasks?organizationId=default-org",
        "kind": "task",
        "itemsPath": "items"
      },
      {
        "label": "Notification events",
        "url": "/api/observability/events/notifications?limit=100",
        "kind": "notification",
        "itemsPath": "items"
      },
      {
        "label": "RabbitMQ overview",
        "url": "/api/observability/events/rabbitmq",
        "kind": "broker",
        "itemsPath": "items"
      }
    ]
  }
};
  const VERSION = 'v22.4.6-live-real-data-observability-audit';
  const TOKEN_KEY = 'nebulaops.v22_4.jwt';
  const css = `
    :host{display:block;color:#eaf4ff;font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;}
    .mfe{min-height:640px;padding:28px;background:radial-gradient(circle at top left,rgba(25,179,255,.18),transparent 36%),linear-gradient(135deg,#07111f,#0b1026 58%,#11152d);}
    .hero{display:flex;gap:18px;align-items:center;justify-content:space-between;flex-wrap:wrap;margin-bottom:22px}.hero-left{display:flex;gap:16px;align-items:center;min-width:280px}.icon{width:72px;height:72px;border-radius:22px;display:grid;place-items:center;background:linear-gradient(135deg,#00c8ff,#8b5cf6);box-shadow:0 18px 40px rgba(0,0,0,.3);font-size:34px}.eyebrow{letter-spacing:.22em;text-transform:uppercase;color:#6ee7ff;font-size:12px;font-weight:800}.title{margin:4px 0 6px;font-size:34px;line-height:1;font-weight:900}.subtitle{color:#a8b7d8;font-size:15px;max-width:780px}.actions{display:flex;gap:10px;flex-wrap:wrap}.btn{border:1px solid rgba(125,211,252,.28);background:rgba(14,24,48,.82);color:#eaf4ff;border-radius:12px;padding:10px 14px;font-weight:800;cursor:pointer;text-decoration:none}.btn.primary{background:linear-gradient(135deg,#0ea5e9,#7c3aed);border:0}.btn.danger{border-color:rgba(248,113,113,.45);color:#fecaca}.grid{display:grid;grid-template-columns:280px 1fr;gap:18px}.side{border:1px solid rgba(148,163,184,.18);background:rgba(8,15,34,.72);border-radius:22px;padding:14px;align-self:start;position:sticky;top:12px}.side h3{font-size:12px;text-transform:uppercase;letter-spacing:.18em;color:#78e0ff;margin:4px 8px 12px}.navbtn{width:100%;text-align:left;display:flex;justify-content:space-between;gap:10px;margin:6px 0;border:1px solid transparent;background:transparent;color:#b7c6e6;border-radius:14px;padding:11px 12px;cursor:pointer;font-weight:800}.navbtn.active,.navbtn:hover{background:rgba(14,165,233,.13);border-color:rgba(14,165,233,.35);color:#fff}.count{font-size:11px;padding:2px 8px;border-radius:999px;background:rgba(14,165,233,.18);border:1px solid rgba(14,165,233,.3)}.content{min-width:0}.cards{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin-bottom:18px}.card{border:1px solid rgba(148,163,184,.18);background:linear-gradient(180deg,rgba(16,26,55,.86),rgba(9,16,36,.82));border-radius:20px;padding:16px;box-shadow:0 18px 50px rgba(0,0,0,.2)}.metric-label{color:#9fb0d4;font-size:12px}.metric-value{font-size:26px;font-weight:900;margin-top:6px}.panel{border:1px solid rgba(56,189,248,.24);background:rgba(4,10,24,.72);border-radius:22px;overflow:hidden}.panel-head{display:flex;justify-content:space-between;gap:12px;align-items:center;padding:15px 18px;border-bottom:1px solid rgba(148,163,184,.16);background:rgba(15,23,42,.65)}.panel-title{font-size:15px;font-weight:900}.status{font-size:12px;border:1px solid rgba(148,163,184,.24);border-radius:999px;padding:5px 9px;color:#b7c6e6}.status.ok{color:#bbf7d0;border-color:rgba(34,197,94,.45);background:rgba(22,163,74,.12)}.status.warn{color:#fde68a;border-color:rgba(245,158,11,.45);background:rgba(245,158,11,.12)}.status.err{color:#fecaca;border-color:rgba(248,113,113,.45);background:rgba(239,68,68,.12)}.toolbar{display:flex;gap:10px;align-items:center;flex-wrap:wrap}.input{background:rgba(15,23,42,.8);border:1px solid rgba(148,163,184,.2);border-radius:12px;color:#eaf4ff;padding:10px 12px;min-width:240px}.table-wrap{overflow:auto}.table{width:100%;border-collapse:collapse;min-width:820px}.table th{text-align:left;color:#7dd3fc;text-transform:uppercase;letter-spacing:.14em;font-size:11px;background:rgba(15,23,42,.55)}.table th,.table td{padding:13px 14px;border-bottom:1px solid rgba(148,163,184,.12);vertical-align:top}.table td{font-size:13px;color:#dce7ff}.mono{font-family:"SFMono-Regular",Consolas,monospace;font-size:12px;color:#a7f3d0}.json{max-height:420px;overflow:auto;white-space:pre-wrap;font-family:"SFMono-Regular",Consolas,monospace;font-size:12px;color:#c7d2fe;background:rgba(2,6,23,.6);border-radius:16px;padding:14px}.empty{padding:32px;color:#a8b7d8;text-align:center}.toast{position:fixed;right:24px;bottom:24px;background:#0f172a;border:1px solid rgba(125,211,252,.35);border-radius:14px;padding:12px 16px;box-shadow:0 18px 40px rgba(0,0,0,.35);z-index:50}.pill{display:inline-flex;align-items:center;gap:6px;border-radius:999px;padding:4px 8px;font-size:11px;border:1px solid rgba(148,163,184,.22);color:#b7c6e6}.pill.ok{color:#bbf7d0;border-color:rgba(34,197,94,.38)}.pill.err{color:#fecaca;border-color:rgba(248,113,113,.4)}.muted{color:#8ea2c9}.small{font-size:12px}.inline-actions{display:flex;gap:8px;flex-wrap:wrap}@media(max-width:980px){.grid{grid-template-columns:1fr}.side{position:static}.cards{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:640px){.cards{grid-template-columns:1fr}.title{font-size:28px}}
  `;
  const iconById = { 'docker-desktop':'🐳','openlens-kubernetes':'☸️','task-management':'✅','observability':'📈','cicd-gitops':'🚀','terraform-studio':'🧱','devsecops':'🛡️','ai-ops':'🤖','finops-cost':'💶','infra-hub':'🏗️','release-center':'🚢','policy-center':'⚖️','notification-center':'🔔','identity-admin':'👥' };
  function token(){ try { return window.__NEBULAOPS_ACCESS_TOKEN__ || localStorage.getItem(TOKEN_KEY) || ''; } catch(_) { return window.__NEBULAOPS_ACCESS_TOKEN__ || ''; } }
  function esc(value){ return String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch])); }
  function getPath(obj, path){ if(!path) return obj; return String(path).split('.').reduce((acc,key)=> acc && acc[key] !== undefined ? acc[key] : undefined, obj); }
  function asArray(payload, path){
    const selected = getPath(payload, path);
    const val = selected !== undefined ? selected : payload;
    if(Array.isArray(val)) return val;
    if(val && Array.isArray(val.items)) return val.items;
    if(val && val.data && Array.isArray(val.data.items)) return val.data.items;
    if(val && val.data && Array.isArray(val.data)) return val.data;
    if(val && val.body && val.body.data && Array.isArray(val.body.data.result)) return val.body.data.result;
    if(val && val.body && Array.isArray(val.body.items)) return val.body.items;
    if(val && typeof val === 'object') return Object.entries(val).filter(([k]) => !['toolStatus','data','body','live','url','statusCode','durationMs','executedAt'].includes(k)).map(([key,value]) => ({ key, value }));
    return [];
  }
  function liveFlag(payload, ok){ if(!ok) return false; if(payload && typeof payload === 'object' && payload.live === false) return false; if(payload && payload.error) return false; return true; }
  function human(v){ if(v === null || v === undefined || v === '') return '—'; if(typeof v === 'object') return JSON.stringify(v); return String(v); }
  function flattenItem(item){
    if(!item || typeof item !== 'object') return { value: item };
    const meta = item.metadata || {}; const status = item.status || {}; const spec = item.spec || {};
    const firstContainer = Array.isArray(spec.containers) ? spec.containers[0] : (spec.template && spec.template.spec && Array.isArray(spec.template.spec.containers) ? spec.template.spec.containers[0] : {});
    return { id: item.id || item.Id || meta.uid || item.name || meta.name || item.key || item.repository || item.image || '', name: item.name || item.Name || meta.name || item.key || item.repository || item.title || item.id || '', namespace: item.namespace || meta.namespace || '', kind: item.kind || item.Kind || '', status: item.status || item.State || status.phase || status.status || item.phase || item.health || item.severity || '', image: item.image || item.Image || (firstContainer && firstContainer.image) || '', ports: item.ports || item.Ports || '', created: item.created || item.createdAt || meta.creationTimestamp || item.updatedAt || '', raw: item };
  }
  class LiveRemote extends HTMLElement {
    constructor(){ super(); this.attachShadow({mode:'open'}); this.state={active:0, filter:'', loading:false, endpoints:[], toast:'', realm:'nebulaops', dragTaskId:''}; }
    connectedCallback(){ this.render(); this.refresh(); }
    toast(msg){ this.state.toast = msg; this.render(); setTimeout(()=>{ this.state.toast=''; this.render(); }, 2800); }
    async request(url, opts){
      await (window.__NEBULAOPS_AUTH_READY__ && window.__NEBULAOPS_AUTH_READY__.catch ? window.__NEBULAOPS_AUTH_READY__.catch(()=>undefined) : Promise.resolve());
      const h = new Headers((opts && opts.headers) || {}); if(!h.has('Content-Type') && opts && opts.body) h.set('Content-Type','application/json');
      const t = token(); if(t && !h.has('Authorization')) h.set('Authorization','Bearer '+t);
      h.set('X-NebulaOps-MFE', CFG.id); h.set('X-NebulaOps-Live-Only', VERSION);
      const res = await fetch(url, {...(opts||{}), headers:h}); const text = await res.text(); let body = text;
      try { body = text ? JSON.parse(text) : {}; } catch(_) {}
      if(!res.ok) throw {status:res.status, body}; return body;
    }
    async refresh(){
      const endpoints = CFG.config.endpoints || []; this.state.loading = true; this.render(); const results = [];
      for(const ep of endpoints){ const started = performance.now(); try{ const requestUrl = CFG.id==='identity-admin' ? this.identityUrl((/group/i.test(ep.label||'')?'groups':/role/i.test(ep.label||'')?'roles':'users')) : ep.url; const body = await this.request(requestUrl, {method: ep.method || 'GET', body: ep.body ? JSON.stringify(ep.body) : undefined}); const rows = asArray(body, ep.itemsPath); results.push({...ep, ok:true, live:liveFlag(body,true), duration:Math.round(performance.now()-started), body, rows}); }catch(e){ results.push({...ep, ok:false, live:false, duration:Math.round(performance.now()-started), body:e.body || {error:e.message || String(e), status:e.status}, rows:[]}); } }
      this.state.endpoints = results; this.state.loading=false; this.render();
    }
    counts(){ const total=this.state.endpoints.reduce((a,e)=>a+(e.rows?e.rows.length:0),0); const live=this.state.endpoints.filter(e=>e.live).length; const errors=this.state.endpoints.filter(e=>!e.ok||!e.live).length; return {total,live,errors,endpoints:this.state.endpoints.length}; }
    activeEndpoint(){ return this.state.endpoints[this.state.active] || this.state.endpoints[0] || (CFG.config.endpoints||[])[0] || {}; }
    setActive(i){ this.state.active=i; this.render(); }
    setFilter(v){ this.state.filter=v.toLowerCase(); this.render(); }
    filteredRows(ep){ const q=this.state.filter; const rows=ep.rows||[]; return q ? rows.filter(r=>JSON.stringify(r).toLowerCase().includes(q)) : rows; }
    async dockerAction(action, item){ const row=flattenItem(item); const id=encodeURIComponent(row.id || row.name); const map={start:['POST',`/api/runtime/docker/containers/${id}/start`],stop:['POST',`/api/runtime/docker/containers/${id}/stop`],restart:['POST',`/api/runtime/docker/containers/${id}/restart`],remove:['DELETE',`/api/runtime/docker/containers/${id}`]}; const spec=map[action]; if(!spec) return; try{ await this.request(spec[1],{method:spec[0]}); this.toast(`${action} executed for ${row.name || id}`); await this.refresh(); }catch(e){ this.toast(`${action} failed: ${human(e.body && (e.body.error || e.body.stderr) || e.message)}`); } }
    async pruneImages(){ try{ await this.request('/api/runtime/docker/images/prune',{method:'POST',body:JSON.stringify({})}); this.toast('Image prune requested'); await this.refresh(); }catch(e){ this.toast('Image prune failed'); } }
    async taskStatus(item,status){ const id=encodeURIComponent(item.id || item._id || item.key || ''); if(!id) return; try{ await this.request(`/api/tasks/${id}/status/${status}`,{method:'PATCH',body:JSON.stringify({})}); this.toast(`Task moved to ${status}`); await this.refresh(); }catch(e){ this.toast(`Task update failed: ${human(e.body && e.body.error || e.message)}`); } }
    async releaseAction(kind,item){ const id=encodeURIComponent(item.id || item.name || ''); if(!id) return; try{ await this.request(`/api/releases/${id}/${kind}`,{method:'POST',body:JSON.stringify({})}); this.toast(`Release ${kind} requested`); await this.refresh(); }catch(e){ this.toast(`Release ${kind} failed`); } }
    async evaluatePolicy(){ try{ const body=await this.request('/api/governance/decisions',{method:'POST',body:JSON.stringify({action:'runtime.evaluate',target:{name:'current-runtime'},payload:{requestedFrom:'policy-center'}})}); this.toast(`Governance decision: ${human(body.outcome || body.allow)}`); await this.refresh(); }catch(e){ this.toast('Governance evaluation failed'); } }
    async approvalAction(id,action){ if(!id) return; const comment=prompt(action==='approve'?'Approval comment':'Rejection comment',''); if(comment===null) return; try{ await this.request(`/api/governance/approvals/${encodeURIComponent(id)}/${action}`,{method:'POST',body:JSON.stringify({comment})}); this.toast(`Approval ${action} submitted`); await this.refresh(); }catch(e){ this.toast(`Approval ${action} failed`); } }
    async k8sAction(action,item){ const row=flattenItem(item); const ns=encodeURIComponent(row.namespace || 'default'); const name=encodeURIComponent(row.name || ''); if(!name) return; let url='', method='POST', body={}; if(action==='restartPod') url=`/api/kubernetes/pods/${ns}/${name}/restart`; if(action==='deletePod'){ url=`/api/kubernetes/pods/${ns}/${name}`; method='DELETE'; } if(action==='restartDeployment') url=`/api/kubernetes/deployments/${ns}/${name}/restart`; if(action==='scaleDeployment'){ const replicas=prompt('Replicas', String(item.replicas || item.spec?.replicas || 1)); if(replicas===null) return; url=`/api/kubernetes/deployments/${ns}/${name}/scale`; body={replicas:Number(replicas)}; } try{ await this.request(url,{method,body: method==='DELETE' ? undefined : JSON.stringify(body)}); this.toast(`${action} executed for ${row.name}`); await this.refresh(); }catch(e){ this.toast(`${action} failed: ${human(e.body && (e.body.stderr || e.body.error) || e.message)}`); } }
    actionButtons(ep,item){ const id=CFG.id; const row=flattenItem(item); const kind=(row.kind || ep.kind || '').toLowerCase(); if(id==='docker-desktop' && /container/.test(ep.label.toLowerCase())) return `<div class="inline-actions"><button class="btn" data-act="docker:start" data-key="${esc(row.id)}">Start</button><button class="btn" data-act="docker:stop" data-key="${esc(row.id)}">Stop</button><button class="btn" data-act="docker:restart" data-key="${esc(row.id)}">Restart</button><button class="btn danger" data-act="docker:remove" data-key="${esc(row.id)}">Remove</button></div>`; if(id==='task-management') return `<div class="inline-actions"><button class="btn" data-act="task:IN_PROGRESS" data-key="${esc(row.id)}">Start</button><button class="btn" data-act="task:REVIEW" data-key="${esc(row.id)}">Test</button><button class="btn" data-act="task:DONE" data-key="${esc(row.id)}">Done</button></div>`; if(id==='release-center') return `<div class="inline-actions"><button class="btn" data-act="release:promote" data-key="${esc(row.id || row.name)}">Promote</button><button class="btn danger" data-act="release:rollback" data-key="${esc(row.id || row.name)}">Rollback</button></div>`; if(id==='policy-center' && /approval/i.test(ep.label||'') && String(item.status||'').toUpperCase()==='PENDING') return `<div class="inline-actions"><button class="btn" data-approval-act="approve" data-approval-id="${esc(item.id || row.id)}">Approve</button><button class="btn danger" data-approval-act="reject" data-approval-id="${esc(item.id || row.id)}">Reject</button></div>`; if(id==='openlens-kubernetes' && kind.includes('pod')) return `<div class="inline-actions"><button class="btn" data-act="k8s:restartPod" data-key="${esc(row.id || row.name)}">Restart</button><button class="btn danger" data-act="k8s:deletePod" data-key="${esc(row.id || row.name)}">Delete</button></div>`; if(id==='openlens-kubernetes' && kind.includes('deployment')) return `<div class="inline-actions"><button class="btn" data-act="k8s:scaleDeployment" data-key="${esc(row.id || row.name)}">Scale</button><button class="btn" data-act="k8s:restartDeployment" data-key="${esc(row.id || row.name)}">Restart</button></div>`; return ''; }

    taskEndpoint(){ return (this.state.endpoints || []).find(e => /task/i.test(e.kind || e.label || '') || String(e.url || '').includes('/api/tasks')) || {rows:[]}; }
    taskColumns(){ return [
      ['TODO','To start'], ['IN_PROGRESS','In progress'], ['REVIEW','To test'], ['DONE','Done']
    ]; }
    allTasks(){ return (this.taskEndpoint().rows || []).filter(Boolean); }
    async createTask(){
      const root=this.shadowRoot; const title=root.getElementById('taskTitle')?.value?.trim(); if(!title){ this.toast('Task title is required'); return; }
      const body={ organizationId:'default-org', projectId:root.getElementById('taskProject')?.value?.trim() || 'portfolio', title, description:root.getElementById('taskDescription')?.value?.trim() || '', status:root.getElementById('taskStatus')?.value || 'TODO', priority:root.getElementById('taskPriority')?.value || 'MEDIUM', assigneeId:root.getElementById('taskAssignee')?.value?.trim() || 'unassigned', labels:(root.getElementById('taskLabels')?.value || '').split(',').map(x=>x.trim()).filter(Boolean) };
      try{ await this.request('/api/tasks',{method:'POST',body:JSON.stringify(body)}); this.toast('Task created and notification event published'); await this.refresh(); }
      catch(e){ this.toast(`Task creation failed: ${human(e.body && e.body.error || e.message)}`); }
    }
    async deleteTask(id){ if(!id || !confirm('Remove this task?')) return; try{ await this.request(`/api/tasks/${encodeURIComponent(id)}`,{method:'DELETE'}); this.toast('Task removed'); await this.refresh(); }catch(e){ this.toast('Task remove failed'); } }
    async moveTask(id,status,sortOrder){ if(!id || !status) return; try{ await this.request(`/api/tasks/${encodeURIComponent(id)}/move`,{method:'PATCH',body:JSON.stringify({status, sortOrder: sortOrder || Date.now()})}); this.toast(`Task moved to ${status}`); await this.refresh(); }catch(e){ this.toast(`Task move failed: ${human(e.body && e.body.error || e.message)}`); } }
    taskCard(task){ const priority=task.priority || 'MEDIUM'; const assignee=task.assigneeId || 'unassigned'; const labels=Array.isArray(task.labels)?task.labels:[]; return `<article class="task-card" draggable="true" data-drag-task="${esc(task.id)}"><div class="task-title">${esc(task.title || task.name || 'Untitled task')}</div><div class="muted small">${esc(task.description || '')}</div><div class="task-meta"><span class="pill">${esc(priority)}</span><span class="pill">${esc(assignee)}</span></div>${labels.length?`<div class="task-labels">${labels.map(l=>`<span>${esc(l)}</span>`).join('')}</div>`:''}<div class="inline-actions"><button class="btn" data-task-move="IN_PROGRESS" data-task-id="${esc(task.id)}">Start</button><button class="btn" data-task-move="REVIEW" data-task-id="${esc(task.id)}">Test</button><button class="btn" data-task-move="DONE" data-task-id="${esc(task.id)}">Done</button><button class="btn danger" data-task-delete="${esc(task.id)}">Remove</button></div></article>`; }
    taskBoard(){ const tasks=this.allTasks(); const cols=this.taskColumns().map(([status,label])=>{ const items=tasks.filter(t=>(t.status||'TODO')===status).sort((a,b)=>(a.sortOrder||0)-(b.sortOrder||0)); return `<section class="kanban-col" data-drop-status="${status}"><div class="kanban-head"><strong>${label}</strong><span class="count">${items.length}</span></div><div class="kanban-dropzone">${items.length?items.map(t=>this.taskCard(t)).join(''):'<div class="empty small">Drop tasks here</div>'}</div></section>`; }).join(''); return `<div class="task-board"><div class="panel task-create"><div class="panel-head"><div><div class="panel-title">Create task</div><div class="muted small">The assignee receives a notification through RabbitMQ and the notification service.</div></div></div><div class="form-grid"><input id="taskTitle" class="input" placeholder="Title"><input id="taskAssignee" class="input" placeholder="Assignee username or email"><input id="taskProject" class="input" placeholder="Project" value="portfolio"><select id="taskStatus" class="input"><option value="TODO">To start</option><option value="IN_PROGRESS">In progress</option><option value="REVIEW">To test</option><option value="DONE">Done</option></select><select id="taskPriority" class="input"><option>LOW</option><option selected>MEDIUM</option><option>HIGH</option><option>CRITICAL</option></select><input id="taskLabels" class="input" placeholder="Labels, comma separated"><textarea id="taskDescription" class="input" placeholder="Description"></textarea><button class="btn primary" id="taskCreate">Add task</button></div></div><div class="kanban">${cols}</div></div>`; }
    identityEndpointKind(){ const ep=this.activeEndpoint(); const label=String(ep.label || '').toLowerCase(); if(label.includes('group')) return 'groups'; if(label.includes('role')) return 'roles'; return 'users'; }
    identityUrl(kind, id, action){ const realm=encodeURIComponent(this.state.realm || 'nebulaops'); let base=`/api/identity/realms/${realm}/${kind}`; if(id) base += `/${encodeURIComponent(id)}`; if(action) base += `/${action}`; return base; }
    async identityCreate(){ const kind=this.identityEndpointKind(); const root=this.shadowRoot; let body={}; if(kind==='users'){ const username=root.getElementById('identityUsername')?.value?.trim(); if(!username){ this.toast('Username is required'); return; } body={username,email:root.getElementById('identityEmail')?.value?.trim() || username,firstName:root.getElementById('identityFirstName')?.value?.trim() || '',lastName:root.getElementById('identityLastName')?.value?.trim() || '',enabled:true}; } else if(kind==='groups'){ const name=root.getElementById('identityName')?.value?.trim(); if(!name){ this.toast('Group name is required'); return; } body={name}; } else { const name=root.getElementById('identityName')?.value?.trim(); if(!name){ this.toast('Role name is required'); return; } body={name,description:root.getElementById('identityDescription')?.value?.trim() || ''}; }
      try{ await this.request(this.identityUrl(kind),{method:'POST',body:JSON.stringify(body)}); this.toast(`${kind.slice(0,-1)} created`); await this.refresh(); }catch(e){ this.toast(`Identity create failed: ${human(e.body && e.body.error || e.message)}`); }
    }
    async identityDisable(kind,id){ if(!id || !confirm(`Disable ${kind.slice(0,-1)} ${id}?`)) return; try{ await this.request(this.identityUrl(kind,id,'disable'),{method:'PATCH',body:JSON.stringify({})}); this.toast(`${kind.slice(0,-1)} disabled`); await this.refresh(); }catch(e){ this.toast('Disable failed'); } }
    async identityEdit(kind,item){ const id=item.id || item.name; if(!id) return; let body={...item}; if(kind==='users'){ const email=prompt('Email', item.email || ''); if(email===null) return; body.email=email; body.firstName=prompt('First name', item.firstName || '') || ''; body.lastName=prompt('Last name', item.lastName || '') || ''; body.enabled=item.enabled !== false; } else if(kind==='groups'){ const name=prompt('Group name', item.name || ''); if(name===null) return; body.name=name; } else { const description=prompt('Role description', item.description || ''); if(description===null) return; body.description=description; body.name=item.name; }
      try{ await this.request(this.identityUrl(kind,id),{method:'PUT',body:JSON.stringify(body)}); this.toast(`${kind.slice(0,-1)} updated`); await this.refresh(); }catch(e){ this.toast('Update failed'); } }
    identityConsole(){ const ep=this.activeEndpoint(); const kind=this.identityEndpointKind(); const rows=this.filteredRows(ep); const createForm=kind==='users'?`<input id="identityUsername" class="input" placeholder="Username"><input id="identityEmail" class="input" placeholder="Email"><input id="identityFirstName" class="input" placeholder="First name"><input id="identityLastName" class="input" placeholder="Last name">`:`<input id="identityName" class="input" placeholder="${kind==='groups'?'Group name':'Role name'}">${kind==='roles'?'<input id="identityDescription" class="input" placeholder="Description">':''}`; const table=rows.length?`<div class="table-wrap"><table class="table"><thead><tr><th>ID</th><th>Name</th><th>Email / Description</th><th>Status</th><th>Actions</th></tr></thead><tbody>${rows.map((r,i)=>{ const id=r.id || r.name || i; const name=r.username || r.name || r.firstName || id; const detail=r.email || r.description || (r.path || '—'); const disabled=(r.enabled===false) || (r.attributes && String(r.attributes.disabled).includes('true')); return `<tr><td class="mono">${esc(id)}</td><td><strong>${esc(name)}</strong><div class="muted small">${esc(r.firstName || '')} ${esc(r.lastName || '')}</div></td><td>${esc(detail)}</td><td><span class="pill ${disabled?'err':'ok'}">${disabled?'disabled':'enabled'}</span></td><td><div class="inline-actions"><button class="btn" data-identity-edit="${esc(kind)}" data-identity-id="${esc(id)}">Edit</button><button class="btn danger" data-identity-disable="${esc(kind)}" data-identity-id="${esc(id)}">Disable</button></div></td></tr>`; }).join('')}</tbody></table></div>`:`<div class="empty">No ${kind} returned by Keycloak for this realm.</div>`; return `<div class="identity-console"><div class="panel"><div class="panel-head"><div><div class="panel-title">Keycloak realm identity administration</div><div class="muted small">Lists are cached in Redis for 45 seconds and invalidated after mutations.</div></div><div class="toolbar"><input id="identityRealm" class="input" value="${esc(this.state.realm || 'nebulaops')}" placeholder="Realm"><button class="btn" id="identityRealmLoad">Load realm</button></div></div><div class="form-grid identity-form">${createForm}<button id="identityCreate" class="btn primary">Add ${kind.slice(0,-1)}</button></div></div><div class="grid"><aside class="side"><h3>Keycloak resources</h3>${(this.state.endpoints.length?this.state.endpoints:CFG.config.endpoints||[]).map((e,i)=>`<button class="navbtn ${i===this.state.active?'active':''}" data-nav="${i}"><span>${esc(e.label)}</span><span class="count">${(e.rows||[]).length||0}</span></button>`).join('')}</aside><main class="content"><div class="panel"><div class="panel-head"><div><div class="panel-title">${esc(ep.label || kind)}</div><div class="muted small mono">${esc(this.identityUrl(kind))}</div></div><div class="toolbar"><input class="input" id="filter" placeholder="Filter..." value="${esc(this.state.filter)}"><span class="status ${ep.ok?'ok':'warn'}">${ep.ok?'LIVE':'PENDING'}</span></div></div>${table}<div style="padding:16px"><details><summary class="muted small">Raw endpoint response</summary><pre class="json">${esc(JSON.stringify(ep.body||{},null,2))}</pre></details></div></div></main></div></div>`; }

    summaryCards(){ const c=this.counts(); return `<div class="cards"><div class="card"><div class="metric-label">Endpoint checks</div><div class="metric-value">${c.endpoints}</div></div><div class="card"><div class="metric-label">Live sources</div><div class="metric-value">${c.live}</div></div><div class="card"><div class="metric-label">Records returned</div><div class="metric-value">${c.total}</div></div><div class="card"><div class="metric-label">Unavailable sources</div><div class="metric-value">${c.errors}</div></div></div>`; }
    rowHtml(ep,item,idx){ const row=flattenItem(item); const status=row.status || item.severity || item.status || item.phase || ''; const pillClass=/running|active|ok|pass|healthy|available|success|done|applied/i.test(status)?'ok':(/fail|error|down|blocked|critical|unavailable/i.test(status)?'err':''); return `<tr><td class="mono">${esc(row.id || idx+1)}</td><td><strong>${esc(row.name || item.title || item.message || row.kind || 'record')}</strong><div class="muted small">${esc(row.namespace || row.kind || ep.label)}</div></td><td>${esc(row.image || item.repository || item.source || item.tool || '—')}</td><td><span class="pill ${pillClass}">${esc(status || '—')}</span></td><td>${esc(row.ports || row.created || item.createdAt || item.updatedAt || '—')}</td><td>${this.actionButtons(ep,item)}</td></tr>`; }

    observabilityConsole(){ const endpoints=this.state.endpoints.length ? this.state.endpoints : (CFG.config.endpoints||[]).map(e=>({...e,rows:[],ok:false,live:false,body:null})); const nav=endpoints.map((e,i)=>`<button class="navbtn ${i===this.state.active?'active':''}" data-nav="${i}"><span>${esc(e.label)}</span><span class="count">${e.rows?e.rows.length:0}</span></button>`).join(''); const ep=this.activeEndpoint(); const rows=this.filteredRows(ep); const serviceRows=(endpoints.find(e=>/service health/i.test(e.label))?.rows)||[]; const liveCount=serviceRows.filter(r=>r.live===true || Number(r.status)>0 && Number(r.status)<500).length; const table=rows.length ? `<div class="table-wrap"><table class="table"><thead><tr><th>ID</th><th>Name</th><th>Source</th><th>Status</th><th>Details</th><th>Actions</th></tr></thead><tbody>${rows.map((r,i)=>this.rowHtml(ep,r,i)).join('')}</tbody></table></div>` : `<div class="empty">No records returned by this live source.<br><span class="small">No mock or seeded records are rendered here.</span></div>`; return `<div class="observability-console"><div class="panel" style="margin-bottom:18px"><div class="panel-head"><div><div class="panel-title">Runtime data policy</div><div class="muted small">This console renders only responses returned by NebulaOps APIs, Prometheus, Loki, Tempo, RabbitMQ and audit-service. Empty states mean the source returned no rows or is unavailable.</div></div><span class="status ${liveCount?'ok':'warn'}">${liveCount} services live</span></div></div><div class="grid"><aside class="side"><h3>Live sources</h3>${nav}</aside><main class="content"><div class="panel"><div class="panel-head"><div><div class="panel-title">${esc(ep.label || 'Live source')}</div><div class="muted small mono">${esc(ep.url || '')}</div></div><div class="toolbar"><input class="input" id="filter" placeholder="Filter live data..." value="${esc(this.state.filter)}"><span class="status ${ep.ok?'ok':'warn'}">${ep.ok?'LIVE':'UNAVAILABLE'}</span></div></div>${table}<div style="padding:16px"><details><summary class="muted small">Raw live response</summary><pre class="json">${esc(JSON.stringify(ep.body||{},null,2))}</pre></details></div></div></main></div></div>`; }
    endpointPanel(){ const ep=this.activeEndpoint(); const rows=this.filteredRows(ep); const statusClass=ep.ok && ep.live ? 'ok' : ep.ok ? 'warn' : 'err'; const raw=ep.body ? esc(JSON.stringify(ep.body,null,2)) : ''; const table=rows.length ? `<div class="table-wrap"><table class="table"><thead><tr><th>ID</th><th>Name</th><th>Source/Image</th><th>Status</th><th>Details</th><th>Actions</th></tr></thead><tbody>${rows.map((r,i)=>this.rowHtml(ep,r,i)).join('')}</tbody></table></div>` : `<div class="empty">No records returned by this live endpoint.<br><span class="small">Endpoint: <span class="mono">${esc(ep.url || '')}</span></span></div>`; return `<div class="panel"><div class="panel-head"><div><div class="panel-title">${esc(ep.label || 'Endpoint')}</div><div class="muted small mono">${esc(ep.url || '')}</div></div><div class="toolbar"><input class="input" id="filter" placeholder="Filter current endpoint..." value="${esc(this.state.filter)}"><span class="status ${statusClass}">${ep.ok ? (ep.live ? 'LIVE' : 'NO LIVE DATA') : 'ERROR'} · ${ep.duration ?? 0}ms</span></div></div>${table}<div style="padding:16px"><details><summary class="muted small">Raw endpoint response</summary><pre class="json">${raw}</pre></details></div></div>`; }
    render(){ const endpoints=this.state.endpoints.length ? this.state.endpoints : (CFG.config.endpoints || []).map(e=>({...e, rows:[], ok:false, live:false, body:null})); const nav=endpoints.map((e,i)=>`<button class="navbtn ${i===this.state.active?'active':''}" data-nav="${i}"><span>${esc(e.label)}</span><span class="count">${e.rows?e.rows.length:0}</span></button>`).join(''); const extra=CFG.id==='docker-desktop' ? '<button class="btn" id="prune">Prune images</button>' : CFG.id==='policy-center' ? '<button class="btn" id="evalPolicy">Evaluate current runtime</button>' : ''; const specialContent=CFG.id==='task-management'?this.taskBoard():(CFG.id==='identity-admin'?this.identityConsole():(CFG.id==='observability'?this.observabilityConsole():null)); this.shadowRoot.innerHTML = `<style>${css}.kanban{display:grid;grid-template-columns:repeat(4,minmax(220px,1fr));gap:14px}.kanban-col{border:1px solid rgba(148,163,184,.18);background:rgba(8,15,34,.72);border-radius:20px;padding:12px;min-height:360px}.kanban-col.drag-over{border-color:rgba(56,189,248,.8);box-shadow:0 0 0 3px rgba(56,189,248,.12)}.kanban-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:10px}.task-card{border:1px solid rgba(148,163,184,.2);background:linear-gradient(180deg,rgba(16,26,55,.92),rgba(9,16,36,.9));border-radius:16px;padding:12px;margin-bottom:10px;cursor:grab}.task-card:active{cursor:grabbing}.task-title{font-weight:900;margin-bottom:6px}.task-meta,.task-labels{display:flex;gap:6px;flex-wrap:wrap;margin:8px 0}.task-labels span{font-size:11px;color:#bfdbfe;background:rgba(59,130,246,.14);border:1px solid rgba(59,130,246,.24);padding:3px 7px;border-radius:999px}.form-grid{display:grid;grid-template-columns:repeat(4,minmax(160px,1fr));gap:10px;padding:16px}.form-grid textarea{grid-column:span 3;min-height:44px;resize:vertical}.task-create{margin-bottom:18px}.identity-form{grid-template-columns:repeat(5,minmax(150px,1fr))}@media(max-width:1100px){.kanban{grid-template-columns:repeat(2,minmax(0,1fr))}.form-grid,.identity-form{grid-template-columns:1fr}.form-grid textarea{grid-column:auto}}@media(max-width:700px){.kanban{grid-template-columns:1fr}}</style><section class="mfe"><div class="hero"><div class="hero-left"><div class="icon">${iconById[CFG.id] || '◆'}</div><div><div class="eyebrow">${esc(CFG.scope)}</div><h1 class="title">${esc(CFG.title)}</h1><div class="subtitle">Live-only remote module. The view is populated from NebulaOps gateway and service endpoints; empty states mean the live source returned no records.</div></div></div><div class="actions"><button class="btn primary" id="refresh">${this.state.loading?'Loading...':'Refresh live data'}</button>${extra}<a class="btn" href="/remoteEntry.js?v=${VERSION}" target="_blank" rel="noreferrer">remoteEntry.js</a></div></div>${this.summaryCards()}${specialContent || `<div class="grid"><aside class="side"><h3>Live endpoints</h3>${nav}</aside><main class="content">${this.endpointPanel()}</main></div>`}${this.state.toast?`<div class="toast">${esc(this.state.toast)}</div>`:''}</section>`; this.shadowRoot.getElementById('refresh')?.addEventListener('click',()=>this.refresh()); this.shadowRoot.getElementById('prune')?.addEventListener('click',()=>this.pruneImages()); this.shadowRoot.getElementById('evalPolicy')?.addEventListener('click',()=>this.evaluatePolicy()); this.shadowRoot.querySelectorAll('[data-nav]').forEach(b=>b.addEventListener('click',()=>this.setActive(Number(b.getAttribute('data-nav'))))); this.shadowRoot.getElementById('filter')?.addEventListener('input',e=>this.setFilter(e.target.value)); this.shadowRoot.querySelectorAll('[data-act]').forEach(btn=>btn.addEventListener('click',()=>{ const [area,act]=btn.getAttribute('data-act').split(':'); const key=btn.getAttribute('data-key'); const ep=this.activeEndpoint(); const item=(ep.rows||[]).find(r=>String(flattenItem(r).id||flattenItem(r).name)===String(key)); if(area==='docker') this.dockerAction(act,item||{}); if(area==='task') this.taskStatus(item||{},act); if(area==='release') this.releaseAction(act,item||{}); if(area==='k8s') this.k8sAction(act,item||{}); })); this.shadowRoot.querySelectorAll('[data-approval-act]').forEach(btn=>btn.addEventListener('click',()=>this.approvalAction(btn.getAttribute('data-approval-id'), btn.getAttribute('data-approval-act')))); this.shadowRoot.getElementById('taskCreate')?.addEventListener('click',()=>this.createTask()); this.shadowRoot.querySelectorAll('[data-task-delete]').forEach(b=>b.addEventListener('click',()=>this.deleteTask(b.getAttribute('data-task-delete')))); this.shadowRoot.querySelectorAll('[data-task-move]').forEach(b=>b.addEventListener('click',()=>this.moveTask(b.getAttribute('data-task-id'), b.getAttribute('data-task-move')))); this.shadowRoot.querySelectorAll('[data-drag-task]').forEach(card=>{ card.addEventListener('dragstart',e=>{ this.state.dragTaskId=card.getAttribute('data-drag-task') || ''; e.dataTransfer?.setData('text/plain', this.state.dragTaskId); }); }); this.shadowRoot.querySelectorAll('[data-drop-status]').forEach(col=>{ col.addEventListener('dragover',e=>{ e.preventDefault(); col.classList.add('drag-over'); }); col.addEventListener('dragleave',()=>col.classList.remove('drag-over')); col.addEventListener('drop',e=>{ e.preventDefault(); col.classList.remove('drag-over'); const id=e.dataTransfer?.getData('text/plain') || this.state.dragTaskId; this.moveTask(id, col.getAttribute('data-drop-status')); }); }); this.shadowRoot.getElementById('identityCreate')?.addEventListener('click',()=>this.identityCreate()); this.shadowRoot.getElementById('identityRealmLoad')?.addEventListener('click',()=>{ this.state.realm=this.shadowRoot.getElementById('identityRealm')?.value?.trim() || 'nebulaops'; this.refresh(); }); this.shadowRoot.querySelectorAll('[data-identity-disable]').forEach(b=>b.addEventListener('click',()=>this.identityDisable(b.getAttribute('data-identity-disable'), b.getAttribute('data-identity-id')))); this.shadowRoot.querySelectorAll('[data-identity-edit]').forEach(b=>b.addEventListener('click',()=>{ const kind=b.getAttribute('data-identity-edit'); const id=b.getAttribute('data-identity-id'); const ep=this.activeEndpoint(); const item=(ep.rows||[]).find(r=>String(r.id||r.name)===String(id)); this.identityEdit(kind,item||{}); })); }
  }
  if(!customElements.get(CFG.tag)) customElements.define(CFG.tag, LiveRemote);
})();
