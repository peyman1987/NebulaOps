/* NebulaOps v22.3 auth bridge
 * Makes shell-loaded and standalone MFE requests share a valid Bearer token.
 * - Shell origin: reuses localStorage token or bootstraps dev admin token.
 * - Standalone MFE route (/remotes/<mfe>/): uses the same nebulaops.localhost origin and the shared shell token.
 * - Patches fetch/XMLHttpRequest so the first API request waits for token availability.
 * - Rewrites legacy relative runtime endpoints to the gateway API path.
 */
(function nebulaopsAuthBridge() {
  'use strict';
  var VERSION = 'v22.3.9-restore-ui-live-api';
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


(function(){
'use strict';
const CSS = `
:host{display:block;color:#eaf3ff;font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;--bg:#071024;--panel:#0d1831;--panel2:#111d3a;--line:#213654;--muted:#89a0c3;--accent:#34d3ff;--ok:#2dd4bf;--warn:#fbbf24;--bad:#fb7185;--violet:#8b5cf6}*{box-sizing:border-box}.mfe{min-height:100vh;background:radial-gradient(circle at 10% 0%,rgba(34,211,238,.18),transparent 30%),linear-gradient(135deg,#071024,#0a1024 58%,#121436);padding:34px}.top{display:flex;align-items:flex-start;justify-content:space-between;gap:24px;margin-bottom:24px}.title{display:flex;align-items:center;gap:18px}.icon{width:72px;height:72px;border-radius:24px;display:grid;place-items:center;background:linear-gradient(135deg,#1bb7ff,#8b5cf6);box-shadow:0 18px 50px rgba(0,0,0,.28);font-size:34px}.eyebrow{color:#79e7ff;font-weight:900;letter-spacing:.24em;text-transform:uppercase;font-size:12px}.h1{font-size:38px;line-height:1.04;margin:6px 0 6px;font-weight:1000;letter-spacing:-.04em}.sub{color:#b9c8e7;font-size:15px;max-width:900px;line-height:1.45}.top-actions{display:flex;gap:10px;flex-wrap:wrap;justify-content:flex-end}.btn{border:1px solid #2b4264;background:#111d37;color:#eaf3ff;border-radius:13px;padding:10px 14px;font-weight:850;cursor:pointer;transition:.15s}.btn:hover{transform:translateY(-1px);border-color:#45d8ff}.btn.primary{background:linear-gradient(135deg,#1fb6ff,#7c3aed);border:0}.btn.danger{border-color:#7f1d1d;color:#fecaca}.btn.warn{border-color:#a16207;color:#fde68a}.btn.ok{border-color:#0f766e;color:#99f6e4}.btn:disabled{opacity:.38;cursor:not-allowed;transform:none}.grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin-bottom:18px}.metric{background:linear-gradient(180deg,rgba(16,29,58,.94),rgba(11,20,43,.94));border:1px solid #263b61;border-radius:18px;padding:16px}.metric .label{color:#8fb0df;font-size:12px}.metric .value{font-size:28px;font-weight:1000;margin-top:7px}.shell{display:grid;grid-template-columns:290px minmax(0,1fr);gap:18px}.side{border:1px solid #263b61;border-radius:22px;background:rgba(9,18,40,.72);padding:14px;position:sticky;top:16px;align-self:start}.sectionTitle{color:#79e7ff;font-weight:950;letter-spacing:.14em;text-transform:uppercase;font-size:12px;margin:8px 8px 14px}.navbtn{width:100%;display:flex;align-items:center;justify-content:space-between;gap:10px;padding:13px 12px;margin:5px 0;border:1px solid transparent;border-radius:14px;background:transparent;color:#c8d7f3;text-align:left;font-weight:850;cursor:pointer}.navbtn:hover,.navbtn.active{background:#0d3554;border-color:#1d81aa;color:#fff}.pill{padding:3px 9px;border-radius:999px;background:#123a58;color:#7ee8ff;font-size:12px;border:1px solid #1d6d91}.content{border:1px solid #263b61;border-radius:22px;background:rgba(9,18,40,.72);overflow:hidden}.toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:16px;border-bottom:1px solid #263b61}.toolbar h2{margin:0;font-size:18px}.search{background:#0b142c;color:#eaf3ff;border:1px solid #263b61;border-radius:13px;padding:10px 13px;min-width:260px}.tableWrap{overflow:auto}.table{width:100%;border-collapse:collapse;min-width:820px}.table th{color:#7ee8ff;font-size:11px;text-transform:uppercase;letter-spacing:.12em;text-align:left;padding:12px 14px;border-bottom:1px solid #263b61}.table td{padding:12px 14px;border-bottom:1px solid rgba(38,59,97,.65);vertical-align:top}.rowTitle{font-weight:900;color:#fff}.muted{color:#8ea2c5;font-size:12px}.status{display:inline-flex;align-items:center;gap:6px;border-radius:999px;padding:4px 9px;font-size:12px;font-weight:850;background:#17233f;border:1px solid #2c4168}.status.ok{color:#99f6e4;border-color:#0f766e}.status.warn{color:#fde68a;border-color:#a16207}.status.bad{color:#fecaca;border-color:#7f1d1d}.actions{display:flex;gap:7px;flex-wrap:wrap}.small{font-size:12px;padding:7px 9px;border-radius:10px}.empty{padding:48px 20px;text-align:center;color:#acc0de}.empty b{display:block;color:#fff;margin-bottom:8px}.split{display:grid;grid-template-columns:1fr 1fr;gap:16px}.card{border:1px solid #263b61;border-radius:20px;background:rgba(10,19,42,.76);padding:16px}.card h3{margin:0 0 12px}.raw{white-space:pre-wrap;background:#050b1b;border:1px solid #22395e;border-radius:14px;padding:12px;max-height:300px;overflow:auto;color:#bfd1ee;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px}.toast{position:fixed;right:24px;bottom:24px;background:#071024;border:1px solid #36d7ff;color:#fff;border-radius:16px;padding:14px 18px;box-shadow:0 18px 60px rgba(0,0,0,.42);z-index:99999}.diag{margin-top:10px;color:#fca5a5;font-size:12px}.badgeRow{display:flex;gap:8px;flex-wrap:wrap}.link{color:#7ee8ff;text-decoration:none}.link:hover{text-decoration:underline}@media(max-width:1000px){.grid{grid-template-columns:repeat(2,1fr)}.shell{grid-template-columns:1fr}.split{grid-template-columns:1fr}.top{flex-direction:column}.search{min-width:0;width:100%}}`;
const h = (s)=>String(s??'');
const esc = (s)=>h(s).replace(/[&<>"']/g, m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));
const short = (s,n=12)=>{s=h(s);return s.length>n?s.slice(0,n):s};
const lower = s => h(s).toLowerCase();
const nowTime = () => new Date().toLocaleTimeString();
function statusClass(s){s=lower(s); if(s.includes('run')||s.includes('up')||s.includes('ready')||s==='true'||s.includes('active')) return 'ok'; if(s.includes('warn')||s.includes('pending')||s.includes('created')) return 'warn'; if(s.includes('error')||s.includes('fail')||s.includes('exit')||s.includes('crash')||s.includes('unauth')) return 'bad'; return '';}
function normalizeItems(payload){
  if(Array.isArray(payload)) return payload;
  if(!payload || typeof payload!=='object') return [];
  if(Array.isArray(payload.items)) return payload.items;
  if(Array.isArray(payload.data)) return payload.data;
  if(payload.data && Array.isArray(payload.data.items)) return payload.data.items;
  if(payload.data && Array.isArray(payload.data.result)) return payload.data.result;
  if(payload.result && Array.isArray(payload.result)) return payload.result;
  if(payload.content && Array.isArray(payload.content)) return payload.content;
  return [];
}
function flatten(obj){
  const o = obj || {};
  const md = o.metadata || {};
  const st = o.status || {};
  const spec = o.spec || {};
  const names = Array.isArray(o.Names) ? o.Names : [];
  return {
    id: h(o.id || o.Id || md.uid || o.name || md.name || o.ID),
    name: h(o.name || o.Name || md.name || (names[0]||'').replace(/^\//,'') || o.repository || o.Repository || o.RepoTags?.[0] || o.Id || ''),
    namespace: h(o.namespace || md.namespace || o.Namespace || ''),
    image: h(o.image || o.Image || (spec.template && spec.template.spec && spec.template.spec.containers && spec.template.spec.containers[0] && spec.template.spec.containers[0].image) || (spec.containers && spec.containers[0] && spec.containers[0].image) || ''),
    status: h(o.state || o.State || o.status || st.phase || o.Status || o.ready || ''),
    created: h(o.created || o.CreatedAt || o.Created || md.creationTimestamp || ''),
    ports: h(o.ports || o.Ports || ''),
    size: h(o.size || o.Size || o.VirtualSize || ''),
    ready: h(o.ready || (st.readyReplicas!=null && spec.replicas!=null ? `${st.readyReplicas}/${spec.replicas}` : '') || ''),
    replicas: (o.replicas ?? spec.replicas ?? st.replicas ?? ''),
    kind: h(o.kind || ''),
    raw: o
  };
}
class BaseMfe extends HTMLElement{
  constructor(){super(); this.attachShadow({mode:'open'}); this.state={loading:false,lastRefresh:'-',errors:{},data:{},active:'',filter:'',toast:'',raw:null}; this.config={};}
  connectedCallback(){ if(this._connected) return; this._connected=true; this.state.active=this.config.tabs?.[0]?.id||''; this.render(); this.refresh(); }
  qs(sel){return this.shadowRoot.querySelector(sel)}
  qsa(sel){return Array.from(this.shadowRoot.querySelectorAll(sel))}
  setState(p){Object.assign(this.state,p); this.render();}
  toast(msg){this.state.toast=msg; this.render(); setTimeout(()=>{this.state.toast=''; this.render();},3600)}
  async request(url, opts={}, retry=true){
    const headers=Object.assign({'Accept':'application/json'}, opts.headers||{});
    if(opts.body && !headers['Content-Type']) headers['Content-Type']='application/json';
    const res=await fetch(url, Object.assign({cache:'no-store'}, opts, {headers}));
    if(res.status===401 && retry && window.__NEBULAOPS_REFRESH_TOKEN__){
      try{ await window.__NEBULAOPS_REFRESH_TOKEN__(); }catch(_){ }
      return this.request(url, opts, false);
    }
    const text=await res.text(); let body=text; try{ body=text?JSON.parse(text):null; }catch(_){ }
    if(!res.ok){ const e=new Error(`${res.status} ${res.statusText}`); e.status=res.status; e.body=body; throw e; }
    return body;
  }
  async refresh(){
    this.state.loading=true; this.state.errors={}; this.render();
    const data={}; const errors={};
    await Promise.all((this.config.tabs||[]).map(async t=>{
      try{ data[t.id]=await this.request(t.url); }catch(e){ errors[t.id]=`${e.status||''} ${e.message||e}`.trim() + (e.body && e.body.error ? ` · ${e.body.error}` : ''); data[t.id]=null; }
    }));
    this.state.data=data; this.state.errors=errors; this.state.lastRefresh=nowTime(); this.state.loading=false; this.render();
  }
  tabItems(id){ return normalizeItems(this.state.data[id]).map(flatten); }
  filterItems(items){const q=lower(this.state.filter); if(!q) return items; return items.filter(x=>lower(JSON.stringify(x.raw)).includes(q)||lower(x.name).includes(q)||lower(x.id).includes(q)||lower(x.namespace).includes(q)||lower(x.image).includes(q));}
  metrics(){const tabs=this.config.tabs||[]; const total=tabs.reduce((a,t)=>a+this.tabItems(t.id).length,0); const ok=tabs.length-Object.keys(this.state.errors).length; return {endpoints:tabs.length, live:ok, records:total, unavailable:Object.keys(this.state.errors).length};}
  render(){
    const m=this.metrics(); const active=(this.config.tabs||[]).find(t=>t.id===this.state.active) || (this.config.tabs||[])[0] || {}; const rows=this.filterItems(this.tabItems(active.id));
    this.shadowRoot.innerHTML=`<style>${CSS}</style><div class="mfe"><div class="top"><div class="title"><div class="icon">${esc(this.config.icon||'◇')}</div><div><div class="eyebrow">${esc(this.config.eyebrow||'NebulaOps')}</div><div class="h1">${esc(this.config.title||'Module')}</div><div class="sub">${esc(this.config.subtitle||'')}</div></div></div><div class="top-actions"><button class="btn primary" data-act="refresh">${this.state.loading?'Refreshing...':'Refresh live data'}</button><a class="btn" href="/remoteEntry.js" target="_blank">remoteEntry.js</a></div></div>${this.renderBody(m,active,rows)}</div>${this.state.toast?`<div class="toast">${esc(this.state.toast)}</div>`:''}`;
    this.shadowRoot.querySelector('[data-act="refresh"]')?.addEventListener('click',()=>this.refresh());
    this.shadowRoot.querySelectorAll('[data-tab]').forEach(b=>b.addEventListener('click',()=>{this.state.active=b.getAttribute('data-tab');this.render();}));
    this.shadowRoot.querySelectorAll('[data-action]').forEach(b=>b.addEventListener('click',()=>this.handleAction(b.getAttribute('data-action'), b.getAttribute('data-id'), b.getAttribute('data-tab'))));
    const search=this.shadowRoot.querySelector('[data-search]'); if(search){search.value=this.state.filter; search.addEventListener('input',e=>{this.state.filter=e.target.value;this.render();});}
  }
  renderBody(m,active,rows){ return `<div class="grid"><div class="metric"><div class="label">Endpoint checks</div><div class="value">${m.endpoints}</div></div><div class="metric"><div class="label">Live sources</div><div class="value">${m.live}</div></div><div class="metric"><div class="label">Records returned</div><div class="value">${m.records}</div></div><div class="metric"><div class="label">Unavailable sources</div><div class="value">${m.unavailable}</div></div></div><div class="shell"><div class="side"><div class="sectionTitle">Live endpoints</div>${(this.config.tabs||[]).map(t=>`<button class="navbtn ${this.state.active===t.id?'active':''}" data-tab="${esc(t.id)}"><span>${esc(t.label)}</span><span class="pill">${this.tabItems(t.id).length}</span></button>`).join('')}</div><div class="content"><div class="toolbar"><h2>${esc(active.label||'Endpoint')}</h2><input class="search" data-search placeholder="Filter current endpoint..."></div>${this.renderTable(active,rows)}</div></div>`; }
  renderTable(active, rows){
    const err=this.state.errors[active.id];
    if(err) return `<div class="empty"><b>Live endpoint unavailable</b><span>${esc(active.url)}</span><div class="diag">${esc(err)}</div></div>`;
    if(!rows.length) return `<div class="empty"><b>No records returned by this live endpoint.</b><span>Endpoint: ${esc(active.url||'')}</span></div>`;
    return `<div class="tableWrap"><table class="table"><thead><tr>${(active.columns||['Name','Status','Details','Actions']).map(c=>`<th>${esc(c)}</th>`).join('')}</tr></thead><tbody>${rows.map(x=>this.renderRow(active,x)).join('')}</tbody></table></div>`;
  }
  renderRow(active,x){return `<tr><td><div class="rowTitle">${esc(x.name||short(x.id))}</div><div class="muted">${esc(short(x.id,18))}</div></td><td><span class="status ${statusClass(x.status)}">${esc(x.status||'-')}</span></td><td><div class="muted">${esc(x.namespace||x.image||x.ports||x.size||x.created||'-')}</div></td><td><div class="actions">${this.renderActions(active,x)}</div></td></tr>`}
  renderActions(active,x){return `<button class="btn small" data-action="raw" data-tab="${esc(active.id)}" data-id="${esc(x.id||x.name)}">Raw</button>`}
  findItem(tab,id){return this.tabItems(tab).find(x=>(x.id||x.name)===id) || this.tabItems(tab).find(x=>x.name===id)}
  async handleAction(action,id,tab){ if(action==='raw'){ const item=this.findItem(tab,id); this.toast(JSON.stringify(item?.raw||{},null,2).slice(0,900)); return; } }
}
window.__NebulaBaseMfe=BaseMfe; window.__NebulaMfeHelpers={esc,short,flatten,normalizeItems,statusClass};
})();


(function(){
'use strict';
const Base=window.__NebulaBaseMfe; const {esc,statusClass}=window.__NebulaMfeHelpers;
class InfraMfe extends Base{
 constructor(){super();this.config={icon:'🛰️',eyebrow:'Infrastructure · Hub',title:'INFRA Hub',subtitle:'Infrastructure launchpad and health console for real NebulaOps tools, SSO bridges, data plane and micro-frontends.',tabs:[
  {id:'tools',label:'Tool UIs',url:'/api/platform/tools',columns:['Tool','Status','URL','Actions']},
  {id:'health',label:'Gateway health',url:'/api/health',columns:['Service','Status','Details','Actions']},
  {id:'containers',label:'Runtime containers',url:'/api/runtime/docker/containers',columns:['Container','Status','Image / Ports','Actions']},
  {id:'events',label:'Platform events',url:'/api/events',columns:['Event','Type','Source','Actions']}
 ]}}
 tabItems(id){
  if(id==='tools'){
    const links=[
      ['Grafana','http://localhost:3000/api/health','http://localhost:3000'],['Prometheus','http://localhost:9090/-/healthy','http://localhost:9090'],['Loki','http://localhost:3100/loki/api/v1/status/buildinfo','http://localhost:3100'],['Tempo','http://localhost:3200/ready','http://localhost:3200'],['RabbitMQ','http://localhost:15672','http://localhost:15672'],['Mongo Express','http://localhost:8088','http://localhost:8088'],['Redis Commander','http://localhost:8089','http://localhost:8089'],['Gateway API','http://localhost:8080/actuator/health','http://localhost:8080/actuator/health']
    ];
    return links.map(x=>({id:x[0],name:x[0],status:'open external',namespace:'',image:x[1],raw:{title:x[0],healthUrl:x[1],url:x[2]}}));
  }
  return super.tabItems(id);
 }
 renderRow(active,x){return `<tr><td><div class="rowTitle">${esc(x.name)}</div><div class="muted">${esc(x.id)}</div></td><td><span class="status ${statusClass(x.status)}">${esc(x.status||'-')}</span></td><td><div class="muted">${esc(x.image||x.raw?.url||x.raw?.healthUrl||'')}</div></td><td><div class="actions">${this.renderActions(active,x)}</div></td></tr>`;}
 renderActions(active,x){const id=esc(x.id||x.name); if(active.id==='tools') return `<a class="btn small ok" href="${esc(x.raw.url)}" target="_blank">Open</a><a class="btn small" href="${esc(x.raw.healthUrl)}" target="_blank">Health</a>`; if(active.id==='containers') return `<button class="btn small" data-action="raw" data-tab="containers" data-id="${id}">Raw</button>`; return `<button class="btn small" data-action="raw" data-tab="${active.id}" data-id="${id}">Raw</button>`;}
 async refresh(){
   // /api/platform/tools may not exist by design; tools are launch links only, other tabs still hit live endpoints.
   await super.refresh();
   if(this.state.errors.tools){delete this.state.errors.tools; this.render();}
 }
}
if(!customElements.get('nebulaops-mfe-infra-hub')) customElements.define('nebulaops-mfe-infra-hub',InfraMfe);
})();
