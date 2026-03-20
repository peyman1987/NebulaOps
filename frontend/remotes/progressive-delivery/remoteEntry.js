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


/* NebulaOps v23.1.0-live-real-data-progressive-delivery · live progressive delivery runtime. It renders only responses returned by NebulaOps APIs. */
(function(){
  'use strict';
  const TAG="nebulaops-mfe-progressive-delivery";
  const TITLE="Progressive Delivery Center";
  const SCOPE="Release · Argo Rollouts · Canary/Blue-Green";
  const ENDPOINTS=[{"label":"Rollouts","url":"/api/progressive-delivery/rollouts?namespace=all","kind":"rollout","itemsPath":"items"},{"label":"Argo CD applications","url":"/api/progressive-delivery/applications","kind":"application","itemsPath":"items"},{"label":"Canary / Blue-Green overview","url":"/api/progressive-delivery/overview?namespace=all","kind":"overview","itemsPath":"items"},{"label":"Analysis runs","url":"/api/progressive-delivery/analysis-runs?namespace=all","kind":"analysisRun","itemsPath":"items"},{"label":"Experiments","url":"/api/progressive-delivery/experiments?namespace=all","kind":"experiment","itemsPath":"items"},{"label":"Progressive delivery audit","url":"/api/audit/events?type=PROGRESSIVE&limit=100","kind":"audit","itemsPath":"items"}];
  const TOKEN_KEY='nebulaops.v23_1.jwt';
  const css=':host{display:block;color:#eaf4ff;font-family:Inter,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}.mfe{min-height:640px;padding:28px;background:radial-gradient(circle at 0 0,rgba(56,189,248,.18),transparent 34%),linear-gradient(135deg,#07111f,#0b1026 62%,#11152d)}.hero{display:flex;justify-content:space-between;align-items:flex-start;gap:18px;flex-wrap:wrap;margin-bottom:18px}.icon{width:70px;height:70px;border-radius:22px;display:grid;place-items:center;background:linear-gradient(135deg,#38bdf8,#8b5cf6);font-size:34px;box-shadow:0 20px 50px rgba(0,0,0,.35)}.hero-left{display:flex;gap:16px;align-items:center}.eyebrow{text-transform:uppercase;letter-spacing:.2em;color:#67e8f9;font-weight:900;font-size:12px}.title{font-size:34px;font-weight:950;line-height:1;margin:5px 0}.subtitle{color:#a8b7d8;max-width:760px}.btn{border:1px solid rgba(125,211,252,.28);background:rgba(14,24,48,.82);color:#eaf4ff;border-radius:12px;padding:10px 14px;font-weight:850;cursor:pointer}.btn.primary{border:0;background:linear-gradient(135deg,#0ea5e9,#7c3aed)}.btn.warn{border-color:rgba(245,158,11,.45);color:#fde68a}.btn.danger{border-color:rgba(248,113,113,.45);color:#fecaca}.actions{display:flex;gap:10px;flex-wrap:wrap}.cards{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin:18px 0}.card{border:1px solid rgba(148,163,184,.18);background:linear-gradient(180deg,rgba(16,26,55,.88),rgba(9,16,36,.82));border-radius:20px;padding:16px}.metric-label{font-size:12px;color:#9fb0d4}.metric-value{font-size:28px;font-weight:950;margin-top:6px}.layout{display:grid;grid-template-columns:270px 1fr;gap:18px}.side{border:1px solid rgba(148,163,184,.18);background:rgba(8,15,34,.72);border-radius:22px;padding:14px;align-self:start;position:sticky;top:12px}.side h3{font-size:12px;text-transform:uppercase;letter-spacing:.18em;color:#78e0ff;margin:4px 8px 12px}.navbtn{width:100%;text-align:left;display:flex;justify-content:space-between;gap:10px;margin:6px 0;border:1px solid transparent;background:transparent;color:#b7c6e6;border-radius:14px;padding:11px 12px;cursor:pointer;font-weight:850}.navbtn.active,.navbtn:hover{background:rgba(14,165,233,.13);border-color:rgba(14,165,233,.35);color:#fff}.panel{border:1px solid rgba(56,189,248,.24);background:rgba(4,10,24,.72);border-radius:22px;overflow:hidden}.panel-head{display:flex;justify-content:space-between;gap:12px;align-items:center;padding:15px 18px;border-bottom:1px solid rgba(148,163,184,.16);background:rgba(15,23,42,.65)}.panel-title{font-size:15px;font-weight:900}.toolbar{display:flex;gap:10px;align-items:center;flex-wrap:wrap}.input{background:rgba(15,23,42,.8);border:1px solid rgba(148,163,184,.2);border-radius:12px;color:#eaf4ff;padding:10px 12px;min-width:180px}.status{font-size:12px;border:1px solid rgba(148,163,184,.24);border-radius:999px;padding:5px 9px;color:#b7c6e6}.status.ok{color:#bbf7d0;border-color:rgba(34,197,94,.45);background:rgba(22,163,74,.12)}.status.err{color:#fecaca;border-color:rgba(248,113,113,.45);background:rgba(239,68,68,.12)}.table-wrap{overflow:auto}.table{width:100%;min-width:900px;border-collapse:collapse}.table th{text-align:left;color:#7dd3fc;text-transform:uppercase;letter-spacing:.14em;font-size:11px;background:rgba(15,23,42,.55)}.table th,.table td{padding:13px 14px;border-bottom:1px solid rgba(148,163,184,.12);vertical-align:top}.table td{font-size:13px;color:#dce7ff}.mono{font-family:SFMono-Regular,Consolas,monospace;font-size:12px;color:#a7f3d0}.pill{display:inline-flex;align-items:center;border:1px solid rgba(148,163,184,.22);border-radius:999px;padding:4px 8px;font-size:11px;color:#b7c6e6}.pill.ok{color:#bbf7d0;border-color:rgba(34,197,94,.4)}.pill.warn{color:#fde68a;border-color:rgba(245,158,11,.45)}.pill.err{color:#fecaca;border-color:rgba(248,113,113,.45)}.empty{padding:32px;color:#a8b7d8;text-align:center}.json{max-height:430px;overflow:auto;white-space:pre-wrap;font-family:SFMono-Regular,Consolas,monospace;font-size:12px;color:#c7d2fe;background:rgba(2,6,23,.6);border-radius:16px;padding:14px}.toast{position:fixed;right:24px;bottom:24px;background:#0f172a;border:1px solid rgba(125,211,252,.35);border-radius:14px;padding:12px 16px;box-shadow:0 18px 40px rgba(0,0,0,.35);z-index:50}.muted{color:#8ea2c9}.small{font-size:12px}@media(max-width:980px){.layout{grid-template-columns:1fr}.side{position:static}.cards{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:640px){.cards{grid-template-columns:1fr}.title{font-size:28px}}';
  function token(){try{return window.__NEBULAOPS_ACCESS_TOKEN__ || localStorage.getItem(TOKEN_KEY) || '';}catch(_){return window.__NEBULAOPS_ACCESS_TOKEN__ || '';}}
  function esc(v){return String(v ?? '').replace(/[&<>"']/g,function(ch){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch];});}
  function getPath(obj,path){if(!path)return obj;return String(path).split('.').reduce(function(acc,key){return acc && acc[key]!==undefined ? acc[key] : undefined;}, obj);}
  function rows(payload, path){const selected=getPath(payload,path);const val=selected!==undefined?selected:payload;if(Array.isArray(val))return val;if(val&&Array.isArray(val.items))return val.items;if(val&&val.data&&Array.isArray(val.data.items))return val.data.items;if(val&&typeof val==='object')return Object.entries(val).filter(function(pair){return !['data','items','toolStatus','live','realDataOnly'].includes(pair[0]);}).map(function(pair){return {key:pair[0],value:pair[1]};});return [];}
  function meta(item){const m=item && item.metadata ? item.metadata : {}; const s=item && item.status ? item.status : {}; const spec=item && item.spec ? item.spec : {}; return {name:m.name || item.name || item.key || '—', namespace:m.namespace || item.namespace || '—', phase:s.phase || s.status || item.status || item.value || '—', strategy: spec.strategy ? Object.keys(spec.strategy).join(', ') : (item.strategy || '—'), revision:s.currentPodHash || s.currentStepHash || s.sync?.revision || item.revision || '—', health:s.health?.status || s.healthStatus || item.health || '—'};}
  function authHeaders(){const h={'Accept':'application/json'}; const t=token(); if(t)h.Authorization='Bearer '+t; return h;}
  class ProgressiveDelivery extends HTMLElement{
    constructor(){super();this.attachShadow({mode:'open'});this.state={active:0,namespace:'all',data:[],loading:false,toast:''};}
    connectedCallback(){this.render();this.refresh();}
    async call(url, options){const res=await fetch(url, Object.assign({headers:authHeaders()}, options||{}));let body={};try{body=await res.json();}catch(_){body={status:res.status};}if(!res.ok){body.live=false;body.error=body.error||('HTTP '+res.status);}return body;}
    async refresh(){this.state.loading=true;this.render();const ns=encodeURIComponent(this.state.namespace||'all');const endpoints=ENDPOINTS.map(function(e){return Object.assign({}, e, {url:e.url.replace('namespace=all','namespace='+ns)});});const loaded=[];for(const ep of endpoints){try{const payload=await this.call(ep.url);loaded.push(Object.assign({}, ep, {payload:payload, rows:rows(payload, ep.itemsPath)}));}catch(err){loaded.push(Object.assign({}, ep, {payload:{live:false,error:String(err),realDataOnly:true}, rows:[]}));}}this.state.data=loaded;this.state.loading=false;this.render();}
    async action(kind, ns, name){const encodedNs=encodeURIComponent(ns||'default');const encodedName=encodeURIComponent(name||'');const map={promote:'/api/progressive-delivery/rollouts/'+encodedNs+'/'+encodedName+'/promote',promoteFull:'/api/progressive-delivery/rollouts/'+encodedNs+'/'+encodedName+'/promote?full=true',abort:'/api/progressive-delivery/rollouts/'+encodedNs+'/'+encodedName+'/abort',restart:'/api/progressive-delivery/rollouts/'+encodedNs+'/'+encodedName+'/restart'};this.state.toast='Sending '+kind+' to '+name;this.render();const payload=await this.call(map[kind],{method:'POST',headers:Object.assign({'Content-Type':'application/json'},authHeaders()),body:'{}'});this.state.toast=payload.live ? kind+' completed for '+name : kind+' returned a runtime error';this.render();setTimeout(()=>{this.state.toast='';this.refresh();},1200);}
    async sync(app){this.state.toast='Syncing '+app;this.render();const payload=await this.call('/api/progressive-delivery/applications/'+encodeURIComponent(app)+'/sync',{method:'POST',headers:Object.assign({'Content-Type':'application/json'},authHeaders()),body:'{}'});this.state.toast=payload.live ? 'Argo CD sync completed for '+app : 'Argo CD sync returned a runtime error';this.render();setTimeout(()=>{this.state.toast='';this.refresh();},1200);}
    summary(){const overview=this.state.data[0]?.payload || {};const vals=[['Rollouts',overview.rolloutCount],['Applications',overview.applicationCount],['Analysis Runs',overview.analysisRunCount],['Experiments',overview.experimentCount]];return '<div class="cards">'+vals.map(function(v){return '<div class="card"><div class="metric-label">'+esc(v[0])+'</div><div class="metric-value">'+esc(v[1] ?? '—')+'</div></div>';}).join('')+'</div>';}
    panel(){const ep=this.state.data[this.state.active] || {};const items=ep.rows || [];const isRollout=ep.kind==='rollout';const isApp=ep.kind==='application';const live=ep.payload && ep.payload.live;let body='';if(items.length===0){body='<div class="empty">No runtime records returned by '+esc(ep.label || 'this endpoint')+'. Configure Argo Rollouts, Argo CD or Kubernetes access and refresh.</div>';}else{body='<div class="table-wrap"><table class="table"><thead><tr><th>Name</th><th>Namespace</th><th>Status</th><th>Strategy</th><th>Revision</th><th>Health</th><th>Actions</th></tr></thead><tbody>'+items.map((item)=>{const m=meta(item);let actions='<span class="muted small">read only</span>';if(isRollout && m.name!=='—' && m.namespace!=='—'){actions='<div class="actions"><button class="btn" data-action="promote" data-ns="'+esc(m.namespace)+'" data-name="'+esc(m.name)+'">Promote</button><button class="btn warn" data-action="promoteFull" data-ns="'+esc(m.namespace)+'" data-name="'+esc(m.name)+'">Promote full</button><button class="btn danger" data-action="abort" data-ns="'+esc(m.namespace)+'" data-name="'+esc(m.name)+'">Abort</button><button class="btn" data-action="restart" data-ns="'+esc(m.namespace)+'" data-name="'+esc(m.name)+'">Restart</button></div>';}if(isApp && m.name!=='—'){actions='<button class="btn primary" data-sync="'+esc(m.name)+'">Sync</button>';}return '<tr><td><b>'+esc(m.name)+'</b></td><td><span class="mono">'+esc(m.namespace)+'</span></td><td><span class="pill '+(String(m.phase).match(/Healthy|Succeeded|Synced|Progressing/i)?'ok':String(m.phase).match(/Error|Failed|Degraded|Aborted/i)?'err':'warn')+'">'+esc(m.phase)+'</span></td><td>'+esc(m.strategy)+'</td><td><span class="mono">'+esc(m.revision)+'</span></td><td>'+esc(m.health)+'</td><td>'+actions+'</td></tr>';}).join('')+'</tbody></table></div>';}
      const raw='<details style="padding:14px 18px"><summary class="muted">Raw runtime response</summary><pre class="json">'+esc(JSON.stringify(ep.payload || {}, null, 2))+'</pre></details>';
      return '<section class="panel"><div class="panel-head"><div><div class="panel-title">'+esc(ep.label || 'Endpoint')+'</div><div class="muted small">'+esc(ep.url || '')+'</div></div><div class="toolbar"><span class="status '+(live?'ok':'err')+'">'+(live?'LIVE':'UNAVAILABLE')+'</span><input class="input" id="namespace" value="'+esc(this.state.namespace)+'" placeholder="namespace or all"><button class="btn" id="loadNs">Load namespace</button></div></div>'+body+raw+'</section>';}
    render(){const nav=(this.state.data.length?this.state.data:ENDPOINTS).map((e,i)=>'<button class="navbtn '+(i===this.state.active?'active':'')+'" data-nav="'+i+'"><span>'+esc(e.label)+'</span><span>'+(e.rows?e.rows.length:0)+'</span></button>').join('');this.shadowRoot.innerHTML='<style>'+css+'</style><section class="mfe"><div class="hero"><div class="hero-left"><div class="icon">🚢</div><div><div class="eyebrow">'+esc(SCOPE)+'</div><h1 class="title">'+esc(TITLE)+'</h1><div class="subtitle">Runtime progressive delivery console for Argo Rollouts, Argo CD applications, canary steps, blue-green promotions, analysis runs and experiments. Empty states mean the live source has no records or is not reachable.</div></div></div><div class="actions"><button class="btn primary" id="refresh">'+(this.state.loading?'Loading...':'Refresh live data')+'</button><a class="btn" href="/api/progressive-delivery/overview?namespace=all" target="_blank" rel="noreferrer">Open API</a></div></div>'+this.summary()+'<div class="layout"><aside class="side"><h3>Runtime sources</h3>'+nav+'</aside><main>'+this.panel()+'</main></div>'+(this.state.toast?'<div class="toast">'+esc(this.state.toast)+'</div>':'')+'</section>';this.shadowRoot.getElementById('refresh')?.addEventListener('click',()=>this.refresh());this.shadowRoot.getElementById('loadNs')?.addEventListener('click',()=>{this.state.namespace=this.shadowRoot.getElementById('namespace')?.value?.trim()||'all';this.refresh();});this.shadowRoot.querySelectorAll('[data-nav]').forEach(b=>b.addEventListener('click',()=>{this.state.active=Number(b.getAttribute('data-nav'));this.render();}));this.shadowRoot.querySelectorAll('[data-action]').forEach(b=>b.addEventListener('click',()=>this.action(b.getAttribute('data-action'), b.getAttribute('data-ns'), b.getAttribute('data-name'))));this.shadowRoot.querySelectorAll('[data-sync]').forEach(b=>b.addEventListener('click',()=>this.sync(b.getAttribute('data-sync'))));}
  }
  if(!customElements.get(TAG)) customElements.define(TAG, ProgressiveDelivery);
})();