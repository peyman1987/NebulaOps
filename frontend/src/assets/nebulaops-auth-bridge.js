/* NebulaOps v22.4 auth bridge
 * Makes shell-loaded and standalone MFE requests share a valid Bearer token.
 * - Shell origin: reuses localStorage token or bootstraps dev admin token.
 * - Standalone MFE route (/remotes/<mfe>/): uses the same nebulaops.localhost origin and the shared shell token.
 * - Patches fetch/XMLHttpRequest so the first API request waits for token availability.
 * - Rewrites legacy relative runtime endpoints to the gateway API path.
 */
(function nebulaopsAuthBridge() {
  'use strict';
  var VERSION = 'v22.4.8-dual-jwt-auth-bridge';
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
