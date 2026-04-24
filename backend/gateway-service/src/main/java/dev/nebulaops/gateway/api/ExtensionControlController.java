package dev.nebulaops.gateway.api;

import dev.nebulaops.gateway.client.ToolCommandClient;
import dev.nebulaops.gateway.client.ToolResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NebulaOps v23.2 — UI-controlled extension control plane.
 *
 * Installed extensions are explicit and limited to APIForge, KubeBridge and Contract Hub.
 * The controller never creates mock data: status is read from kubectl, Docker/local-registry
 * and the live Spring Boot extension health endpoints.
 */
@RestController
public class ExtensionControlController {

    private static final String NAMESPACE = "nebulaops";

    private final ToolCommandClient tools;
    private final RestTemplate rest;
    private final Map<String, ExtensionSpec> extensions;
    private final Map<String, CachedExtensionStatus> statusCache = new ConcurrentHashMap<>();

    @Value("${nebulaops.extensions.workspace:/workspace}")
    private String workspace;

    @Value("${nebulaops.extensions.local-registry:localhost:5001}")
    private String localRegistry;

    @Value("${nebulaops.extensions.platform:linux/amd64}")
    private String platform;

    @Value("${nebulaops.extensions.control.enabled:true}")
    private boolean controlEnabled;

    @Value("${nebulaops.extensions.status-cache-ttl-ms:5000}")
    private long statusCacheTtlMs;

    @Value("${nebulaops.extensions.probe-timeout-seconds:3}")
    private int probeTimeoutSeconds;

    public ExtensionControlController(ToolCommandClient tools, RestTemplate rest) {
        this.tools = tools;
        this.rest = rest;
        this.extensions = Map.of(
                "apiforge", new ExtensionSpec("apiforge", "APIForge", "⚒️", "API workspace", "nebulaops-v23-2-apiforge:latest", 18110, "/apiforge/actuator/health", "/apiforge/"),
                "kubebridge", new ExtensionSpec("kubebridge", "KubeBridge", "☸️", "Kubernetes control", "nebulaops-v23-2-kubebridge:latest", 18111, "/kubebridge/healthz", "/kubebridge/"),
                "contract-hub", new ExtensionSpec("contract-hub", "Contract Hub", "📜", "API contracts", "nebulaops-v23-2-contract-hub:latest", 18114, "/contract-hub/healthz", "/contract-hub/")
        );
    }

    @GetMapping("/api/extensions")
    public Map<String, Object> listExtensions() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ExtensionSpec spec : installedExtensions()) {
            items.add(lightweightStatusBody(spec));
        }
        return Map.of(
                "live", true,
                "realDataOnly", true,
                "mode", "FAST_EXTENSION_REGISTRY",
                "message", "Installed extension registry returned without blocking kubectl probes. Use /api/extensions/{slug}/status for deep runtime status.",
                "items", items,
                "generatedAt", Instant.now().toString()
        );
    }

    @GetMapping("/api/extensions/summary")
    public Map<String, Object> extensionSummary() {
        List<Map<String, Object>> items = new ArrayList<>();
        int running = 0;
        for (ExtensionSpec spec : installedExtensions()) {
            Map<String, Object> status = cachedStatusBody(spec, false);
            items.add(status);
            if ("RUNNING".equals(status.get("state"))) running++;
        }
        return Map.of(
                "live", true,
                "realDataOnly", true,
                "mode", "CACHED_EXTENSION_SUMMARY",
                "installed", items.size(),
                "running", running,
                "stopped", items.size() - running,
                "items", items,
                "generatedAt", Instant.now().toString()
        );
    }

    @GetMapping("/api/extensions/{slug}/status")
    public Map<String, Object> extensionStatus(@PathVariable String slug, @RequestParam(defaultValue = "false") boolean refresh) {
        return cachedStatusBody(spec(slug), refresh);
    }

    @GetMapping("/api/extensions/{slug}/diagnostics")
    public Map<String, Object> extensionDiagnostics(@PathVariable String slug) {
        ExtensionSpec spec = spec(slug);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", spec.slug());
        out.put("title", spec.title());
        out.put("realDataOnly", true);
        out.put("docker", resultMap(run("command -v docker >/dev/null 2>&1 && docker info --format '{{.ServerVersion}}'", 4)));
        out.put("kubectl", resultMap(run("command -v kubectl >/dev/null 2>&1 && kubectl version --client=true --output=json >/dev/null 2>&1", 4)));
        out.put("deployment", resultMap(run("kubectl -n " + q(NAMESPACE) + " get deploy " + q(spec.slug()) + " -o wide", 4)));
        out.put("service", resultMap(run("kubectl -n " + q(NAMESPACE) + " get svc " + q(spec.slug()) + " -o wide", 4)));
        out.put("pods", resultMap(run("kubectl -n " + q(NAMESPACE) + " get pods -l app=" + q(spec.slug()) + " -o wide", 4)));
        out.put("status", cachedStatusBody(spec, true));
        out.put("generatedAt", Instant.now().toString());
        return out;
    }

    @PostMapping("/api/extensions/{slug}/start")
    public Map<String, Object> startExtension(@PathVariable String slug) {
        assertControlEnabled();
        return startOrRestart(spec(slug), "start", false);
    }

    @PostMapping("/api/extensions/{slug}/restart")
    public Map<String, Object> restartExtension(@PathVariable String slug) {
        assertControlEnabled();
        return startOrRestart(spec(slug), "restart", true);
    }

    @PostMapping("/api/extensions/{slug}/stop")
    public Map<String, Object> stopExtension(@PathVariable String slug, @RequestParam(defaultValue = "false") boolean deleteData) {
        assertControlEnabled();
        ExtensionSpec spec = spec(slug);
        Map<String, Object> out = baseAction(spec, "stop");
        out.put("portForward", resultMap(stopGatewayPortForward(spec)));
        out.put("ingress", resultMap(run("kubectl -n " + q(NAMESPACE) + " delete ingress " + q(spec.slug()) + " --ignore-not-found=true", 60)));
        out.put("service", resultMap(run("kubectl -n " + q(NAMESPACE) + " delete service " + q(spec.slug()) + " --ignore-not-found=true", 60)));
        out.put("deployment", resultMap(run("kubectl -n " + q(NAMESPACE) + " delete deployment " + q(spec.slug()) + " --ignore-not-found=true", 90)));
        if (deleteData && "apiforge".equals(spec.slug())) {
            out.put("pvc", resultMap(run("kubectl -n " + q(NAMESPACE) + " delete pvc apiforge-data --ignore-not-found=true", 90)));
        }
        out.put("ok", true);
        out.put("status", cachedStatusBody(spec, true));
        return out;
    }

    @GetMapping(value = "/api/extensions/console", produces = MediaType.TEXT_HTML_VALUE)
    public String console() {
        return """
<!doctype html>
<html lang=\"en\">
<head>
  <meta charset=\"utf-8\" />
  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
  <title>NebulaOps · Extension Control</title>
  <style>
    *{box-sizing:border-box} body{margin:0;min-height:100vh;background:radial-gradient(circle at 10% 5%,rgba(0,216,255,.14),transparent 28%),radial-gradient(circle at 90% 0%,rgba(124,92,255,.18),transparent 30%),#050816;color:#eef6ff;font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;padding:32px}.wrap{max-width:1220px;margin:0 auto}.kicker{color:#7adfff;font-size:11px;font-weight:900;letter-spacing:.22em;text-transform:uppercase;margin:0 0 8px}.hero{display:flex;justify-content:space-between;gap:24px;align-items:flex-start;margin-bottom:22px}.hero h1{margin:0;font-size:34px;line-height:1.08}.hero p{margin:8px 0 0;color:#9fb1ce;line-height:1.6;max-width:780px}.grid{display:grid;grid-template-columns:330px minmax(0,1fr);gap:16px}.card{border:1px solid rgba(130,170,255,.18);background:linear-gradient(145deg,rgba(14,23,51,.9),rgba(7,10,26,.94));box-shadow:0 24px 70px rgba(0,0,0,.42),inset 0 1px 0 rgba(255,255,255,.07);border-radius:24px;padding:20px}.ext-list{display:grid;gap:10px}.ext{width:100%;text-align:left;border:1px solid rgba(140,180,255,.13);background:rgba(255,255,255,.05);color:#eef6ff;border-radius:18px;padding:14px;cursor:pointer}.ext:hover,.ext.active{border-color:#7adfff66;background:#7adfff12}.ext b{display:block;font-size:15px}.ext small{display:block;color:#8ea0c3;margin-top:4px}.status{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.metric{border:1px solid rgba(140,180,255,.14);background:rgba(255,255,255,.05);border-radius:16px;padding:14px}.metric small{display:block;color:#6b7fa8;font-weight:800;text-transform:uppercase;letter-spacing:.08em;font-size:10px;margin-bottom:7px}.metric b{font-size:16px}.actions{display:flex;gap:12px;flex-wrap:wrap;margin-top:18px}.btn{border:1px solid rgba(122,223,255,.26);border-radius:14px;padding:12px 16px;background:rgba(122,223,255,.1);color:#7adfff;font-weight:900;cursor:pointer;text-decoration:none;display:inline-flex;align-items:center;gap:8px}.btn.primary{background:linear-gradient(135deg,#00d9ff,#7b61ff);border-color:transparent;color:white}.btn.warn{border-color:rgba(255,207,138,.32);background:rgba(255,207,138,.09);color:#ffcf8a}.btn.danger{border-color:rgba(255,95,130,.3);background:rgba(255,95,130,.09);color:#ff9db2}.btn:disabled{opacity:.45;cursor:not-allowed}.log{white-space:pre-wrap;word-break:break-word;background:#00000045;border:1px solid rgba(255,255,255,.08);border-radius:16px;padding:14px;color:#b7c7e8;min-height:250px;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px}.pill{display:inline-flex;align-items:center;gap:8px;border-radius:999px;padding:7px 11px;background:rgba(255,255,255,.06);border:1px solid rgba(255,255,255,.09);color:#b7c7e8;font-size:12px}.dot{width:9px;height:9px;border-radius:50%;background:#ffcf8a}.dot.up{background:#70f0b4;box-shadow:0 0 18px rgba(112,240,180,.8)}.dot.down{background:#ff8fa3;box-shadow:0 0 18px rgba(255,143,163,.75)}.dot.warn{background:#ffcf8a;box-shadow:0 0 18px rgba(255,207,138,.75)}@media(max-width:900px){.grid{grid-template-columns:1fr}.status{grid-template-columns:1fr}.hero{flex-direction:column}}
  </style>
</head>
<body>
  <div class=\"wrap\">
    <div class=\"hero\">
      <div><p class=\"kicker\">NEBULAOPS EXTENSION CONTROL</p><h1>Installed Extensions</h1><p>Installed extensions are disabled by default. Start, stop, restart, inspect and open them explicitly. The control plane uses Docker, the local registry and kubectl; no mock data is generated.</p></div>
      <span class=\"pill\"><span id=\"dot\" class=\"dot\"></span><span id=\"state\">CHECKING</span></span>
    </div>
    <section class=\"grid\">
      <aside class=\"card\"><p class=\"kicker\">INSTALLED</p><div id=\"extensions\" class=\"ext-list\"></div></aside>
      <main>
        <section class=\"card\"><p class=\"kicker\" id=\"selectedTitle\">EXTENSION</p><div class=\"status\">
          <div class=\"metric\"><small>Deployment</small><b id=\"deployment\">—</b></div>
          <div class=\"metric\"><small>Ready</small><b id=\"ready\">—</b></div>
          <div class=\"metric\"><small>Service</small><b id=\"service\">—</b></div>
          <div class=\"metric\"><small>Gateway proxy</small><b id=\"proxy\">—</b></div>
        </div><div class=\"actions\">
          <button id=\"start\" class=\"btn primary\">▶ Start</button>
          <button id=\"stop\" class=\"btn danger\">■ Stop</button>
          <button id=\"restart\" class=\"btn warn\">↻ Restart</button>
          <button id=\"refresh\" class=\"btn\">● Status</button>
          <a id=\"open\" class=\"btn\" href=\"#\" target=\"_blank\" rel=\"noreferrer\">↗ Open</a>
        </div></section>
        <section class=\"card\" style=\"margin-top:16px\"><p class=\"kicker\">RUNTIME OUTPUT</p><div id=\"log\" class=\"log\">Ready.</div></section>
      </main>
    </section>
  </div>
<script>
const token=localStorage.getItem('nebulaops.v23_2.jwt')||'';
const headers=token?{Authorization:'Bearer '+token}:{};
const log=document.getElementById('log');
let items=[]; let selected=new URLSearchParams(location.search).get('extension') || 'apiforge';
function setBusy(v){ for(const id of ['start','stop','restart','refresh']) document.getElementById(id).disabled=v; }
function write(v){ log.textContent=typeof v==='string'?v:JSON.stringify(v,null,2); }
function esc(v){ return String(v??'').replace(/[&<>\"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;',"'":'&#39;'}[c])); }
function renderList(){ const box=document.getElementById('extensions'); box.innerHTML=items.map(x=>`<button class=\"ext ${x.id===selected?'active':''}\" data-id=\"${esc(x.id)}\"><b>${esc(x.icon||'◈')} ${esc(x.title)}</b><small>${esc(x.category||'Extension')} · ${esc(x.state||'UNKNOWN')}</small></button>`).join(''); box.querySelectorAll('button').forEach(b=>b.onclick=()=>{selected=b.dataset.id; history.replaceState(null,'','?extension='+selected); renderList(); const it=items.find(x=>x.id===selected); if(it) render(it); status().then(write).catch(e=>write(String(e)));}); }
function render(j){ const up=j.state==='RUNNING', stopped=j.state==='STOPPED'; document.getElementById('state').textContent=j.state||'UNKNOWN'; document.getElementById('dot').className='dot '+(up?'up':stopped?'down':'warn'); document.getElementById('selectedTitle').textContent=(j.title||j.id||'EXTENSION')+' CONTROL'; document.getElementById('deployment').textContent=j.deployment||'NOT_FOUND'; document.getElementById('ready').textContent=(j.readyReplicas??0)+'/'+(j.replicas??0); document.getElementById('service').textContent=j.service||'NOT_FOUND'; document.getElementById('proxy').textContent=j.gatewayProxy||'UNKNOWN'; document.getElementById('open').href=j.openUrl||('/'+selected+'/'); }
async function loadAll(){ const r=await fetch('/api/extensions',{headers}); const j=await r.json(); items=j.items||[]; if(!items.find(x=>x.id===selected) && items[0]) selected=items[0].id; renderList(); const it=items.find(x=>x.id===selected); if(it) render(it); return j; }
async function status(){ const r=await fetch('/api/extensions/'+selected+'/status',{headers}); const j=await r.json(); const ix=items.findIndex(x=>x.id===selected); if(ix>=0) items[ix]=j; renderList(); render(j); return j; }
async function post(action){ setBusy(true); write('Executing '+action+' for '+selected+' ...'); try{ const r=await fetch('/api/extensions/'+selected+'/'+action,{method:'POST',headers:{...headers,'Content-Type':'application/json'}}); const j=await r.json(); write(j); await loadAll(); } catch(e){ write(String(e)); } finally{ setBusy(false); }}
document.getElementById('start').onclick=()=>post('start');
document.getElementById('stop').onclick=()=>post('stop');
document.getElementById('restart').onclick=()=>post('restart');
document.getElementById('refresh').onclick=()=>status().then(write).catch(e=>write(String(e)));
loadAll().then(write).catch(e=>write(String(e))); setInterval(()=>loadAll().catch(()=>{}),15000);
</script>
</body>
</html>
""";
    }

    @RequestMapping(value = {"/apiforge/**", "/kubebridge/**", "/contract-hub/**", "/extensions/apiforge/**", "/extensions/kubebridge/**", "/extensions/contract-hub/**"},
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS})
    public ResponseEntity<byte[]> proxyExtension(HttpServletRequest request, @RequestBody(required = false) byte[] body) throws IOException {
        ExtensionSpec spec = specFromPath(request.getRequestURI());
        ToolResult ready = deploymentReady(spec);
        if (!ready.ok()) {
            return extensionProxyUnavailable(spec, "EXTENSION_NOT_RUNNING", ready, request);
        }
        ToolResult portForward = ensureGatewayPortForward(spec);
        if (!portForward.ok()) {
            return extensionProxyUnavailable(spec, "GATEWAY_PROXY_UNAVAILABLE", portForward, request);
        }
        String forwardedPath = forwardedPath(request.getRequestURI(), spec);
        String query = request.getQueryString();
        String target = "http://127.0.0.1:" + spec.localProxyPort() + forwardedPath + (query == null || query.isBlank() ? "" : "?" + query);
        HttpHeaders headers = new HttpHeaders();
        String contentType = request.getContentType();
        if (contentType != null && !contentType.isBlank()) headers.set(HttpHeaders.CONTENT_TYPE, contentType);
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept != null && !accept.isBlank()) headers.set(HttpHeaders.ACCEPT, accept);
        try {
            return rest.exchange(URI.create(target), HttpMethod.valueOf(request.getMethod()), new HttpEntity<>(body == null ? new byte[0] : body, headers), byte[].class);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).headers(e.getResponseHeaders()).body(e.getResponseBodyAsByteArray());
        } catch (Exception e) {
            return extensionProxyUnavailable(spec, "UPSTREAM_REQUEST_FAILED", new ToolResult(false, -1, e.getClass().getSimpleName() + ": " + e.getMessage(), "", "", 0, Instant.now().toString()), request);
        }
    }

    private ToolResult deploymentReady(ExtensionSpec spec) {
        ToolResult deploy = run("kubectl -n " + q(NAMESPACE) + " get deploy " + q(spec.slug()) + " -o jsonpath='{.status.readyReplicas}:{.spec.replicas}'", 12);
        if (!deploy.ok()) return deploy;
        String value = deploy.stdout() == null ? "" : deploy.stdout().replace("'", "").trim();
        if (!value.contains(":")) {
            return new ToolResult(false, 1, spec.title() + " deployment has no readiness information", value, "", 0, Instant.now().toString());
        }
        String[] parts = value.split(":", 2);
        int ready = parseInt(parts[0]);
        int replicas = parseInt(parts[1]);
        if (ready > 0 && replicas > 0) {
            return new ToolResult(true, 0, spec.title() + " deployment is ready", value, "", 0, Instant.now().toString());
        }
        return new ToolResult(false, 1, spec.title() + " deployment is not ready", value, "", 0, Instant.now().toString());
    }

    private ResponseEntity<byte[]> extensionProxyUnavailable(ExtensionSpec spec, String code, ToolResult result, HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        boolean wantsHtml = accept == null || accept.contains(MediaType.TEXT_HTML_VALUE) || accept.contains("*/*");
        String body;
        MediaType mediaType;
        if (wantsHtml) {
            mediaType = MediaType.TEXT_HTML;
            body = """
                    <!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>NebulaOps Extension Unavailable</title>
                    <style>body{margin:0;min-height:100vh;background:#071124;color:#eaf2ff;font-family:Inter,Segoe UI,Arial,sans-serif;display:flex;align-items:center;justify-content:center;padding:24px}.card{max-width:860px;border:1px solid #2b3b63;background:#121b31;border-radius:28px;padding:32px;box-shadow:0 28px 90px rgba(0,0,0,.42)}.k{color:#7adfff;font-weight:900;letter-spacing:.22em;font-size:12px;text-transform:uppercase}p{color:#92a8d2;line-height:1.65}.btn{display:inline-block;margin:8px 8px 0 0;padding:12px 16px;border-radius:14px;background:#0e7490;color:white;text-decoration:none;font-weight:800}.btn.secondary{background:#1f2937}pre{white-space:pre-wrap;background:#050b16;border:1px solid #2b3b63;border-radius:16px;padding:14px;color:#c7d2fe}</style></head>
                    <body><section class=\"card\"><div class=\"k\">NEBULAOPS EXTENSION GATEWAY</div><h1>%s is not reachable</h1><p>State: <b>%s</b>. Start the extension from the Extensions control panel and retry. No mock data is served.</p><a class=\"btn\" href=\"/api/extensions/console?extension=%s\">Open Extensions Control</a><a class=\"btn secondary\" href=\"%s\">Retry</a><pre>%s</pre></section></body></html>
                    """.formatted(escapeHtml(spec.title()), escapeHtml(code), escapeHtml(spec.slug()), escapeHtml(request.getRequestURI()), escapeHtml(result.message() + "\n" + (result.stdout() == null ? "" : result.stdout()) + "\n" + (result.stderr() == null ? "" : result.stderr())));
        } else {
            mediaType = MediaType.APPLICATION_JSON;
            body = "{\"ok\":false,\"state\":\"" + escapeJson(code) + "\",\"extension\":\"" + escapeJson(spec.slug()) + "\",\"message\":\"" + escapeJson(result.message()) + "\"}";
        }
        return ResponseEntity.status(503).contentType(mediaType).body(body.getBytes(StandardCharsets.UTF_8));
    }

    private static String escapeHtml(String value) {
        return String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String escapeJson(String value) {
        return String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private Map<String, Object> startOrRestart(ExtensionSpec spec, String action, boolean restartOnly) {
        Map<String, Object> out = baseAction(spec, action);

        ToolResult registry = ensureRegistry();
        out.put("registry", resultMap(registry));
        if (!registry.ok()) return failed(out, "LOCAL_REGISTRY_UNAVAILABLE", registry, spec);

        String deployedImage = localRegistry + "/" + spec.image();
        ToolResult build = run("docker build --platform " + q(platform)
                + " -t " + q(spec.image())
                + " -t " + q(deployedImage)
                + " " + q(workspace + "/extensions/" + spec.slug()), 600);
        out.put("build", resultMap(build));
        if (!build.ok()) return failed(out, "IMAGE_BUILD_FAILED", build, spec);

        ToolResult push = run("docker push " + q(deployedImage), 300);
        out.put("push", resultMap(push));
        if (!push.ok()) return failed(out, "IMAGE_PUSH_FAILED", push, spec);

        ToolResult apply = applyManifest(spec, deployedImage);
        out.put("apply", resultMap(apply));
        if (!apply.ok()) return failed(out, "KUBERNETES_APPLY_FAILED", apply, spec);

        ToolResult scale = run("kubectl -n " + q(NAMESPACE) + " scale deployment/" + q(spec.slug()) + " --replicas=1", 90);
        out.put("scale", resultMap(scale));
        if (!scale.ok()) return failed(out, "KUBERNETES_SCALE_FAILED", scale, spec);

        String restartCommand = restartOnly
                ? "kubectl -n " + q(NAMESPACE) + " rollout restart deployment/" + q(spec.slug())
                : "kubectl -n " + q(NAMESPACE) + " rollout restart deployment/" + q(spec.slug()) + " >/dev/null 2>&1 || true";
        ToolResult rollout = run(restartCommand + "; kubectl -n " + q(NAMESPACE) + " rollout status deployment/" + q(spec.slug()) + " --timeout=300s", 330);
        out.put("rollout", resultMap(rollout));
        if (!rollout.ok()) return failed(out, "ROLLOUT_FAILED", rollout, spec);

        ToolResult portForward = ensureGatewayPortForward(spec);
        out.put("gatewayPortForward", resultMap(portForward));
        if (!portForward.ok()) return failed(out, "GATEWAY_PROXY_UNAVAILABLE", portForward, spec);

        out.put("ok", true);
        out.put("status", cachedStatusBody(spec, true));
        out.put("openUrl", spec.openUrl());
        return out;
    }


    private Map<String, Object> lightweightStatusBody(ExtensionSpec spec) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", spec.slug());
        out.put("title", spec.title());
        out.put("icon", spec.icon());
        out.put("category", spec.category());
        out.put("enabledByDefault", false);
        out.put("defaultState", "DISABLED");
        out.put("state", "DISABLED_BY_DEFAULT");
        out.put("deployment", "NOT_PROBED");
        out.put("service", "NOT_PROBED");
        out.put("pods", "NOT_PROBED");
        out.put("readyReplicas", 0);
        out.put("replicas", 0);
        out.put("gatewayProxy", "NOT_PROBED");
        out.put("openUrl", spec.openUrl());
        out.put("healthPath", spec.healthPath());
        out.put("statusUrl", "/api/extensions/" + spec.slug() + "/status");
        out.put("message", "Extension is installed and disabled by default. Deep status is loaded on demand to avoid gateway timeouts.");
        out.put("generatedAt", Instant.now().toString());
        return out;
    }

    private Map<String, Object> cachedStatusBody(ExtensionSpec spec, boolean refresh) {
        long now = System.currentTimeMillis();
        CachedExtensionStatus cached = statusCache.get(spec.slug());
        if (!refresh && cached != null && now - cached.cachedAt() < Math.max(1000L, statusCacheTtlMs)) {
            Map<String, Object> hit = new LinkedHashMap<>(cached.payload());
            hit.put("cache", "HIT");
            hit.put("cacheAgeMs", now - cached.cachedAt());
            return hit;
        }
        Map<String, Object> fresh = statusBody(spec);
        statusCache.put(spec.slug(), new CachedExtensionStatus(now, new LinkedHashMap<>(fresh)));
        fresh.put("cache", "MISS");
        return fresh;
    }

    private Map<String, Object> statusBody(ExtensionSpec spec) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", spec.slug());
        out.put("title", spec.title());
        out.put("icon", spec.icon());
        out.put("category", spec.category());
        out.put("enabledByDefault", false);
        out.put("defaultState", "DISABLED");
        int probeTimeout = Math.max(1, Math.min(8, probeTimeoutSeconds));
        ToolResult kubectl = run("command -v kubectl >/dev/null 2>&1 && kubectl version --client=true --output=json >/dev/null 2>&1", probeTimeout);
        if (!kubectl.ok()) {
            out.put("deployment", "KUBERNETES_UNAVAILABLE");
            out.put("service", "KUBERNETES_UNAVAILABLE");
            out.put("pods", "KUBERNETES_UNAVAILABLE");
            out.put("readyReplicas", 0);
            out.put("replicas", 0);
            out.put("gatewayProxy", "NOT_PROBED");
            out.put("state", "KUBERNETES_UNAVAILABLE");
            out.put("openUrl", spec.openUrl());
            out.put("healthPath", spec.healthPath());
            out.put("toolStatus", resultMap(kubectl));
            out.put("message", "kubectl is not available or not executable inside the gateway runtime. No mock extension status was generated.");
            out.put("generatedAt", Instant.now().toString());
            return out;
        }
        ToolResult deploy = run("kubectl -n " + q(NAMESPACE) + " get deploy " + q(spec.slug()) + " -o jsonpath='{.status.readyReplicas}:{.spec.replicas}'", probeTimeout);
        ToolResult svc = run("kubectl -n " + q(NAMESPACE) + " get svc " + q(spec.slug()) + " -o jsonpath='{.spec.type}:{.spec.ports[0].nodePort}'", probeTimeout);
        ToolResult pods = run("kubectl -n " + q(NAMESPACE) + " get pods -l app=" + q(spec.slug()) + " --no-headers 2>/dev/null | awk '{print $3}' | paste -sd, -", probeTimeout);
        boolean proxyUp = isProxyHealthy(spec);
        int ready = 0;
        int replicas = 0;
        if (deploy.ok() && deploy.stdout() != null && deploy.stdout().contains(":")) {
            String[] parts = deploy.stdout().trim().split(":", 2);
            ready = parseInt(parts[0]);
            replicas = parseInt(parts[1]);
        }
        out.put("deployment", deploy.ok() ? "FOUND" : "NOT_FOUND");
        out.put("service", svc.ok() ? svc.stdout() : "NOT_FOUND");
        out.put("pods", pods.ok() && !pods.stdout().isBlank() ? pods.stdout() : "NOT_FOUND");
        out.put("readyReplicas", ready);
        out.put("replicas", replicas);
        out.put("gatewayProxy", proxyUp ? "UP" : "DOWN");
        out.put("state", ready > 0 && replicas > 0 ? "RUNNING" : "STOPPED");
        out.put("openUrl", spec.openUrl());
        out.put("healthPath", spec.healthPath());
        out.put("generatedAt", Instant.now().toString());
        return out;
    }

    private ToolResult ensureRegistry() {
        return run("docker ps --format '{{.Names}}' | grep -qx nebulaops-v23-2-registry || "
                + "(docker ps -a --format '{{.Names}}' | grep -qx nebulaops-v23-2-registry && docker start nebulaops-v23-2-registry >/dev/null) || "
                + "docker run -d --restart unless-stopped -p 5001:5000 --name nebulaops-v23-2-registry registry:2 >/dev/null", 60);
    }

    private ToolResult applyManifest(ExtensionSpec spec, String deployedImage) {
        try {
            Path src = Path.of(workspace, "extensions", spec.slug(), "k8s", "deployment.yml");
            String text = Files.readString(src, StandardCharsets.UTF_8)
                    .replace("image: " + spec.image(), "image: " + deployedImage)
                    .replace("imagePullPolicy: IfNotPresent", "imagePullPolicy: Always");
            Path tmp = Files.createTempFile("nebulaops-" + spec.slug() + "-ui-", ".yml");
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            ToolResult result = run("kubectl apply -f " + q(tmp.toString()), 180);
            Files.deleteIfExists(tmp);
            return result;
        } catch (Exception e) {
            return new ToolResult(false, -1, e.getClass().getSimpleName() + ": " + e.getMessage(), "", "", 0, Instant.now().toString());
        }
    }

    private ToolResult ensureGatewayPortForward(ExtensionSpec spec) {
        if (isProxyHealthy(spec)) {
            return new ToolResult(true, 0, spec.title() + " gateway proxy is already healthy", "", "", 0, Instant.now().toString());
        }
        stopGatewayPortForward(spec);
        ToolResult start = run("nohup kubectl -n " + q(NAMESPACE) + " port-forward --address 127.0.0.1 svc/" + q(spec.slug()) + " " + spec.localProxyPort() + ":8080 >/tmp/nebulaops-" + spec.slug() + "-gateway-port-forward.log 2>&1 & echo $! >/tmp/nebulaops-" + spec.slug() + "-gateway-port-forward.pid", 10);
        if (!start.ok()) return start;
        long deadline = System.currentTimeMillis() + 45_000L;
        while (System.currentTimeMillis() < deadline) {
            if (isProxyHealthy(spec)) {
                return new ToolResult(true, 0, spec.title() + " gateway proxy is ready", "", "", 0, Instant.now().toString());
            }
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new ToolResult(false, 130, "Interrupted while waiting for " + spec.title() + " gateway proxy", "", e.getMessage(), 0, Instant.now().toString());
            }
        }
        ToolResult log = run("cat /tmp/nebulaops-" + spec.slug() + "-gateway-port-forward.log 2>/dev/null || true", 5);
        return new ToolResult(false, 1, spec.title() + " gateway proxy did not become healthy", log.stdout(), log.stderr(), 45_000, Instant.now().toString());
    }

    private boolean isProxyHealthy(ExtensionSpec spec) {
        HttpURLConnection connection = null;
        try {
            URL url = URI.create("http://127.0.0.1:" + spec.localProxyPort() + spec.healthPath()).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private ToolResult stopGatewayPortForward(ExtensionSpec spec) {
        return run("if [ -f /tmp/nebulaops-" + spec.slug() + "-gateway-port-forward.pid ]; then pid=$(cat /tmp/nebulaops-" + spec.slug() + "-gateway-port-forward.pid 2>/dev/null || true); [ -n \"$pid\" ] && kill \"$pid\" >/dev/null 2>&1 || true; rm -f /tmp/nebulaops-" + spec.slug() + "-gateway-port-forward.pid; fi; pkill -f 'kubectl .*port-forward .*svc/" + spec.slug() + " .*" + spec.localProxyPort() + ":8080' >/dev/null 2>&1 || true", 15);
    }

    private Map<String, Object> failed(Map<String, Object> out, String code, ToolResult result, ExtensionSpec spec) {
        out.put("ok", false);
        out.put("state", code);
        out.put("error", result.message());
        out.put("status", cachedStatusBody(spec, true));
        return out;
    }

    private Map<String, Object> baseAction(ExtensionSpec spec, String action) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("extension", spec.slug());
        out.put("title", spec.title());
        out.put("action", action);
        out.put("startedAt", Instant.now().toString());
        return out;
    }

    private List<ExtensionSpec> installedExtensions() {
        return List.of(extensions.get("apiforge"), extensions.get("kubebridge"), extensions.get("contract-hub"));
    }

    private ExtensionSpec spec(String slug) {
        ExtensionSpec spec = extensions.get(slug);
        if (spec == null) throw new IllegalArgumentException("Unknown or not installed extension: " + slug);
        return spec;
    }

    private ExtensionSpec specFromPath(String uri) {
        for (ExtensionSpec spec : installedExtensions()) {
            if (uri.startsWith("/" + spec.slug() + "/") || uri.equals("/" + spec.slug()) || uri.startsWith("/extensions/" + spec.slug() + "/") || uri.equals("/extensions/" + spec.slug())) {
                return spec;
            }
        }
        throw new IllegalArgumentException("No installed extension matches path: " + uri);
    }

    private String forwardedPath(String uri, ExtensionSpec spec) {
        String path = uri.startsWith("/extensions/") ? uri.substring("/extensions".length()) : uri;
        if (path.equals("/" + spec.slug())) path = "/" + spec.slug() + "/";
        return path;
    }

    private Map<String, Object> resultMap(ToolResult r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", r.ok());
        out.put("exitCode", r.exitCode());
        out.put("message", r.message());
        out.put("stdout", r.stdout() == null ? "" : r.stdout());
        out.put("stderr", r.stderr() == null ? "" : r.stderr());
        out.put("durationMs", r.durationMs());
        out.put("executedAt", r.executedAt());
        return out;
    }

    private ToolResult run(String command, int timeoutSeconds) {
        return tools.shell("export PATH=/opt/nebula-tools:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; export KUBECONFIG=/kube/config; " + command, timeoutSeconds);
    }

    private void assertControlEnabled() {
        if (!controlEnabled) throw new IllegalStateException("Extension control is disabled by NEBULAOPS_EXTENSION_CONTROL_ENABLED=false");
    }

    private static String q(String value) {
        return "'" + String.valueOf(value).replace("'", "'\\''") + "'";
    }

    private static int parseInt(String value) {
        try { return value == null || value.isBlank() ? 0 : Integer.parseInt(value.trim()); } catch (Exception ignored) { return 0; }
    }

    private record ExtensionSpec(String slug, String title, String icon, String category, String image, int localProxyPort, String healthPath, String openUrl) {}

    private record CachedExtensionStatus(long cachedAt, Map<String, Object> payload) {}
}

