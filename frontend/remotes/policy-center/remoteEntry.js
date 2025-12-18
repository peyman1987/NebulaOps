/* NebulaOps v22.3 auth bridge
 * Makes shell-loaded and standalone MFE requests share a valid Bearer token.
 * - Shell origin: reuses localStorage token or bootstraps dev admin token.
 * - Standalone MFE route (/remotes/<mfe>/): uses the same nebulaops.localhost origin and the shared shell token.
 * - Patches fetch/XMLHttpRequest so the first API request waits for token availability.
 * - Rewrites legacy relative runtime endpoints to the gateway API path.
 */
(function nebulaopsAuthBridge() {
  'use strict';
  var VERSION = 'v22.3.8-dual-jwt-auth-bridge';
  var JWT_KEY = 'nebulaops.v22_3.jwt';
  var USER_KEY = 'nebulaops.v22_3.user';
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


/* NebulaOps v22.3.6-live-real-data · live endpoint custom element.
   This bundle does not contain seeded records. It renders only responses returned by NebulaOps APIs. */
(function nebulaopsLiveRemote(){
  'use strict';
  const CFG = {
  "tag": "nebulaops-mfe-policy-center",
  "title": "Policy Center",
  "scope": "Governance · Policy Gates",
  "port": 4222,
  "id": "policy-center",
  "config": {
    "endpoints": [
      {
        "label": "Policies",
        "url": "/api/policies",
        "kind": "policy"
      },
      {
        "label": "Evaluations",
        "url": "/api/policies/evaluations",
        "kind": "evaluation",
        "itemsPath": "items"
      },
      {
        "label": "CVEs",
        "url": "/api/platform/devsecops",
        "kind": "cve",
        "itemsPath": "cves"
      },
      {
        "label": "Cost forecast",
        "url": "/api/cost/forecast",
        "kind": "forecast"
      }
    ]
  }
};
  const VERSION = 'v22.3.6-live-real-data';
  const TOKEN_KEY = 'nebulaops.v22_3.jwt';
  const css = `
    :host{display:block;color:#eaf4ff;font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;}
    .mfe{min-height:640px;padding:28px;background:radial-gradient(circle at top left,rgba(25,179,255,.18),transparent 36%),linear-gradient(135deg,#07111f,#0b1026 58%,#11152d);}
    .hero{display:flex;gap:18px;align-items:center;justify-content:space-between;flex-wrap:wrap;margin-bottom:22px}.hero-left{display:flex;gap:16px;align-items:center;min-width:280px}.icon{width:72px;height:72px;border-radius:22px;display:grid;place-items:center;background:linear-gradient(135deg,#00c8ff,#8b5cf6);box-shadow:0 18px 40px rgba(0,0,0,.3);font-size:34px}.eyebrow{letter-spacing:.22em;text-transform:uppercase;color:#6ee7ff;font-size:12px;font-weight:800}.title{margin:4px 0 6px;font-size:34px;line-height:1;font-weight:900}.subtitle{color:#a8b7d8;font-size:15px;max-width:780px}.actions{display:flex;gap:10px;flex-wrap:wrap}.btn{border:1px solid rgba(125,211,252,.28);background:rgba(14,24,48,.82);color:#eaf4ff;border-radius:12px;padding:10px 14px;font-weight:800;cursor:pointer;text-decoration:none}.btn.primary{background:linear-gradient(135deg,#0ea5e9,#7c3aed);border:0}.btn.danger{border-color:rgba(248,113,113,.45);color:#fecaca}.grid{display:grid;grid-template-columns:280px 1fr;gap:18px}.side{border:1px solid rgba(148,163,184,.18);background:rgba(8,15,34,.72);border-radius:22px;padding:14px;align-self:start;position:sticky;top:12px}.side h3{font-size:12px;text-transform:uppercase;letter-spacing:.18em;color:#78e0ff;margin:4px 8px 12px}.navbtn{width:100%;text-align:left;display:flex;justify-content:space-between;gap:10px;margin:6px 0;border:1px solid transparent;background:transparent;color:#b7c6e6;border-radius:14px;padding:11px 12px;cursor:pointer;font-weight:800}.navbtn.active,.navbtn:hover{background:rgba(14,165,233,.13);border-color:rgba(14,165,233,.35);color:#fff}.count{font-size:11px;padding:2px 8px;border-radius:999px;background:rgba(14,165,233,.18);border:1px solid rgba(14,165,233,.3)}.content{min-width:0}.cards{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin-bottom:18px}.card{border:1px solid rgba(148,163,184,.18);background:linear-gradient(180deg,rgba(16,26,55,.86),rgba(9,16,36,.82));border-radius:20px;padding:16px;box-shadow:0 18px 50px rgba(0,0,0,.2)}.metric-label{color:#9fb0d4;font-size:12px}.metric-value{font-size:26px;font-weight:900;margin-top:6px}.panel{border:1px solid rgba(56,189,248,.24);background:rgba(4,10,24,.72);border-radius:22px;overflow:hidden}.panel-head{display:flex;justify-content:space-between;gap:12px;align-items:center;padding:15px 18px;border-bottom:1px solid rgba(148,163,184,.16);background:rgba(15,23,42,.65)}.panel-title{font-size:15px;font-weight:900}.status{font-size:12px;border:1px solid rgba(148,163,184,.24);border-radius:999px;padding:5px 9px;color:#b7c6e6}.status.ok{color:#bbf7d0;border-color:rgba(34,197,94,.45);background:rgba(22,163,74,.12)}.status.warn{color:#fde68a;border-color:rgba(245,158,11,.45);background:rgba(245,158,11,.12)}.status.err{color:#fecaca;border-color:rgba(248,113,113,.45);background:rgba(239,68,68,.12)}.toolbar{display:flex;gap:10px;align-items:center;flex-wrap:wrap}.input{background:rgba(15,23,42,.8);border:1px solid rgba(148,163,184,.2);border-radius:12px;color:#eaf4ff;padding:10px 12px;min-width:240px}.table-wrap{overflow:auto}.table{width:100%;border-collapse:collapse;min-width:820px}.table th{text-align:left;color:#7dd3fc;text-transform:uppercase;letter-spacing:.14em;font-size:11px;background:rgba(15,23,42,.55)}.table th,.table td{padding:13px 14px;border-bottom:1px solid rgba(148,163,184,.12);vertical-align:top}.table td{font-size:13px;color:#dce7ff}.mono{font-family:"SFMono-Regular",Consolas,monospace;font-size:12px;color:#a7f3d0}.json{max-height:420px;overflow:auto;white-space:pre-wrap;font-family:"SFMono-Regular",Consolas,monospace;font-size:12px;color:#c7d2fe;background:rgba(2,6,23,.6);border-radius:16px;padding:14px}.empty{padding:32px;color:#a8b7d8;text-align:center}.toast{position:fixed;right:24px;bottom:24px;background:#0f172a;border:1px solid rgba(125,211,252,.35);border-radius:14px;padding:12px 16px;box-shadow:0 18px 40px rgba(0,0,0,.35);z-index:50}.pill{display:inline-flex;align-items:center;gap:6px;border-radius:999px;padding:4px 8px;font-size:11px;border:1px solid rgba(148,163,184,.22);color:#b7c6e6}.pill.ok{color:#bbf7d0;border-color:rgba(34,197,94,.38)}.pill.err{color:#fecaca;border-color:rgba(248,113,113,.4)}.muted{color:#8ea2c9}.small{font-size:12px}.inline-actions{display:flex;gap:8px;flex-wrap:wrap}@media(max-width:980px){.grid{grid-template-columns:1fr}.side{position:static}.cards{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:640px){.cards{grid-template-columns:1fr}.title{font-size:28px}}
  `;
  const iconById = { 'docker-desktop':'🐳','openlens-kubernetes':'☸️','task-management':'✅','observability':'📈','cicd-gitops':'🚀','terraform-studio':'🧱','devsecops':'🛡️','ai-ops':'🤖','finops-cost':'💶','infra-hub':'🏗️','release-center':'🚢','policy-center':'⚖️','notification-center':'🔔' };
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
    constructor(){ super(); this.attachShadow({mode:'open'}); this.state={active:0, filter:'', loading:false, endpoints:[], toast:''}; }
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
      for(const ep of endpoints){ const started = performance.now(); try{ const body = await this.request(ep.url, {method: ep.method || 'GET', body: ep.body ? JSON.stringify(ep.body) : undefined}); const rows = asArray(body, ep.itemsPath); results.push({...ep, ok:true, live:liveFlag(body,true), duration:Math.round(performance.now()-started), body, rows}); }catch(e){ results.push({...ep, ok:false, live:false, duration:Math.round(performance.now()-started), body:e.body || {error:e.message || String(e), status:e.status}, rows:[]}); } }
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
    async evaluatePolicy(){ try{ const body=await this.request('/api/policies/evaluate',{method:'POST',body:JSON.stringify({target:'current-runtime'})}); this.toast(`Policy evaluation: ${human(body.status || body.allowPromotion)}`); await this.refresh(); }catch(e){ this.toast('Policy evaluation failed'); } }
    async k8sAction(action,item){ const row=flattenItem(item); const ns=encodeURIComponent(row.namespace || 'default'); const name=encodeURIComponent(row.name || ''); if(!name) return; let url='', method='POST', body={}; if(action==='restartPod') url=`/api/kubernetes/pods/${ns}/${name}/restart`; if(action==='deletePod'){ url=`/api/kubernetes/pods/${ns}/${name}`; method='DELETE'; } if(action==='restartDeployment') url=`/api/kubernetes/deployments/${ns}/${name}/restart`; if(action==='scaleDeployment'){ const replicas=prompt('Replicas', String(item.replicas || item.spec?.replicas || 1)); if(replicas===null) return; url=`/api/kubernetes/deployments/${ns}/${name}/scale`; body={replicas:Number(replicas)}; } try{ await this.request(url,{method,body: method==='DELETE' ? undefined : JSON.stringify(body)}); this.toast(`${action} executed for ${row.name}`); await this.refresh(); }catch(e){ this.toast(`${action} failed: ${human(e.body && (e.body.stderr || e.body.error) || e.message)}`); } }
    actionButtons(ep,item){ const id=CFG.id; const row=flattenItem(item); const kind=(row.kind || ep.kind || '').toLowerCase(); if(id==='docker-desktop' && /container/.test(ep.label.toLowerCase())) return `<div class="inline-actions"><button class="btn" data-act="docker:start" data-key="${esc(row.id)}">Start</button><button class="btn" data-act="docker:stop" data-key="${esc(row.id)}">Stop</button><button class="btn" data-act="docker:restart" data-key="${esc(row.id)}">Restart</button><button class="btn danger" data-act="docker:remove" data-key="${esc(row.id)}">Remove</button></div>`; if(id==='task-management') return `<div class="inline-actions"><button class="btn" data-act="task:IN_PROGRESS" data-key="${esc(row.id)}">Start</button><button class="btn" data-act="task:DONE" data-key="${esc(row.id)}">Done</button><button class="btn danger" data-act="task:BLOCKED" data-key="${esc(row.id)}">Block</button></div>`; if(id==='release-center') return `<div class="inline-actions"><button class="btn" data-act="release:promote" data-key="${esc(row.id || row.name)}">Promote</button><button class="btn danger" data-act="release:rollback" data-key="${esc(row.id || row.name)}">Rollback</button></div>`; if(id==='openlens-kubernetes' && kind.includes('pod')) return `<div class="inline-actions"><button class="btn" data-act="k8s:restartPod" data-key="${esc(row.id || row.name)}">Restart</button><button class="btn danger" data-act="k8s:deletePod" data-key="${esc(row.id || row.name)}">Delete</button></div>`; if(id==='openlens-kubernetes' && kind.includes('deployment')) return `<div class="inline-actions"><button class="btn" data-act="k8s:scaleDeployment" data-key="${esc(row.id || row.name)}">Scale</button><button class="btn" data-act="k8s:restartDeployment" data-key="${esc(row.id || row.name)}">Restart</button></div>`; return ''; }
    summaryCards(){ const c=this.counts(); return `<div class="cards"><div class="card"><div class="metric-label">Endpoint checks</div><div class="metric-value">${c.endpoints}</div></div><div class="card"><div class="metric-label">Live sources</div><div class="metric-value">${c.live}</div></div><div class="card"><div class="metric-label">Records returned</div><div class="metric-value">${c.total}</div></div><div class="card"><div class="metric-label">Unavailable sources</div><div class="metric-value">${c.errors}</div></div></div>`; }
    rowHtml(ep,item,idx){ const row=flattenItem(item); const status=row.status || item.severity || item.status || item.phase || ''; const pillClass=/running|active|ok|pass|healthy|available|success|done|applied/i.test(status)?'ok':(/fail|error|down|blocked|critical|unavailable/i.test(status)?'err':''); return `<tr><td class="mono">${esc(row.id || idx+1)}</td><td><strong>${esc(row.name || item.title || item.message || row.kind || 'record')}</strong><div class="muted small">${esc(row.namespace || row.kind || ep.label)}</div></td><td>${esc(row.image || item.repository || item.source || item.tool || '—')}</td><td><span class="pill ${pillClass}">${esc(status || '—')}</span></td><td>${esc(row.ports || row.created || item.createdAt || item.updatedAt || '—')}</td><td>${this.actionButtons(ep,item)}</td></tr>`; }
    endpointPanel(){ const ep=this.activeEndpoint(); const rows=this.filteredRows(ep); const statusClass=ep.ok && ep.live ? 'ok' : ep.ok ? 'warn' : 'err'; const raw=ep.body ? esc(JSON.stringify(ep.body,null,2)) : ''; const table=rows.length ? `<div class="table-wrap"><table class="table"><thead><tr><th>ID</th><th>Name</th><th>Source/Image</th><th>Status</th><th>Details</th><th>Actions</th></tr></thead><tbody>${rows.map((r,i)=>this.rowHtml(ep,r,i)).join('')}</tbody></table></div>` : `<div class="empty">No records returned by this live endpoint.<br><span class="small">Endpoint: <span class="mono">${esc(ep.url || '')}</span></span></div>`; return `<div class="panel"><div class="panel-head"><div><div class="panel-title">${esc(ep.label || 'Endpoint')}</div><div class="muted small mono">${esc(ep.url || '')}</div></div><div class="toolbar"><input class="input" id="filter" placeholder="Filter current endpoint..." value="${esc(this.state.filter)}"><span class="status ${statusClass}">${ep.ok ? (ep.live ? 'LIVE' : 'NO LIVE DATA') : 'ERROR'} · ${ep.duration ?? 0}ms</span></div></div>${table}<div style="padding:16px"><details><summary class="muted small">Raw endpoint response</summary><pre class="json">${raw}</pre></details></div></div>`; }
    render(){ const endpoints=this.state.endpoints.length ? this.state.endpoints : (CFG.config.endpoints || []).map(e=>({...e, rows:[], ok:false, live:false, body:null})); const nav=endpoints.map((e,i)=>`<button class="navbtn ${i===this.state.active?'active':''}" data-nav="${i}"><span>${esc(e.label)}</span><span class="count">${e.rows?e.rows.length:0}</span></button>`).join(''); const extra=CFG.id==='docker-desktop' ? '<button class="btn" id="prune">Prune images</button>' : CFG.id==='policy-center' ? '<button class="btn" id="evalPolicy">Evaluate current runtime</button>' : ''; this.shadowRoot.innerHTML = `<style>${css}</style><section class="mfe"><div class="hero"><div class="hero-left"><div class="icon">${iconById[CFG.id] || '◆'}</div><div><div class="eyebrow">${esc(CFG.scope)}</div><h1 class="title">${esc(CFG.title)}</h1><div class="subtitle">Live-only remote module. The view is populated from NebulaOps gateway and service endpoints; empty states mean the live source returned no records.</div></div></div><div class="actions"><button class="btn primary" id="refresh">${this.state.loading?'Loading...':'Refresh live data'}</button>${extra}<a class="btn" href="/remoteEntry.js?v=${VERSION}" target="_blank" rel="noreferrer">remoteEntry.js</a></div></div>${this.summaryCards()}<div class="grid"><aside class="side"><h3>Live endpoints</h3>${nav}</aside><main class="content">${this.endpointPanel()}</main></div>${this.state.toast?`<div class="toast">${esc(this.state.toast)}</div>`:''}</section>`; this.shadowRoot.getElementById('refresh')?.addEventListener('click',()=>this.refresh()); this.shadowRoot.getElementById('prune')?.addEventListener('click',()=>this.pruneImages()); this.shadowRoot.getElementById('evalPolicy')?.addEventListener('click',()=>this.evaluatePolicy()); this.shadowRoot.querySelectorAll('[data-nav]').forEach(b=>b.addEventListener('click',()=>this.setActive(Number(b.getAttribute('data-nav'))))); this.shadowRoot.getElementById('filter')?.addEventListener('input',e=>this.setFilter(e.target.value)); this.shadowRoot.querySelectorAll('[data-act]').forEach(btn=>btn.addEventListener('click',()=>{ const [area,act]=btn.getAttribute('data-act').split(':'); const key=btn.getAttribute('data-key'); const ep=this.activeEndpoint(); const item=(ep.rows||[]).find(r=>String(flattenItem(r).id||flattenItem(r).name)===String(key)); if(area==='docker') this.dockerAction(act,item||{}); if(area==='task') this.taskStatus(item||{},act); if(area==='release') this.releaseAction(act,item||{}); if(area==='k8s') this.k8sAction(act,item||{}); })); }
  }
  if(!customElements.get(CFG.tag)) customElements.define(CFG.tag, LiveRemote);
})();
