/* NebulaOps v23.1 auth bridge
 * Makes shell-loaded and standalone MFE requests share a valid Bearer token.
 * - Shell origin: reuses localStorage token or bootstraps dev admin token.
 * - Standalone MFE route (/remotes/<mfe>/): uses the same nebulaops.localhost origin and the shared shell token.
 * - Patches fetch/XMLHttpRequest so the first API request waits for token availability.
 * - Rewrites legacy relative runtime endpoints to the gateway API path.
 */
(function nebulaopsAuthBridge() {
  'use strict';
  var VERSION = 'v23.1.9-restore-ui-live-api';
  var JWT_KEY = 'nebulaops.v23_1.jwt';
  var USER_KEY = 'nebulaops.v23_1.user';
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
      'tasks': '/api/tasks',
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


/* NebulaOps v23.1.0-live-real-data-control-plane · live endpoint custom element.
   This bundle renders only responses returned by NebulaOps APIs. */
(function nebulaopsLiveRemote(){
  'use strict';
  const CFG = {
  "tag": "nebulaops-mfe-ai-ops",
  "title": "AI Ops",
  "scope": "AI · RCA/Assist",
  "port": 4218,
  "id": "ai-ops",
  "config": {
    "endpoints": [
      {
        "label": "AI incidents",
        "url": "/api/ai-ops/incidents",
        "kind": "incident"
      },
      {
        "label": "Kubernetes logs",
        "url": "/api/kubernetes/logs",
        "kind": "log"
      },
      {
        "label": "Platform events",
        "url": "/api/events?limit=50",
        "kind": "event"
      }
    ]
  }
};
  const VERSION = 'v23.1.0-live-real-data-control-plane';
  const TOKEN_KEY = 'nebulaops.v23_1.jwt';
  const css = `
    :host{display:block;color:#eaf4ff;font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}
    .mfe{min-height:640px;padding:28px;background:radial-gradient(circle at top left,rgba(25,179,255,.18),transparent 36%),linear-gradient(135deg,#07111f,#0b1026 58%,#11152d)}
    .hero{display:flex;gap:18px;align-items:center;justify-content:space-between;flex-wrap:wrap;margin-bottom:22px}.hero-left{display:flex;gap:16px;align-items:center;min-width:280px}.icon{width:72px;height:72px;border-radius:22px;display:grid;place-items:center;background:linear-gradient(135deg,#00c8ff,#8b5cf6);box-shadow:0 18px 40px rgba(0,0,0,.3);font-size:34px}.eyebrow{letter-spacing:.22em;text-transform:uppercase;color:#6ee7ff;font-size:12px;font-weight:800}.title{margin:4px 0 6px;font-size:34px;line-height:1;font-weight:900}.subtitle{color:#a8b7d8;font-size:15px;max-width:820px}.actions{display:flex;gap:10px;flex-wrap:wrap}.btn{border:1px solid rgba(125,211,252,.28);background:rgba(14,24,48,.82);color:#eaf4ff;border-radius:12px;padding:10px 14px;font-weight:800;cursor:pointer;text-decoration:none}.btn.primary{background:linear-gradient(135deg,#0ea5e9,#7c3aed);border:0}.btn.danger{border-color:rgba(248,113,113,.45);color:#fecaca}.btn.warn{border-color:rgba(245,158,11,.45);color:#fde68a}.grid{display:grid;grid-template-columns:280px 1fr;gap:18px}.side{border:1px solid rgba(148,163,184,.18);background:rgba(8,15,34,.72);border-radius:22px;padding:14px;align-self:start;position:sticky;top:12px}.side h3{font-size:12px;text-transform:uppercase;letter-spacing:.18em;color:#78e0ff;margin:4px 8px 12px}.navbtn{width:100%;text-align:left;display:flex;justify-content:space-between;gap:10px;margin:6px 0;border:1px solid transparent;background:transparent;color:#b7c6e6;border-radius:14px;padding:11px 12px;cursor:pointer;font-weight:800}.navbtn.active,.navbtn:hover{background:rgba(14,165,233,.13);border-color:rgba(14,165,233,.35);color:#fff}.count{font-size:11px;padding:2px 8px;border-radius:999px;background:rgba(14,165,233,.18);border:1px solid rgba(14,165,233,.3)}.content{min-width:0}.cards{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin-bottom:18px}.card{border:1px solid rgba(148,163,184,.18);background:linear-gradient(180deg,rgba(16,26,55,.86),rgba(9,16,36,.82));border-radius:20px;padding:16px;box-shadow:0 18px 50px rgba(0,0,0,.2)}.metric-label{color:#9fb0d4;font-size:12px}.metric-value{font-size:26px;font-weight:900;margin-top:6px}.panel{border:1px solid rgba(56,189,248,.24);background:rgba(4,10,24,.72);border-radius:22px;overflow:hidden;margin-bottom:18px}.panel-head{display:flex;justify-content:space-between;gap:12px;align-items:center;padding:15px 18px;border-bottom:1px solid rgba(148,163,184,.16);background:rgba(15,23,42,.65)}.panel-title{font-size:15px;font-weight:900}.status{font-size:12px;border:1px solid rgba(148,163,184,.24);border-radius:999px;padding:5px 9px;color:#b7c6e6}.status.ok{color:#bbf7d0;border-color:rgba(34,197,94,.45);background:rgba(22,163,74,.12)}.status.warn{color:#fde68a;border-color:rgba(245,158,11,.45);background:rgba(245,158,11,.12)}.status.err{color:#fecaca;border-color:rgba(248,113,113,.45);background:rgba(239,68,68,.12)}.toolbar{display:flex;gap:10px;align-items:center;flex-wrap:wrap}.input,.select,.textarea{background:rgba(15,23,42,.8);border:1px solid rgba(148,163,184,.2);border-radius:12px;color:#eaf4ff;padding:10px 12px;min-width:180px}.textarea{width:100%;min-height:220px;font-family:SFMono-Regular,Consolas,monospace;resize:vertical}.table-wrap{overflow:auto}.table{width:100%;border-collapse:collapse;min-width:900px}.table th{text-align:left;color:#7dd3fc;text-transform:uppercase;letter-spacing:.14em;font-size:11px;background:rgba(15,23,42,.55)}.table th,.table td{padding:13px 14px;border-bottom:1px solid rgba(148,163,184,.12);vertical-align:top}.table td{font-size:13px;color:#dce7ff}.mono{font-family:SFMono-Regular,Consolas,monospace;font-size:12px;color:#a7f3d0}.json{max-height:420px;overflow:auto;white-space:pre-wrap;font-family:SFMono-Regular,Consolas,monospace;font-size:12px;color:#c7d2fe;background:rgba(2,6,23,.6);border-radius:16px;padding:14px}.empty{padding:32px;color:#a8b7d8;text-align:center}.toast{position:fixed;right:24px;bottom:24px;background:#0f172a;border:1px solid rgba(125,211,252,.35);border-radius:14px;padding:12px 16px;box-shadow:0 18px 40px rgba(0,0,0,.35);z-index:50}.pill{display:inline-flex;align-items:center;gap:6px;border-radius:999px;padding:4px 8px;font-size:11px;border:1px solid rgba(148,163,184,.22);color:#b7c6e6}.pill.ok{color:#bbf7d0;border-color:rgba(34,197,94,.38)}.pill.err{color:#fecaca;border-color:rgba(248,113,113,.4)}.pill.warn{color:#fde68a;border-color:rgba(245,158,11,.45)}.muted{color:#8ea2c9}.small{font-size:12px}.inline-actions{display:flex;gap:8px;flex-wrap:wrap}.kanban{display:grid;grid-template-columns:repeat(4,minmax(220px,1fr));gap:14px}.kanban-col{border:1px solid rgba(148,163,184,.18);background:rgba(8,15,34,.72);border-radius:20px;padding:12px;min-height:360px}.kanban-col.drag-over{border-color:rgba(56,189,248,.8);box-shadow:0 0 0 3px rgba(56,189,248,.12)}.kanban-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:10px}.task-card{border:1px solid rgba(148,163,184,.2);background:linear-gradient(180deg,rgba(16,26,55,.92),rgba(9,16,36,.9));border-radius:16px;padding:12px;margin-bottom:10px;cursor:grab}.task-title{font-weight:900;margin-bottom:6px}.task-meta,.task-labels{display:flex;gap:6px;flex-wrap:wrap;margin:8px 0}.task-labels span{font-size:11px;color:#bfdbfe;background:rgba(59,130,246,.14);border:1px solid rgba(59,130,246,.24);padding:3px 7px;border-radius:999px}.form-grid{display:grid;grid-template-columns:repeat(4,minmax(160px,1fr));gap:10px;padding:16px}.form-grid textarea{grid-column:span 3;min-height:44px;resize:vertical}@media(max-width:1100px){.grid{grid-template-columns:1fr}.side{position:static}.kanban{grid-template-columns:repeat(2,minmax(0,1fr))}.form-grid{grid-template-columns:1fr}.form-grid textarea{grid-column:auto}}@media(max-width:700px){.cards,.kanban{grid-template-columns:1fr}.title{font-size:28px}}
  `;
  const iconById = { 'docker-desktop':'🐳','openlens-kubernetes':'☸️','task-management':'✅','observability':'📈','cicd-gitops':'🚀','terraform-studio':'🧱','devsecops':'🛡️','ai-ops':'🤖','finops-cost':'💶','infra-hub':'🏗️','release-center':'🚢','policy-center':'⚖️','notification-center':'🔔','identity-admin':'👥','progressive-delivery':'🧭','platform-catalog':'📚','incident-command-center':'🚨','runtime-readiness':'🟢','docker-storage-cleanup':'🧹','environment-configuration':'🔧','dependency-impact':'🕸️','test-quality-dashboard':'🧪' };
  function token(){ try { return window.__NEBULAOPS_ACCESS_TOKEN__ || localStorage.getItem(TOKEN_KEY) || ''; } catch(_) { return window.__NEBULAOPS_ACCESS_TOKEN__ || ''; } }
  function esc(value){ return String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch])); }
  function getPath(obj, path){ if(!path) return obj; return String(path).split('.').reduce((acc,key)=> acc && acc[key] !== undefined ? acc[key] : undefined, obj); }
  function asArray(payload, path){
    const selected = getPath(payload, path); const val = selected !== undefined ? selected : payload;
    if(Array.isArray(val)) return val;
    if(val && Array.isArray(val.items)) return val.items;
    if(val && val.data && Array.isArray(val.data.items)) return val.data.items;
    if(val && val.data && Array.isArray(val.data)) return val.data;
    if(val && val.body && val.body.data && Array.isArray(val.body.data.result)) return val.body.data.result;
    if(val && val.body && Array.isArray(val.body.items)) return val.body.items;
    if(val && typeof val === 'object') return Object.entries(val).filter(([k]) => !['toolStatus','data','body','live','url','statusCode','durationMs','executedAt','realDataOnly'].includes(k)).map(([key,value]) => ({ key, value }));
    return [];
  }
  function liveFlag(payload, ok){ if(!ok) return false; if(payload && typeof payload === 'object' && (payload.live === false || payload.ok === false)) return false; if(payload && payload.error) return false; return true; }
  function human(v){ if(v === null || v === undefined || v === '') return '—'; if(typeof v === 'object') return JSON.stringify(v); return String(v); }
  function meta(item){ const m=item?.metadata || {}; const st=item?.status || {}; const sp=item?.spec || {}; const firstContainer=Array.isArray(sp.containers)?sp.containers[0]:(sp.template?.spec?.containers?.[0]||{}); return { id:item?.id || item?.Id || m.uid || item?.name || item?.Name || m.name || item?.key || item?.repository || '', name:item?.name || item?.Name || m.name || item?.key || item?.repository || item?.title || item?.id || '', namespace:item?.namespace || m.namespace || '', kind:item?.kind || item?.Kind || '', status:item?.status || item?.State || st.phase || st.status || st.sync?.status || item?.phase || item?.health || item?.severity || '', image:item?.image || item?.Image || firstContainer?.image || '', ports:item?.ports || item?.Ports || '', created:item?.created || item?.createdAt || m.creationTimestamp || item?.updatedAt || '', raw:item }; }
  class LiveRemote extends HTMLElement {
    constructor(){ super(); this.attachShadow({mode:'open'}); this.state={active:0,filter:'',loading:false,endpoints:[],toast:'',detail:null,realm:'nebulaops',dragTaskId:'',taskFilter:{q:'',assignee:'',priority:''},yaml:'',clusterId:'',kubeconfig:''}; }
    connectedCallback(){ this.render(); this.refresh(); }
    toast(msg){ this.state.toast=msg; this.render(); setTimeout(()=>{ this.state.toast=''; this.render(); },2800); }
    async request(url, opts={}){ await (window.__NEBULAOPS_AUTH_READY__?.catch ? window.__NEBULAOPS_AUTH_READY__.catch(()=>undefined) : Promise.resolve()); const h=new Headers(opts.headers || {}); if(!h.has('Content-Type') && opts.body) h.set('Content-Type','application/json'); const t=token(); if(t && !h.has('Authorization')) h.set('Authorization','Bearer '+t); h.set('X-NebulaOps-MFE',CFG.id); h.set('X-NebulaOps-Live-Only',VERSION); const res=await fetch(url,{...opts,headers:h}); const text=await res.text(); let body=text; try{ body=text?JSON.parse(text):{}; }catch(_){} if(!res.ok) throw {status:res.status,body}; return body; }
    endpointUrl(ep){ let url=ep.url; if(CFG.id==='identity-admin' && url?.includes('/api/identity/realms/')) url=url.replace(/\/realms\/[^/]+\//,`/realms/${encodeURIComponent(this.state.realm || 'nebulaops')}/`); return this.withCluster(url); }
    withCluster(url){ if(CFG.id!=='openlens-kubernetes' || !url) return url; const clusterId=(this.state.clusterId||'').trim(); if(!clusterId || clusterId==='current-context') return url; if(!url.includes('/api/kubernetes') || url.includes('/api/kubernetes/kubeconfigs')) return url; return url + (url.includes('?')?'&':'?') + 'clusterId=' + encodeURIComponent(clusterId); }
    kubeconfigEndpoint(){ return (this.state.endpoints || []).find(e=>String(e.url||e.label||'').includes('kubeconfigs') || String(e.label||'').toLowerCase().includes('cluster registry')) || {rows:[],body:{}}; }
    currentKubeContext(){ return this.kubeconfigEndpoint().body?.currentContext || {}; }
    selectedClusterLabel(){ const id=this.state.clusterId || 'current-context'; if(!id || id==='current-context') return this.currentKubeContext().name || 'kubectl current context'; const found=(this.kubeconfigEndpoint().rows||[]).find(c=>String(c.id)===String(id) || String(c.name)===String(id)); return found?.name || id; }
    async refresh(){ const endpoints=CFG.config.endpoints || []; this.state.loading=true; this.render(); const load=async(ep)=>{ const started=performance.now(); try{ const body=await this.request(this.endpointUrl(ep),{method:ep.method || 'GET', body:ep.body?JSON.stringify(ep.body):undefined}); const rows=asArray(body,ep.itemsPath); return {...ep,url:this.endpointUrl(ep),ok:true,live:liveFlag(body,true),duration:Math.round(performance.now()-started),body,rows}; }catch(e){ return {...ep,url:this.endpointUrl(ep),ok:false,live:false,duration:Math.round(performance.now()-started),body:e.body || {error:e.message || String(e), status:e.status},rows:[]}; } }; this.state.endpoints=await Promise.all(endpoints.map(load)); this.state.loading=false; this.render(); }
    counts(){ const total=this.state.endpoints.reduce((a,e)=>a+(e.rows?e.rows.length:0),0); const live=this.state.endpoints.filter(e=>e.live).length; const errors=this.state.endpoints.filter(e=>!e.ok||!e.live).length; return {total,live,errors,endpoints:this.state.endpoints.length}; }
    activeEndpoint(){ return this.state.endpoints[this.state.active] || this.state.endpoints[0] || (CFG.config.endpoints||[])[0] || {}; }
    setActive(i){ this.state.active=i; this.state.detail=null; this.render(); }
    setFilter(v){ this.state.filter=String(v||'').toLowerCase(); this.render(); }
    filteredRows(ep){ const q=this.state.filter; const rows=ep.rows || []; return q ? rows.filter(r=>JSON.stringify(r).toLowerCase().includes(q)) : rows; }
    showDetail(title, body){ this.state.detail={title,body}; this.render(); }

    async dockerAction(action,item){ const row=meta(item); const ep=this.activeEndpoint(); const label=String(ep.label||'').toLowerCase(); const id=encodeURIComponent(row.id || row.name || ''); if(!id) return; let spec=null;
      if(label.includes('container')) spec={start:['POST',`/api/runtime/docker/containers/${id}/start`],stop:['POST',`/api/runtime/docker/containers/${id}/stop`],restart:['POST',`/api/runtime/docker/containers/${id}/restart`],remove:['DELETE',`/api/runtime/docker/containers/${id}`],logs:['GET',`/api/runtime/docker/containers/${id}/logs?tail=200`],stats:['GET',`/api/runtime/docker/containers/${id}/stats`],inspect:['GET',`/api/runtime/docker/containers/${id}/inspect`]}[action];
      if(label.includes('image')) spec={inspect:['GET',`/api/runtime/docker/images/${id}/inspect`],remove:['DELETE',`/api/runtime/docker/images/${id}`]}[action];
      if(label.includes('volume')) spec={inspect:['GET',`/api/runtime/docker/volumes/${encodeURIComponent(row.name)}/inspect`],remove:['DELETE',`/api/runtime/docker/volumes/${encodeURIComponent(row.name)}`]}[action];
      if(label.includes('network')) spec={inspect:['GET',`/api/runtime/docker/networks/${id}/inspect`],remove:['DELETE',`/api/runtime/docker/networks/${id}`]}[action];
      if(!spec) return; if(['remove','stop'].includes(action) && !confirm(`${action} ${row.name || row.id}?`)) return;
      try{ const body=await this.request(spec[1],{method:spec[0]}); if(['logs','stats','inspect'].includes(action)) this.showDetail(`Docker ${action}: ${row.name || row.id}`, body); else { this.toast(`${action} executed for ${row.name || id}`); await this.refresh(); } }catch(e){ this.toast(`${action} failed: ${human(e.body?.error || e.message)}`); }
    }
    async dockerPrune(type){ const map={images:'/api/runtime/docker/images/prune',volumes:'/api/runtime/docker/volumes/prune',networks:'/api/runtime/docker/networks/prune',system:'/api/runtime/docker/system/prune'}; if(type==='system' && !confirm('Run Docker system prune?')) return; try{ const body=await this.request(map[type],{method:'POST',body:JSON.stringify({})}); this.showDetail(`Docker ${type} prune`,body); await this.refresh(); }catch(e){ this.toast(`${type} prune failed`); } }

    async k8sAction(action,item){ const row=meta(item); const kind=(row.kind || this.activeEndpoint().kind || '').toLowerCase(); const ns=encodeURIComponent(row.namespace || 'default'); const name=encodeURIComponent(row.name || ''); if(!name) return; let url='',method='POST',body={};
      const plural = kind.includes('ingress') ? 'ingresses' : kind.includes('service') ? 'services' : kind.includes('configmap') ? 'configmaps' : kind.includes('statefulset') ? 'statefulsets' : 'deployments';
      if(action==='podLogs'){ url=`/api/kubernetes/pods/${ns}/${name}/logs`; method='GET'; }
      if(action==='podDescribe'){ url=`/api/kubernetes/pods/${ns}/${name}/describe`; method='GET'; }
      if(action==='restartPod') url=`/api/kubernetes/pods/${ns}/${name}/restart`;
      if(action==='deletePod'){ url=`/api/kubernetes/pods/${ns}/${name}`; method='DELETE'; if(!confirm(`Delete pod ${row.name}?`)) return; }
      if(action==='scale'){ const replicas=prompt('Replicas', String(item?.spec?.replicas ?? item?.replicas ?? 1)); if(replicas===null) return; url=`/api/kubernetes/${plural}/${ns}/${name}/scale`; body={replicas:Number(replicas)}; }
      if(action==='restartWorkload') url=`/api/kubernetes/${plural}/${ns}/${name}/restart`;
      if(action==='yaml'){ url=`/api/kubernetes/${plural}/${ns}/${name}/yaml`; method='GET'; }
      if(action==='describe'){ url=`/api/kubernetes/${plural}/${ns}/${name}/describe`; method='GET'; }
      if(action==='cordon') url=`/api/kubernetes/nodes/${name}/cordon`;
      if(action==='uncordon') url=`/api/kubernetes/nodes/${name}/uncordon`;
      if(action==='drain'){ if(!confirm(`Drain node ${row.name}?`)) return; url=`/api/kubernetes/nodes/${name}/drain`; }
      if(action==='describeNode'){ url=`/api/kubernetes/nodes/${name}/describe`; method='GET'; }
      if(action==='describeNamespace'){ url=`/api/kubernetes/namespaces/${name}/describe`; method='GET'; }
      url=this.withCluster(url);
      try{ const result=await this.request(url,{method,body:method==='GET'||method==='DELETE'?undefined:JSON.stringify(body)}); if(['podLogs','podDescribe','yaml','describe','describeNode','describeNamespace'].includes(action)){ this.showDetail(`Kubernetes ${action}: ${row.namespace?row.namespace+'/':''}${row.name}`, result); if(action==='yaml' && result.stdout) this.state.yaml=result.stdout; } else { this.toast(`${action} executed for ${row.name}`); await this.refresh(); } }catch(e){ this.toast(`${action} failed: ${human(e.body?.stderr || e.body?.error || e.message)}`); }
    }
    async yamlAction(action){ const yaml=(this.shadowRoot.getElementById('yamlEditor')?.value || '').trim(); this.state.yaml=yaml; if(!yaml){ this.toast('YAML is required'); return; } let url=action==='diff'?'/api/kubernetes/resources/diff':action==='delete'?'/api/kubernetes/delete':'/api/kubernetes/apply'; url=this.withCluster(url); try{ const body=await this.request(url,{method:'POST',body:JSON.stringify({yaml})}); this.showDetail(`YAML ${action} · ${this.selectedClusterLabel()}`,body); if(action!=='diff') await this.refresh(); }catch(e){ this.toast(`YAML ${action} failed: ${human(e.body?.stderr || e.body?.error || e.message)}`); } }

    async deliveryAction(action,item){ const row=meta(item); const ns=encodeURIComponent(row.namespace || item?.metadata?.namespace || 'default'); const name=encodeURIComponent(row.name || ''); if(!name) return; const isApp=(this.activeEndpoint().kind || '').toLowerCase().includes('application'); let url='',method='POST'; if(isApp && action==='sync') url=`/api/progressive-delivery/applications/${name}/sync`; else { url=`/api/progressive-delivery/rollouts/${ns}/${name}/${action}`; if(action==='history') method='GET'; } try{ const body=await this.request(url,{method,body:method==='GET'?undefined:JSON.stringify({})}); this.showDetail(`Progressive delivery ${action}: ${row.name}`,body); await this.refresh(); }catch(e){ this.toast(`${action} failed: ${human(e.body?.error || e.message)}`); } }
    async releaseAction(kind,item){ const id=encodeURIComponent(item.id || item.name || meta(item).id || ''); if(!id) return; try{ const body=await this.request(`/api/releases/${id}/${kind}`,{method:'POST',body:JSON.stringify({})}); this.showDetail(`Release ${kind}: ${id}`,body); await this.refresh(); }catch(e){ this.toast(`Release ${kind} failed`); } }
    async releaseTimeline(item){ const id=encodeURIComponent(item.id || item.name || meta(item).id || ''); if(!id) return; try{ this.showDetail(`Release timeline: ${id}`, await this.request(`/api/releases/${id}/timeline`)); }catch(e){ this.toast('Timeline unavailable'); } }
    async evaluatePolicy(){ try{ const body=await this.request('/api/governance/decisions',{method:'POST',body:JSON.stringify({action:'runtime.evaluate',target:{name:'current-runtime'},payload:{requestedFrom:'policy-center'}})}); this.showDetail('Governance decision',body); await this.refresh(); }catch(e){ this.toast('Governance evaluation failed'); } }
    async approvalAction(id,action){ if(!id) return; const comment=prompt(action==='approve'?'Approval comment':'Rejection comment',''); if(comment===null) return; try{ const body=await this.request(`/api/governance/approvals/${encodeURIComponent(id)}/${action}`,{method:'POST',body:JSON.stringify({comment})}); this.showDetail(`Approval ${action}`,body); await this.refresh(); }catch(e){ this.toast(`Approval ${action} failed`); } }

    actionButtons(ep,item){ const id=CFG.id; const row=meta(item); const kind=(row.kind || ep.kind || '').toLowerCase(); const label=String(ep.label||'').toLowerCase();
      if(id==='docker-desktop' && label.includes('container')) return `<div class="inline-actions"><button class="btn" data-act="docker:logs" data-key="${esc(row.id)}">Logs</button><button class="btn" data-act="docker:stats" data-key="${esc(row.id)}">Stats</button><button class="btn" data-act="docker:inspect" data-key="${esc(row.id)}">Inspect</button><button class="btn" data-act="docker:start" data-key="${esc(row.id)}">Start</button><button class="btn" data-act="docker:stop" data-key="${esc(row.id)}">Stop</button><button class="btn" data-act="docker:restart" data-key="${esc(row.id)}">Restart</button><button class="btn danger" data-act="docker:remove" data-key="${esc(row.id)}">Delete</button></div>`;
      if(id==='docker-desktop' && (label.includes('image')||label.includes('volume')||label.includes('network'))) return `<div class="inline-actions"><button class="btn" data-act="docker:inspect" data-key="${esc(row.id || row.name)}">Inspect</button><button class="btn danger" data-act="docker:remove" data-key="${esc(row.id || row.name)}">Delete</button></div>`;
      if(id==='openlens-kubernetes' && kind.includes('kubeconfig')) return `<div class="inline-actions"><button class="btn" data-kube-cluster="${esc(item.id || item.name)}">Open tab</button><button class="btn" data-kube-probe="${esc(item.id || item.name)}">Probe</button><button class="btn danger" data-kube-delete="${esc(item.id || item.name)}">Delete</button></div>`;
      if(id==='openlens-kubernetes' && kind.includes('pod')) return `<div class="inline-actions"><button class="btn" data-act="k8s:podLogs" data-key="${esc(row.id || row.name)}">Logs</button><button class="btn" data-act="k8s:podDescribe" data-key="${esc(row.id || row.name)}">Describe</button><button class="btn" data-act="k8s:restartPod" data-key="${esc(row.id || row.name)}">Restart</button><button class="btn danger" data-act="k8s:deletePod" data-key="${esc(row.id || row.name)}">Delete</button></div>`;
      if(id==='openlens-kubernetes' && (kind.includes('deployment')||kind.includes('statefulset'))) return `<div class="inline-actions"><button class="btn" data-act="k8s:yaml" data-key="${esc(row.id || row.name)}">YAML</button><button class="btn" data-act="k8s:describe" data-key="${esc(row.id || row.name)}">Describe</button><button class="btn" data-act="k8s:scale" data-key="${esc(row.id || row.name)}">Scale</button><button class="btn" data-act="k8s:restartWorkload" data-key="${esc(row.id || row.name)}">Restart</button></div>`;
      if(id==='openlens-kubernetes' && (kind.includes('service')||kind.includes('ingress')||kind.includes('configmap'))) return `<div class="inline-actions"><button class="btn" data-act="k8s:yaml" data-key="${esc(row.id || row.name)}">YAML</button><button class="btn" data-act="k8s:describe" data-key="${esc(row.id || row.name)}">Describe</button></div>`;
      if(id==='openlens-kubernetes' && kind.includes('node')) return `<div class="inline-actions"><button class="btn" data-act="k8s:describeNode" data-key="${esc(row.id || row.name)}">Describe</button><button class="btn warn" data-act="k8s:cordon" data-key="${esc(row.id || row.name)}">Cordon</button><button class="btn" data-act="k8s:uncordon" data-key="${esc(row.id || row.name)}">Uncordon</button><button class="btn danger" data-act="k8s:drain" data-key="${esc(row.id || row.name)}">Drain</button></div>`;
      if(id==='openlens-kubernetes' && kind.includes('namespace')) return `<div class="inline-actions"><button class="btn" data-act="k8s:describeNamespace" data-key="${esc(row.id || row.name)}">Describe</button></div>`;
      if(id==='progressive-delivery' && (kind.includes('rollout') || label.includes('rollout'))) return `<div class="inline-actions"><button class="btn" data-act="delivery:promote" data-key="${esc(row.id || row.name)}">Promote</button><button class="btn danger" data-act="delivery:abort" data-key="${esc(row.id || row.name)}">Abort</button><button class="btn" data-act="delivery:restart" data-key="${esc(row.id || row.name)}">Restart</button><button class="btn" data-act="delivery:history" data-key="${esc(row.id || row.name)}">History</button></div>`;
      if(id==='progressive-delivery' && (kind.includes('application') || label.includes('application'))) return `<div class="inline-actions"><button class="btn" data-act="delivery:sync" data-key="${esc(row.id || row.name)}">Sync</button></div>`;
      if(id==='release-center') return `<div class="inline-actions"><button class="btn" data-act="release:promote" data-key="${esc(row.id || row.name)}">Promote</button><button class="btn danger" data-act="release:rollback" data-key="${esc(row.id || row.name)}">Rollback</button><button class="btn" data-act="release:timeline" data-key="${esc(row.id || row.name)}">Timeline</button></div>`;
      if(id==='policy-center' && /approval/i.test(ep.label||'') && String(item.status||'').toUpperCase()==='PENDING') return `<div class="inline-actions"><button class="btn" data-approval-act="approve" data-approval-id="${esc(item.id || row.id)}">Approve</button><button class="btn danger" data-approval-act="reject" data-approval-id="${esc(item.id || row.id)}">Reject</button></div>`;
      return '';
    }

    endpointPanel(){ const ep=this.activeEndpoint(); const rows=this.filteredRows(ep); const statusClass=ep.ok && ep.live ? 'ok' : ep.ok ? 'warn' : 'err'; const table=rows.length?`<div class="table-wrap"><table class="table"><thead><tr><th>ID</th><th>Name</th><th>Source/Image</th><th>Status</th><th>Details</th><th>Actions</th></tr></thead><tbody>${rows.map((r,i)=>this.rowHtml(ep,r,i)).join('')}</tbody></table></div>`:`<div class="empty">No records returned by this live endpoint.<br><span class="small mono">${esc(ep.url || '')}</span></div>`; return `<div class="panel"><div class="panel-head"><div><div class="panel-title">${esc(ep.label || 'Endpoint')}</div><div class="muted small mono">${esc(ep.url || '')}</div></div><div class="toolbar"><input class="input" id="filter" placeholder="Filter current endpoint..." value="${esc(this.state.filter)}"><span class="status ${statusClass}">${ep.ok ? (ep.live ? 'LIVE' : 'NO LIVE DATA') : 'ERROR'} · ${ep.duration ?? 0}ms</span></div></div>${table}<div style="padding:16px"><details open><summary class="muted small">Raw endpoint response</summary><pre class="json">${esc(JSON.stringify(ep.body||{},null,2))}</pre></details></div></div>${this.detailPanel()}`; }
    rowHtml(ep,item,i){ const row=meta(item); const status=row.status || (ep.live?'LIVE':'UNAVAILABLE'); const pillClass=/run|ready|healthy|synced|live|available|up|pass|ok|running/i.test(String(status))?'ok':/fail|error|down|blocked|unavailable|denied/i.test(String(status))?'err':'warn'; return `<tr><td class="mono">${esc(row.id || i+1)}</td><td><strong>${esc(row.name || item.key || ep.kind || 'record')}</strong><div class="muted small">${esc(row.namespace || row.kind || ep.kind || '')}</div></td><td>${esc(row.image || item.source || item.endpoint || item.url || '')}</td><td><span class="pill ${pillClass}">${esc(human(status))}</span></td><td>${esc(row.ports || row.created || item.message || item.toolStatus || item.error || '—')}</td><td>${this.actionButtons(ep,item)}</td></tr>`; }
    detailPanel(){ if(!this.state.detail) return ''; return `<div class="panel"><div class="panel-head"><div class="panel-title">${esc(this.state.detail.title)}</div><button class="btn" id="closeDetail">Close</button></div><div style="padding:16px"><pre class="json">${esc(JSON.stringify(this.state.detail.body,null,2))}</pre></div></div>`; }

    dockerConsole(){ return `<div class="panel"><div class="panel-head"><div><div class="panel-title">Docker operational actions</div><div class="muted small">Actions use Docker Engine API through the Unix socket and return explicit socket/permission/engine states.</div></div><div class="toolbar"><button class="btn" data-prune="images">Prune images</button><button class="btn" data-prune="volumes">Prune volumes</button><button class="btn" data-prune="networks">Prune networks</button><button class="btn danger" data-prune="system">System prune</button></div></div></div>${this.genericGrid()}`; }
    async saveKubeconfig(){ const root=this.shadowRoot; const kubeconfig=(root.getElementById('kubeconfigInput')?.value || '').trim(); const name=(root.getElementById('kubeconfigName')?.value || '').trim(); const description=(root.getElementById('kubeconfigDescription')?.value || '').trim(); if(!kubeconfig){ this.toast('Kubeconfig YAML is required'); return; } try{ const body=await this.request('/api/kubernetes/kubeconfigs',{method:'POST',body:JSON.stringify({name,description,kubeconfig})}); const saved=body.item || {}; this.state.clusterId=saved.id || saved.name || this.state.clusterId; this.toast('Kubeconfig saved in MongoDB'); await this.refresh(); }catch(e){ this.toast(`Kubeconfig save failed: ${human(e.body?.error || e.message)}`); } }
    async deleteKubeconfig(id){ if(!id) return; if(!confirm('Delete this kubeconfig from MongoDB?')) return; try{ await this.request(`/api/kubernetes/kubeconfigs/${encodeURIComponent(id)}`,{method:'DELETE'}); if(String(this.state.clusterId)===String(id)) this.state.clusterId=''; this.toast('Kubeconfig deleted'); await this.refresh(); }catch(e){ this.toast(`Kubeconfig delete failed: ${human(e.body?.error || e.message)}`); } }
    async probeKubeconfig(id){ try{ const body=await this.request(`/api/kubernetes/kubeconfigs/${encodeURIComponent(id || 'current-context')}/probe`,{method:'POST'}); this.showDetail(`Cluster probe · ${id || 'current-context'}`,body); }catch(e){ this.toast(`Cluster probe failed: ${human(e.body?.error || e.message)}`); } }
    openlensConsole(){ const registry=this.kubeconfigEndpoint(); const saved=registry.rows || []; const current=this.currentKubeContext(); const selected=this.state.clusterId || 'current-context'; const currentLive=current.live===true; const tabs=`<button class="navbtn ${selected==='current-context'?'active':''}" data-kube-cluster="current-context"><span>${esc(current.name || 'kubectl current context')}</span><span class="count">${currentLive?'LIVE':'LOCAL'}</span></button>` + saved.map(c=>`<button class="navbtn ${String(selected)===String(c.id)?'active':''}" data-kube-cluster="${esc(c.id)}"><span>${esc(c.name || c.contextName || c.id)}</span><span class="count">MongoDB</span></button>`).join(''); const savedRows=saved.length?saved.map(c=>`<tr><td><strong>${esc(c.name || c.id)}</strong><div class="muted small">${esc(c.description || c.contextName || '')}</div></td><td class="mono">${esc(c.clusterName || '')}</td><td>${esc(c.namespace || '')}</td><td class="mono">${esc(c.server || '')}</td><td><div class="inline-actions"><button class="btn" data-kube-cluster="${esc(c.id)}">Open tab</button><button class="btn" data-kube-probe="${esc(c.id)}">Probe</button><button class="btn danger" data-kube-delete="${esc(c.id)}">Delete</button></div></td></tr>`).join(''):`<tr><td colspan="5" class="muted">No saved kubeconfig in MongoDB. Paste a kubeconfig and save it to create a dedicated cluster tab.</td></tr>`; return `<div class="panel"><div class="panel-head"><div><div class="panel-title">Kubernetes cluster registry</div><div class="muted small">OpenLens now supports the local kubectl context and additional kubeconfig files persisted in MongoDB. Every tab runs the same pods, workloads, logs, YAML, describe, scale, drain and apply/delete actions against the selected cluster.</div></div><div class="toolbar"><span class="status ${registry.live?'ok':'warn'}">${registry.live?'MongoDB registry connected':'Registry unavailable'}</span></div></div><div class="grid" style="grid-template-columns:280px 1fr;padding:16px"><aside class="side" style="position:static"><h3>Cluster tabs</h3>${tabs}</aside><main class="content"><div class="cards"><div class="card"><div class="metric-label">Selected cluster</div><div class="metric-value">${esc(this.selectedClusterLabel())}</div></div><div class="card"><div class="metric-label">Saved kubeconfigs</div><div class="metric-value">${saved.length}</div></div><div class="card"><div class="metric-label">Registry source</div><div class="metric-value">MongoDB</div></div><div class="card"><div class="metric-label">Action target</div><div class="metric-value">${esc(selected)}</div></div></div><div class="table-wrap"><table class="table"><thead><tr><th>Name</th><th>Cluster</th><th>Namespace</th><th>Server</th><th>Actions</th></tr></thead><tbody>${savedRows}</tbody></table></div><details style="margin-top:14px"><summary class="muted small">Save kubeconfig into MongoDB</summary><div class="form-grid"><input id="kubeconfigName" class="input" placeholder="Name, e.g. staging-eks"><input id="kubeconfigDescription" class="input" placeholder="Description / owner"><button class="btn primary" id="kubeconfigSave">Save kubeconfig</button><textarea id="kubeconfigInput" class="textarea" placeholder="Paste kubeconfig YAML here. It is sent to the gateway and persisted in MongoDB, not duplicated in the frontend."></textarea></div></details>${registry.error?`<pre class="json">${esc(registry.error)}</pre>`:''}</main></div></div><div class="panel"><div class="panel-head"><div><div class="panel-title">YAML editor · ${esc(this.selectedClusterLabel())}</div><div class="muted small">Apply, delete or diff manifests against the selected cluster tab.</div></div><div class="toolbar"><button class="btn" data-yaml="diff">Diff</button><button class="btn primary" data-yaml="apply">Apply YAML</button><button class="btn danger" data-yaml="delete">Delete YAML</button></div></div><div style="padding:16px"><textarea id="yamlEditor" class="textarea" placeholder="Paste Kubernetes YAML here...">${esc(this.state.yaml)}</textarea></div></div>${this.genericGrid()}`; }
    genericGrid(){ const endpoints=this.state.endpoints.length ? this.state.endpoints : (CFG.config.endpoints || []).map(e=>({...e,rows:[],ok:false,live:false,body:null})); const nav=endpoints.map((e,i)=>`<button class="navbtn ${i===this.state.active?'active':''}" data-nav="${i}"><span>${esc(e.label)}</span><span class="count">${e.rows?e.rows.length:0}</span></button>`).join(''); return `<div class="grid"><aside class="side"><h3>Live endpoints</h3>${nav}</aside><main class="content">${this.endpointPanel()}</main></div>`; }

    taskEndpoint(){ return (this.state.endpoints || []).find(e => String(e.url || '').includes('/api/tasks')) || {rows:[]}; }
    taskColumns(){ return [['TODO','To start'],['IN_PROGRESS','In progress'],['REVIEW','To test'],['DONE','Done']]; }
    allTasks(){ const f=this.state.taskFilter; return (this.taskEndpoint().rows || []).filter(t=>{ const q=(f.q||'').toLowerCase(); const text=JSON.stringify(t).toLowerCase(); return (!q || text.includes(q)) && (!f.assignee || String(t.assigneeId||'').toLowerCase().includes(f.assignee.toLowerCase())) && (!f.priority || String(t.priority||'')===f.priority); }); }
    async createTask(){ const root=this.shadowRoot; const title=root.getElementById('taskTitle')?.value?.trim(); if(!title){ this.toast('Task title is required'); return; } const body={ organizationId:root.getElementById('taskOrganization')?.value?.trim() || undefined, projectId:root.getElementById('taskProject')?.value?.trim() || undefined, title, description:root.getElementById('taskDescription')?.value?.trim() || '', status:root.getElementById('taskStatus')?.value || 'TODO', priority:root.getElementById('taskPriority')?.value || 'MEDIUM', assigneeId:root.getElementById('taskAssignee')?.value?.trim() || 'unassigned', labels:(root.getElementById('taskLabels')?.value || '').split(',').map(x=>x.trim()).filter(Boolean) }; try{ await this.request('/api/tasks',{method:'POST',body:JSON.stringify(body)}); this.toast('Task created and RabbitMQ notification event published'); await this.refresh(); }catch(e){ this.toast(`Task creation failed: ${human(e.body?.error || e.message)}`); } }
    async deleteTask(id){ if(!id || !confirm('Remove this task?')) return; try{ await this.request(`/api/tasks/${encodeURIComponent(id)}`,{method:'DELETE'}); this.toast('Task removed'); await this.refresh(); }catch(e){ this.toast('Task remove failed'); } }
    async moveTask(id,status,sortOrder){ if(!id || !status) return; try{ await this.request(`/api/tasks/${encodeURIComponent(id)}/move`,{method:'PATCH',body:JSON.stringify({status,sortOrder:sortOrder || Date.now()})}); this.toast(`Task moved to ${status}`); await this.refresh(); }catch(e){ this.toast(`Task move failed: ${human(e.body?.error || e.message)}`); } }
    taskCard(task){ const labels=Array.isArray(task.labels)?task.labels:[]; return `<article class="task-card" draggable="true" data-drag-task="${esc(task.id)}"><div class="task-title">${esc(task.title || task.name || 'Untitled task')}</div><div class="muted small">${esc(task.description || '')}</div><div class="task-meta"><span class="pill">${esc(task.priority || 'MEDIUM')}</span><span class="pill">${esc(task.assigneeId || 'unassigned')}</span>${task.releaseId?`<span class="pill">release ${esc(task.releaseId)}</span>`:''}${task.incidentId?`<span class="pill err">incident ${esc(task.incidentId)}</span>`:''}${task.policyApprovalId?`<span class="pill warn">approval ${esc(task.policyApprovalId)}</span>`:''}</div>${labels.length?`<div class="task-labels">${labels.map(l=>`<span>${esc(l)}</span>`).join('')}</div>`:''}<div class="inline-actions"><button class="btn" data-task-move="IN_PROGRESS" data-task-id="${esc(task.id)}">Start</button><button class="btn" data-task-move="REVIEW" data-task-id="${esc(task.id)}">Review</button><button class="btn" data-task-move="DONE" data-task-id="${esc(task.id)}">Done</button><button class="btn danger" data-task-delete="${esc(task.id)}">Remove</button></div></article>`; }
    taskBoard(){ const tasks=this.allTasks(); const cols=this.taskColumns().map(([status,label])=>{ const items=tasks.filter(t=>(t.status||'TODO')===status).sort((a,b)=>(a.sortOrder||0)-(b.sortOrder||0)); return `<section class="kanban-col" data-drop-status="${status}"><div class="kanban-head"><strong>${label}</strong><span class="count">${items.length}</span></div>${items.length?items.map(t=>this.taskCard(t)).join(''):'<div class="empty small">Drop tasks here</div>'}</section>`; }).join(''); return `<div class="panel"><div class="panel-head"><div><div class="panel-title">Enterprise task board</div><div class="muted small">Tasks are persisted in MongoDB, mutations publish RabbitMQ events, and linked audit/approval/release endpoints are loaded from live APIs.</div></div><div class="toolbar"><input id="taskSearch" class="input" placeholder="Search" value="${esc(this.state.taskFilter.q)}"><input id="taskAssigneeFilter" class="input" placeholder="Assignee" value="${esc(this.state.taskFilter.assignee)}"><select id="taskPriorityFilter" class="select"><option value="">All priorities</option>${['LOW','MEDIUM','HIGH','CRITICAL'].map(p=>`<option ${this.state.taskFilter.priority===p?'selected':''}>${p}</option>`).join('')}</select></div></div><div class="form-grid"><input id="taskTitle" class="input" placeholder="Title"><input id="taskAssignee" class="input" placeholder="Assignee username or email"><input id="taskProject" class="input" placeholder="Project / release id"><input id="taskOrganization" class="input" placeholder="Organization id (optional)"><select id="taskStatus" class="select"><option value="TODO">To start</option><option value="IN_PROGRESS">In progress</option><option value="REVIEW">Review</option><option value="DONE">Done</option></select><select id="taskPriority" class="select"><option>LOW</option><option selected>MEDIUM</option><option>HIGH</option><option>CRITICAL</option></select><input id="taskLabels" class="input" placeholder="Labels, comma separated"><textarea id="taskDescription" class="input" placeholder="Description"></textarea><button class="btn primary" id="taskCreate">Add task</button></div></div><div class="kanban">${cols}</div>${this.genericGrid()}`; }

    identityEndpointKind(){ const ep=this.activeEndpoint(); const label=String(ep.label || '').toLowerCase(); if(label.includes('group')) return 'groups'; if(label.includes('role')) return 'roles'; if(label.includes('user')) return 'users'; return 'status'; }
    identityUrl(kind,id,action){ const realm=encodeURIComponent(this.state.realm || 'nebulaops'); let base=`/api/identity/realms/${realm}/${kind}`; if(kind==='status') base=`/api/identity/realms/${realm}/status`; if(id) base += `/${encodeURIComponent(id)}`; if(action) base += `/${action}`; return base; }
    async identityCreate(){ const kind=this.identityEndpointKind(); if(!['users','groups','roles'].includes(kind)){ this.toast('Select Users, Groups or Roles to create an identity resource'); return; } const root=this.shadowRoot; let body={}; if(kind==='users'){ const username=root.getElementById('identityUsername')?.value?.trim(); if(!username){ this.toast('Username is required'); return; } body={username,email:root.getElementById('identityEmail')?.value?.trim() || username,firstName:root.getElementById('identityFirstName')?.value?.trim() || '',lastName:root.getElementById('identityLastName')?.value?.trim() || '',enabled:true}; } else if(kind==='groups'){ const name=root.getElementById('identityName')?.value?.trim(); if(!name){ this.toast('Group name is required'); return; } body={name}; } else { const name=root.getElementById('identityName')?.value?.trim(); if(!name){ this.toast('Role name is required'); return; } body={name,description:root.getElementById('identityDescription')?.value?.trim() || ''}; } try{ const res=await this.request(this.identityUrl(kind),{method:'POST',body:JSON.stringify(body)}); this.showDetail(`${kind.slice(0,-1)} created`,res); await this.refresh(); }catch(e){ this.toast(`Identity create failed: ${human(e.body?.error || e.message)}`); } }
    async identityDisable(kind,id){ if(!id || !confirm(`Disable ${kind.slice(0,-1)} ${id}?`)) return; try{ const body=await this.request(this.identityUrl(kind,id,'disable'),{method:'PATCH',body:JSON.stringify({})}); this.showDetail(`${kind.slice(0,-1)} disabled`,body); await this.refresh(); }catch(e){ this.toast('Disable failed'); } }
    async identityEdit(kind,item){ const id=item.id || item.name; if(!id) return; let body={...item}; if(kind==='users'){ const email=prompt('Email', item.email || ''); if(email===null) return; body.email=email; body.firstName=prompt('First name', item.firstName || '') || ''; body.lastName=prompt('Last name', item.lastName || '') || ''; body.enabled=item.enabled !== false; } else if(kind==='groups'){ const name=prompt('Group name', item.name || ''); if(name===null) return; body.name=name; } else { const description=prompt('Role description', item.description || ''); if(description===null) return; body.description=description; body.name=item.name; } try{ const res=await this.request(this.identityUrl(kind,id),{method:'PUT',body:JSON.stringify(body)}); this.showDetail(`${kind.slice(0,-1)} updated`,res); await this.refresh(); }catch(e){ this.toast('Update failed'); } }
    identityConsole(){ const ep=this.activeEndpoint(); const kind=this.identityEndpointKind(); const rows=this.filteredRows(ep); const createForm=kind==='users'?`<input id="identityUsername" class="input" placeholder="Username"><input id="identityEmail" class="input" placeholder="Email"><input id="identityFirstName" class="input" placeholder="First name"><input id="identityLastName" class="input" placeholder="Last name">`:`<input id="identityName" class="input" placeholder="${kind==='groups'?'Group name':'Role name'}">${kind==='roles'?'<input id="identityDescription" class="input" placeholder="Description">':''}`; const table=rows.length?`<div class="table-wrap"><table class="table"><thead><tr><th>ID</th><th>Name</th><th>Email / Description</th><th>Status</th><th>Actions</th></tr></thead><tbody>${rows.map((r,i)=>{ const id=r.id || r.name || i; const name=r.username || r.name || r.firstName || id; const detail=r.email || r.description || r.path || '—'; const disabled=(r.enabled===false) || (r.attributes && String(r.attributes.disabled).includes('true')); return `<tr><td class="mono">${esc(id)}</td><td><strong>${esc(name)}</strong><div class="muted small">${esc(r.firstName || '')} ${esc(r.lastName || '')}</div></td><td>${esc(detail)}</td><td><span class="pill ${disabled?'err':'ok'}">${disabled?'disabled':'enabled'}</span></td><td>${['users','groups','roles'].includes(kind)?`<div class="inline-actions"><button class="btn" data-identity-edit="${esc(kind)}" data-identity-id="${esc(id)}">Edit</button><button class="btn danger" data-identity-disable="${esc(kind)}" data-identity-id="${esc(id)}">Disable</button></div>`:''}</td></tr>`; }).join('')}</tbody></table></div>`:`<div class="empty">No rows returned by this Keycloak endpoint.</div>`; return `<div class="panel"><div class="panel-head"><div><div class="panel-title">Keycloak realm identity administration</div><div class="muted small">Users, groups and roles are loaded from Keycloak; Redis cache status is displayed by the cache endpoint.</div></div><div class="toolbar"><input id="identityRealm" class="input" value="${esc(this.state.realm || 'nebulaops')}" placeholder="Realm"><button class="btn" id="identityRealmLoad">Load realm</button></div></div><div class="form-grid">${createForm}<button id="identityCreate" class="btn primary">Add ${['users','groups','roles'].includes(kind)?kind.slice(0,-1):'resource'}</button></div></div><div class="grid"><aside class="side"><h3>Keycloak resources</h3>${(this.state.endpoints.length?this.state.endpoints:CFG.config.endpoints||[]).map((e,i)=>`<button class="navbtn ${i===this.state.active?'active':''}" data-nav="${i}"><span>${esc(e.label)}</span><span class="count">${(e.rows||[]).length||0}</span></button>`).join('')}</aside><main class="content"><div class="panel"><div class="panel-head"><div><div class="panel-title">${esc(ep.label || kind)}</div><div class="muted small mono">${esc(ep.url || '')}</div></div><div class="toolbar"><input class="input" id="filter" placeholder="Filter..." value="${esc(this.state.filter)}"><span class="status ${ep.ok?'ok':'err'}">${ep.ok?'LIVE':'ERROR'}</span></div></div>${table}<div style="padding:16px"><details><summary class="muted small">Raw endpoint response</summary><pre class="json">${esc(JSON.stringify(ep.body||{},null,2))}</pre></details></div></div>${this.detailPanel()}</main></div>`; }

    platformCatalogConsole(){ const ep=this.activeEndpoint(); const rows=this.filteredRows(ep); const table=rows.length?`<div class="table-wrap"><table class="table"><thead><tr><th>Type</th><th>Component</th><th>Owner / Group</th><th>Health</th><th>Runtime</th><th>Dependencies</th><th>Links</th></tr></thead><tbody>${rows.map(r=>{ const links=r.links||{}; const deps=Array.isArray(r.dependencies)?r.dependencies:[]; const status=/up|live|200|running/i.test(String(r.state||r.status||''))?'ok':(/unavailable|down|error|not_found|0/i.test(String(r.state||r.status||''))?'err':'warn'); const docker=r.dockerRuntime||{}; const k8s=r.kubernetesRuntime||{}; return `<tr><td><span class="pill">${esc(r.componentType||ep.kind||'component')}</span></td><td><strong>${esc(r.name||r.id||'')}</strong><div class="mono small">${esc(r.id||'')}</div><div class="muted small">${esc(r.route||r.baseUrl||'')}</div></td><td>${esc(r.owner||'')}<div class="muted small">${esc(r.serviceGroup||'')}</div></td><td><span class="pill ${status}">${esc(r.state||r.status||'UNKNOWN')}</span><div class="muted small">${esc(r.lastProbe?.durationMs ?? '')} ms</div></td><td><div class="small">Docker: ${esc(docker.state||'not mapped')}</div><div class="small">K8s: ${esc(k8s.state||'not mapped')}</div></td><td>${deps.map(d=>`<span class="pill">${esc(d)}</span>`).join(' ') || '<span class="muted small">none</span>'}</td><td><div class="inline-actions">${Object.entries(links).map(([k,v])=>`<a class="btn" href="${esc(v)}" target="_blank" rel="noreferrer">${esc(k)}</a>`).join('')}</div></td></tr>`; }).join('')}</tbody></table></div>`:`<div class="empty">No catalog records returned by the service registry endpoint.</div>`; return `<div class="panel"><div class="panel-head"><div><div class="panel-title">Runtime service registry</div><div class="muted small">Catalog data is loaded from /api/platform/catalog. The shell does not duplicate service rows; unavailable probes remain explicit.</div></div><div class="toolbar"><input class="input" id="filter" placeholder="Filter components, owners, endpoints..." value="${esc(this.state.filter)}"><span class="status ${ep.live?'ok':'warn'}">${ep.live?'LIVE':'UNAVAILABLE'}</span></div></div>${table}<div style="padding:16px"><details><summary class="muted small">Raw catalog response</summary><pre class="json">${esc(JSON.stringify(ep.body||{},null,2))}</pre></details></div></div>${this.genericGrid()}`; }

    observabilityConsole(){ const ep=this.activeEndpoint(); const rows=this.filteredRows(ep); const isTimeline=/incident timeline/i.test(ep.label||''); const table=rows.length?`<div class="table-wrap"><table class="table"><thead><tr><th>Time</th><th>Source</th><th>Correlation</th><th>Status</th><th>Name</th><th>Details</th></tr></thead><tbody>${rows.map(r=>`<tr><td class="mono">${esc(r.timestamp || r.time || r.createdAt || '')}</td><td>${esc(r.source || ep.kind || '')}</td><td class="mono">${esc(r.correlationId || '')}</td><td><span class="pill ${/error|warn|fail|blocked/i.test(String(r.status))?'err':'ok'}">${esc(r.status || 'event')}</span></td><td>${esc(r.name || r.title || r.message || '')}</td><td>${esc(human(r.details || r.payload || r.value || ''))}</td></tr>`).join('')}</tbody></table></div>`:`<div class="empty">No observability rows returned by this live source.</div>`; return `<div class="panel"><div class="panel-head"><div><div class="panel-title">${isTimeline?'Incident timeline':'Runtime observability'}</div><div class="muted small">Prometheus, Loki, Tempo, RabbitMQ, audit and notification sources are correlated through correlation id when present.</div></div><span class="status ${ep.live?'ok':'warn'}">${ep.live?'LIVE':'UNAVAILABLE'}</span></div>${table}</div>${this.genericGrid()}`; }


    async incidentAnalyze(){ const affectedService=prompt('Affected service for live RCA analysis','gateway-service'); if(affectedService===null) return; const namespace=prompt('Kubernetes namespace','default'); if(namespace===null) return; try{ const body=await this.request('/api/incidents/command-center/incidents/analyze',{method:'POST',body:JSON.stringify({affectedService,namespace,source:'incident-command-center'})}); this.showDetail('Incident live RCA analysis',body); await this.refresh(); }catch(e){ this.toast(`Incident analysis failed: ${human(e.body?.error || e.message)}`); } }
    async incidentRunbook(id){ if(!id) return; try{ const body=await this.request(`/api/incidents/command-center/incidents/${encodeURIComponent(id)}/runbook`,{method:'POST',body:JSON.stringify({})}); this.showDetail(`Runbook for ${id}`,body); }catch(e){ this.toast('Runbook unavailable'); } }
    async incidentCreateTask(id){ if(!id) return; const title=prompt('Task title',`Incident follow-up ${id}`); if(title===null) return; try{ const body=await this.request(`/api/incidents/command-center/incidents/${encodeURIComponent(id)}/tasks`,{method:'POST',body:JSON.stringify({title,priority:'HIGH',projectId:'sre',labels:['incident','sre','command-center']})}); this.showDetail(`Task created for ${id}`,body); await this.refresh(); }catch(e){ this.toast(`Task creation failed: ${human(e.body?.error || e.message)}`); } }
    async incidentExport(){ const ep=this.activeEndpoint(); const q=prompt('Optional correlation id for report filter',''); if(q===null) return; try{ const url='/api/incidents/command-center/export?limit=200'+(q.trim()?`&correlationId=${encodeURIComponent(q.trim())}`:''); const body=await this.request(url); this.showDetail(body.filename || 'Incident technical report', body); }catch(e){ this.toast('Report export failed'); } }
    incidentStatusClass(v){ return /critical|sev1|error|down|blocked|failed/i.test(String(v))?'err':/high|warn|degraded|open|pending/i.test(String(v))?'warn':'ok'; }
    incidentConsole(){ const ep=this.activeEndpoint(); const rows=this.filteredRows(ep); const isTimeline=/timeline/i.test(ep.label||''); const isServices=/service/i.test(ep.label||''); const nav=(this.state.endpoints.length?this.state.endpoints:CFG.config.endpoints||[]).map((e,i)=>`<button class="navbtn ${i===this.state.active?'active':''}" data-nav="${i}"><span>${esc(e.label)}</span><span class="count">${(e.rows||[]).length||0}</span></button>`).join(''); const actionBar=`<div class="toolbar"><button class="btn primary" id="incidentAnalyze">Analyze live signals</button><button class="btn" id="incidentExport">Export technical report</button><a class="btn" href="/remotes/observability/" target="_blank" rel="noreferrer">Observability</a><a class="btn" href="/remotes/task-management/" target="_blank" rel="noreferrer">Tasks</a><a class="btn" href="/remotes/release-center/" target="_blank" rel="noreferrer">Release rollback</a><a class="btn" href="/remotes/openlens-kubernetes/" target="_blank" rel="noreferrer">Kubernetes pods/logs</a></div>`; let table=''; if(rows.length){ if(isTimeline){ table=`<div class="table-wrap"><table class="table"><thead><tr><th>Time</th><th>Source</th><th>Correlation</th><th>Status</th><th>Event</th><th>Details</th></tr></thead><tbody>${rows.map(r=>`<tr><td class="mono">${esc(r.timestamp || r.time || r.createdAt || '')}</td><td>${esc(r.source || '')}</td><td class="mono">${esc(r.correlationId || '')}</td><td><span class="pill ${this.incidentStatusClass(r.status || r.severity)}">${esc(r.status || r.severity || 'event')}</span></td><td><strong>${esc(r.name || r.title || r.message || r.type || '')}</strong></td><td>${esc(human(r.details || r.payload || r.value || ''))}</td></tr>`).join('')}</tbody></table></div>`; } else if(isServices){ table=`<div class="table-wrap"><table class="table"><thead><tr><th>Service</th><th>Endpoint</th><th>Health</th><th>Duration</th><th>Error / Probe</th></tr></thead><tbody>${rows.map(r=>`<tr><td><strong>${esc(r.name || r.id || r.service || '')}</strong></td><td class="mono">${esc(r.endpoint || r.url || '')}</td><td><span class="pill ${r.live?'ok':'err'}">${esc(r.live?'UP':'DOWN')}</span></td><td>${esc(r.durationMs ?? r.duration ?? '')} ms</td><td>${esc(r.error || human(r.body || r.probe || ''))}</td></tr>`).join('')}</tbody></table></div>`; } else { table=`<div class="table-wrap"><table class="table"><thead><tr><th>Incident</th><th>Severity</th><th>Service</th><th>Status</th><th>Correlation</th><th>Root cause / Summary</th><th>Actions</th></tr></thead><tbody>${rows.map((r,i)=>{ const id=r.id || r.incidentId || `row-${i}`; const links=r.links||{}; return `<tr><td><strong>${esc(r.name || r.title || r.message || id)}</strong><div class="mono small">${esc(id)}</div><div class="muted small">${esc(r.source || '')}</div></td><td><span class="pill ${this.incidentStatusClass(r.severity || r.priority)}">${esc(r.severity || r.priority || 'UNKNOWN')}</span></td><td>${esc(r.affectedService || r.service || r.target || '')}</td><td><span class="pill ${this.incidentStatusClass(r.status || r.state)}">${esc(r.status || r.state || 'OPEN')}</span></td><td class="mono">${esc(r.correlationId || '')}</td><td>${esc(r.rootCause || r.summary || r.description || '')}</td><td><div class="inline-actions"><button class="btn" data-incident-runbook="${esc(id)}">Runbook</button><button class="btn" data-incident-task="${esc(id)}">Create task</button>${Object.entries(links).map(([k,v])=>`<a class="btn" href="${esc(v)}" target="_blank" rel="noreferrer">${esc(k)}</a>`).join('')}</div></td></tr>`; }).join('')}</tbody></table></div>`; } } else { table=`<div class="empty">No real incident records returned by this live source.<br><span class="small mono">${esc(ep.url || '')}</span><br><span class="small">Use “Analyze live signals” with an affected service to create an RCA from live Kubernetes, Loki and Prometheus data.</span></div>`; } return `<div class="panel"><div class="panel-head"><div><div class="panel-title">Incident Command Center</div><div class="muted small">Unifies Observability, AI Ops, Notifications, Audit, Task, Runbook, Release and Kubernetes signals. No incident rows are generated in the frontend.</div></div>${actionBar}</div></div><div class="grid"><aside class="side"><h3>Incident sources</h3>${nav}</aside><main class="content"><div class="panel"><div class="panel-head"><div><div class="panel-title">${esc(ep.label || 'Incident source')}</div><div class="muted small mono">${esc(ep.url || '')}</div></div><div class="toolbar"><input class="input" id="filter" placeholder="Filter incidents, services, correlation ids..." value="${esc(this.state.filter)}"><span class="status ${ep.live?'ok':ep.ok?'warn':'err'}">${ep.live?'LIVE':ep.ok?'EMPTY/DEGRADED':'ERROR'}</span></div></div>${table}<div style="padding:16px"><details><summary class="muted small">Raw source response</summary><pre class="json">${esc(JSON.stringify(ep.body||{},null,2))}</pre></details></div></div>${this.detailPanel()}</main></div>`; }

    summaryCards(){ const c=this.counts(); return `<div class="cards"><div class="card"><div class="metric-label">Endpoint checks</div><div class="metric-value">${c.endpoints}</div></div><div class="card"><div class="metric-label">Live sources</div><div class="metric-value">${c.live}</div></div><div class="card"><div class="metric-label">Records returned</div><div class="metric-value">${c.total}</div></div><div class="card"><div class="metric-label">Unavailable sources</div><div class="metric-value">${c.errors}</div></div></div>`; }
    render(){ const special=(CFG.id==='docker-desktop'||CFG.id==='docker-storage-cleanup')?this.dockerConsole():CFG.id==='openlens-kubernetes'?this.openlensConsole():CFG.id==='task-management'?this.taskBoard():CFG.id==='identity-admin'?this.identityConsole():CFG.id==='observability'?this.observabilityConsole():CFG.id==='platform-catalog'?this.platformCatalogConsole():CFG.id==='incident-command-center'?this.incidentConsole():null; const extra=CFG.id==='policy-center'?'<button class="btn" id="evalPolicy">Evaluate current runtime</button>':''; this.shadowRoot.innerHTML=`<style>${css}</style><section class="mfe"><div class="hero"><div class="hero-left"><div class="icon">${iconById[CFG.id] || '◆'}</div><div><div class="eyebrow">${esc(CFG.scope)}</div><h1 class="title">${esc(CFG.title)}</h1><div class="subtitle">Live-only remote module. The view is populated from NebulaOps gateway and service endpoints; unavailable sources show explicit runtime errors instead of fallback data.</div></div></div><div class="actions"><button class="btn primary" id="refresh">${this.state.loading?'Loading...':'Refresh live data'}</button>${extra}<a class="btn" href="/remoteEntry.js?v=${VERSION}" target="_blank" rel="noreferrer">Runtime bundle</a></div></div>${this.summaryCards()}${special || this.genericGrid()}${this.state.toast?`<div class="toast">${esc(this.state.toast)}</div>`:''}</section>`; this.bind(); }
    bind(){ const root=this.shadowRoot; root.getElementById('refresh')?.addEventListener('click',()=>this.refresh()); root.getElementById('evalPolicy')?.addEventListener('click',()=>this.evaluatePolicy()); root.getElementById('closeDetail')?.addEventListener('click',()=>{ this.state.detail=null; this.render(); }); root.querySelectorAll('[data-nav]').forEach(b=>b.addEventListener('click',()=>this.setActive(Number(b.getAttribute('data-nav'))))); root.getElementById('filter')?.addEventListener('input',e=>this.setFilter(e.target.value)); root.querySelectorAll('[data-prune]').forEach(b=>b.addEventListener('click',()=>this.dockerPrune(b.getAttribute('data-prune')))); root.querySelectorAll('[data-yaml]').forEach(b=>b.addEventListener('click',()=>this.yamlAction(b.getAttribute('data-yaml')))); root.querySelectorAll('[data-kube-cluster]').forEach(b=>b.addEventListener('click',()=>{ this.state.clusterId=b.getAttribute('data-kube-cluster') || ''; this.state.active=1; this.refresh(); })); root.getElementById('kubeconfigSave')?.addEventListener('click',()=>this.saveKubeconfig()); root.querySelectorAll('[data-kube-delete]').forEach(b=>b.addEventListener('click',()=>this.deleteKubeconfig(b.getAttribute('data-kube-delete')))); root.querySelectorAll('[data-kube-probe]').forEach(b=>b.addEventListener('click',()=>this.probeKubeconfig(b.getAttribute('data-kube-probe')))); root.querySelectorAll('[data-act]').forEach(btn=>btn.addEventListener('click',()=>{ const [area,act]=btn.getAttribute('data-act').split(':'); const key=btn.getAttribute('data-key'); const ep=this.activeEndpoint(); const item=(ep.rows||[]).find(r=>String(meta(r).id||meta(r).name)===String(key)) || {}; if(area==='docker') this.dockerAction(act,item); if(area==='k8s') this.k8sAction(act,item); if(area==='delivery') this.deliveryAction(act,item); if(area==='release'){ if(act==='timeline') this.releaseTimeline(item); else this.releaseAction(act,item); } })); root.querySelectorAll('[data-approval-act]').forEach(btn=>btn.addEventListener('click',()=>this.approvalAction(btn.getAttribute('data-approval-id'),btn.getAttribute('data-approval-act')))); root.getElementById('taskCreate')?.addEventListener('click',()=>this.createTask()); root.getElementById('taskSearch')?.addEventListener('input',e=>{ this.state.taskFilter.q=e.target.value; this.render(); }); root.getElementById('taskAssigneeFilter')?.addEventListener('input',e=>{ this.state.taskFilter.assignee=e.target.value; this.render(); }); root.getElementById('taskPriorityFilter')?.addEventListener('change',e=>{ this.state.taskFilter.priority=e.target.value; this.render(); }); root.querySelectorAll('[data-task-delete]').forEach(b=>b.addEventListener('click',()=>this.deleteTask(b.getAttribute('data-task-delete')))); root.querySelectorAll('[data-task-move]').forEach(b=>b.addEventListener('click',()=>this.moveTask(b.getAttribute('data-task-id'),b.getAttribute('data-task-move')))); root.querySelectorAll('[data-drag-task]').forEach(card=>{ card.addEventListener('dragstart',e=>{ this.state.dragTaskId=card.getAttribute('data-drag-task') || ''; e.dataTransfer?.setData('text/plain',this.state.dragTaskId); }); }); root.querySelectorAll('[data-drop-status]').forEach(col=>{ col.addEventListener('dragover',e=>{ e.preventDefault(); col.classList.add('drag-over'); }); col.addEventListener('dragleave',()=>col.classList.remove('drag-over')); col.addEventListener('drop',e=>{ e.preventDefault(); col.classList.remove('drag-over'); const id=e.dataTransfer?.getData('text/plain') || this.state.dragTaskId; this.moveTask(id,col.getAttribute('data-drop-status')); }); }); root.getElementById('incidentAnalyze')?.addEventListener('click',()=>this.incidentAnalyze()); root.getElementById('incidentExport')?.addEventListener('click',()=>this.incidentExport()); root.querySelectorAll('[data-incident-runbook]').forEach(b=>b.addEventListener('click',()=>this.incidentRunbook(b.getAttribute('data-incident-runbook')))); root.querySelectorAll('[data-incident-task]').forEach(b=>b.addEventListener('click',()=>this.incidentCreateTask(b.getAttribute('data-incident-task')))); root.getElementById('identityCreate')?.addEventListener('click',()=>this.identityCreate()); root.getElementById('identityRealmLoad')?.addEventListener('click',()=>{ this.state.realm=root.getElementById('identityRealm')?.value?.trim() || 'nebulaops'; this.refresh(); }); root.querySelectorAll('[data-identity-disable]').forEach(b=>b.addEventListener('click',()=>this.identityDisable(b.getAttribute('data-identity-disable'),b.getAttribute('data-identity-id')))); root.querySelectorAll('[data-identity-edit]').forEach(b=>b.addEventListener('click',()=>{ const kind=b.getAttribute('data-identity-edit'); const id=b.getAttribute('data-identity-id'); const ep=this.activeEndpoint(); const item=(ep.rows||[]).find(r=>String(r.id||r.name)===String(id)); this.identityEdit(kind,item||{}); })); }
  }
  if(!customElements.get(CFG.tag)) customElements.define(CFG.tag, LiveRemote);
})();
