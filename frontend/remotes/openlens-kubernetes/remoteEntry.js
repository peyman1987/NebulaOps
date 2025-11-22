/* NebulaOps v22.2 · OpenLens Kubernetes MFE — Full Feature Edition */
/* Vanilla Web Component — no build step needed */

const JWT_KEY = 'nebulaops.v22_2.jwt';
function token() { return localStorage.getItem(JWT_KEY) || ''; }

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(token() ? { Authorization: `Bearer ${token()}` } : {}), ...options.headers };
  const res = await fetch(path, { ...options, headers });
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
  const ct = res.headers.get('content-type') || '';
  return ct.includes('application/json') ? res.json() : res.text();
}

async function apiMutation(method, path, body) {
  return api(path, { method, body: body ? JSON.stringify(body) : undefined });
}

function escapeHtml(v) { return String(v ?? '').replace(/[&<>"]/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[m])); }

function statusClass(s) {
  if (!s) return '';
  const st = String(s).toLowerCase();
  if (['running', 'active', 'deployed', 'ready', 'available', 'succeeded', 'healthy'].some(k => st.includes(k))) return 'ok';
  if (['error', 'failed', 'crashloop', 'oomkilled', 'evicted', 'unhealthy'].some(k => st.includes(k))) return 'danger';
  if (['pending', 'terminating', 'unknown', 'waiting', 'warning'].some(k => st.includes(k))) return 'warn';
  return '';
}

/* ─── Simulated Kubernetes data ─── */
/* These mock objects simulate what kubectl/the gateway would return.
   In production the real gateway endpoints are called instead. */
function mockK8sData() {
  const namespaces = ['default', 'kube-system', 'monitoring', 'production', 'staging'];
  const now = new Date();
  const ago = (m) => new Date(now - m * 60000).toISOString();

  const pods = [
    { name: 'api-gateway-7d9f8b-xk2pl', namespace: 'production', status: 'Running', ready: '2/2', restarts: 0, node: 'node-01', ip: '10.0.1.12', labels: { app: 'api-gateway', version: 'v2.1' }, deployment: 'api-gateway', age: ago(340), image: 'nginx:1.27-alpine' },
    { name: 'api-gateway-7d9f8b-mz9qr', namespace: 'production', status: 'Running', ready: '2/2', restarts: 1, node: 'node-02', ip: '10.0.1.13', labels: { app: 'api-gateway', version: 'v2.1' }, deployment: 'api-gateway', age: ago(300), image: 'nginx:1.27-alpine' },
    { name: 'frontend-6c5b8-wq3nt', namespace: 'production', status: 'Running', ready: '1/1', restarts: 0, node: 'node-01', ip: '10.0.1.14', labels: { app: 'frontend', version: 'v22.2' }, deployment: 'frontend', age: ago(180), image: 'nebulaops-frontend:22.2' },
    { name: 'auth-service-5f7d9-pk1mn', namespace: 'production', status: 'CrashLoopBackOff', ready: '0/1', restarts: 7, node: 'node-02', ip: '10.0.1.15', labels: { app: 'auth-service', version: 'v1.4' }, deployment: 'auth-service', age: ago(90), image: 'nebulaops-auth:1.4' },
    { name: 'postgres-0', namespace: 'production', status: 'Running', ready: '1/1', restarts: 0, node: 'node-03', ip: '10.0.1.16', labels: { app: 'postgres', 'statefulset.kubernetes.io/pod-name': 'postgres-0' }, deployment: null, statefulset: 'postgres', age: ago(2880), image: 'postgres:16-alpine' },
    { name: 'prometheus-0', namespace: 'monitoring', status: 'Running', ready: '1/1', restarts: 0, node: 'node-01', ip: '10.0.2.10', labels: { app: 'prometheus' }, deployment: null, statefulset: 'prometheus', age: ago(5760), image: 'prom/prometheus:v2.52' },
    { name: 'grafana-7b4c9-lz8vx', namespace: 'monitoring', status: 'Running', ready: '1/1', restarts: 0, node: 'node-02', ip: '10.0.2.11', labels: { app: 'grafana' }, deployment: 'grafana', age: ago(1440), image: 'grafana/grafana:10.4' },
    { name: 'ai-ops-6d8f7-wr2km', namespace: 'production', status: 'Pending', ready: '0/1', restarts: 0, node: '', ip: '', labels: { app: 'ai-ops', version: 'v1.0' }, deployment: 'ai-ops', age: ago(5), image: 'nebulaops-ai:1.0' },
    { name: 'coredns-787d4-nq5xp', namespace: 'kube-system', status: 'Running', ready: '1/1', restarts: 0, node: 'node-01', ip: '10.96.0.10', labels: { app: 'coredns' }, deployment: 'coredns', age: ago(20160), image: 'registry.k8s.io/coredns:v1.11.1' },
  ];

  const deployments = [
    { name: 'api-gateway', namespace: 'production', ready: '2/2', upToDate: 2, available: 2, replicas: 2, strategy: 'RollingUpdate', image: 'nginx:1.27-alpine', age: ago(340), selector: { app: 'api-gateway' } },
    { name: 'frontend', namespace: 'production', ready: '1/1', upToDate: 1, available: 1, replicas: 1, strategy: 'RollingUpdate', image: 'nebulaops-frontend:22.2', age: ago(180), selector: { app: 'frontend' } },
    { name: 'auth-service', namespace: 'production', ready: '0/1', upToDate: 1, available: 0, replicas: 1, strategy: 'RollingUpdate', image: 'nebulaops-auth:1.4', age: ago(90), selector: { app: 'auth-service' } },
    { name: 'grafana', namespace: 'monitoring', ready: '1/1', upToDate: 1, available: 1, replicas: 1, strategy: 'Recreate', image: 'grafana/grafana:10.4', age: ago(1440), selector: { app: 'grafana' } },
    { name: 'ai-ops', namespace: 'production', ready: '0/1', upToDate: 1, available: 0, replicas: 1, strategy: 'RollingUpdate', image: 'nebulaops-ai:1.0', age: ago(5), selector: { app: 'ai-ops' } },
    { name: 'coredns', namespace: 'kube-system', ready: '1/1', upToDate: 1, available: 1, replicas: 1, strategy: 'RollingUpdate', image: 'registry.k8s.io/coredns:v1.11.1', age: ago(20160), selector: { app: 'coredns' } },
  ];

  const services = [
    { name: 'api-gateway-svc', namespace: 'production', type: 'LoadBalancer', clusterIP: '10.96.1.10', externalIP: '203.0.113.10', ports: '80:31080/TCP, 443:31443/TCP', selector: { app: 'api-gateway' }, age: ago(340) },
    { name: 'frontend-svc', namespace: 'production', type: 'ClusterIP', clusterIP: '10.96.1.11', externalIP: '', ports: '4200:32000/TCP', selector: { app: 'frontend' }, age: ago(180) },
    { name: 'auth-service-svc', namespace: 'production', type: 'ClusterIP', clusterIP: '10.96.1.12', externalIP: '', ports: '8081:32001/TCP', selector: { app: 'auth-service' }, age: ago(90) },
    { name: 'postgres-svc', namespace: 'production', type: 'ClusterIP', clusterIP: '10.96.1.13', externalIP: '', ports: '5432/TCP', selector: { 'app': 'postgres' }, age: ago(2880) },
    { name: 'prometheus-svc', namespace: 'monitoring', type: 'ClusterIP', clusterIP: '10.96.2.10', externalIP: '', ports: '9090/TCP', selector: { app: 'prometheus' }, age: ago(5760) },
    { name: 'grafana-svc', namespace: 'monitoring', type: 'NodePort', clusterIP: '10.96.2.11', externalIP: '', ports: '3000:30300/TCP', selector: { app: 'grafana' }, age: ago(1440) },
    { name: 'kubernetes', namespace: 'default', type: 'ClusterIP', clusterIP: '10.96.0.1', externalIP: '', ports: '443/TCP', selector: {}, age: ago(20160) },
  ];

  const replicasets = [
    { name: 'api-gateway-7d9f8b', namespace: 'production', desired: 2, current: 2, ready: 2, deployment: 'api-gateway', age: ago(340) },
    { name: 'api-gateway-8e2c1a', namespace: 'production', desired: 0, current: 0, ready: 0, deployment: 'api-gateway', age: ago(1200) },
    { name: 'frontend-6c5b8', namespace: 'production', desired: 1, current: 1, ready: 1, deployment: 'frontend', age: ago(180) },
    { name: 'auth-service-5f7d9', namespace: 'production', desired: 1, current: 1, ready: 0, deployment: 'auth-service', age: ago(90) },
    { name: 'grafana-7b4c9', namespace: 'monitoring', desired: 1, current: 1, ready: 1, deployment: 'grafana', age: ago(1440) },
    { name: 'ai-ops-6d8f7', namespace: 'production', desired: 1, current: 1, ready: 0, deployment: 'ai-ops', age: ago(5) },
  ];

  const configmaps = [
    { name: 'api-gateway-config', namespace: 'production', keys: 3, age: ago(340) },
    { name: 'prometheus-config', namespace: 'monitoring', keys: 5, age: ago(5760) },
    { name: 'grafana-datasources', namespace: 'monitoring', keys: 2, age: ago(1440) },
    { name: 'kube-proxy', namespace: 'kube-system', keys: 1, age: ago(20160) },
    { name: 'coredns', namespace: 'kube-system', keys: 1, age: ago(20160) },
  ];

  const secrets = [
    { name: 'postgres-credentials', namespace: 'production', type: 'Opaque', keys: 2, age: ago(2880) },
    { name: 'registry-pull-secret', namespace: 'production', type: 'kubernetes.io/dockerconfigjson', keys: 1, age: ago(5760) },
    { name: 'tls-cert', namespace: 'production', type: 'kubernetes.io/tls', keys: 2, age: ago(720) },
    { name: 'keycloak-admin', namespace: 'production', type: 'Opaque', keys: 3, age: ago(340) },
  ];

  const events = [
    { type: 'Warning', reason: 'BackOff', message: 'Back-off restarting failed container auth-service', object: 'Pod/auth-service-5f7d9-pk1mn', namespace: 'production', count: 14, age: ago(3) },
    { type: 'Normal', reason: 'Scheduled', message: 'Successfully assigned production/ai-ops-6d8f7-wr2km to node-02', object: 'Pod/ai-ops-6d8f7-wr2km', namespace: 'production', count: 1, age: ago(5) },
    { type: 'Warning', reason: 'Failed', message: 'Failed to pull image "nebulaops-ai:1.0": rpc error: code = Unknown', object: 'Pod/ai-ops-6d8f7-wr2km', namespace: 'production', count: 3, age: ago(5) },
    { type: 'Normal', reason: 'Pulling', message: 'Pulling image "nginx:1.27-alpine"', object: 'Pod/api-gateway-7d9f8b-xk2pl', namespace: 'production', count: 1, age: ago(340) },
    { type: 'Normal', reason: 'Started', message: 'Started container api-gateway', object: 'Pod/api-gateway-7d9f8b-xk2pl', namespace: 'production', count: 1, age: ago(338) },
  ];

  const nodes = [
    { name: 'node-01', status: 'Ready', roles: 'control-plane', version: 'v1.30.2', os: 'linux/amd64', cpu: '4', memory: '16Gi', pods: 3, age: ago(20160) },
    { name: 'node-02', status: 'Ready', roles: 'worker', version: 'v1.30.2', os: 'linux/amd64', cpu: '8', memory: '32Gi', pods: 4, age: ago(18000) },
    { name: 'node-03', status: 'Ready', roles: 'worker', version: 'v1.30.2', os: 'linux/amd64', cpu: '8', memory: '32Gi', pods: 2, age: ago(16800) },
  ];

  const statefulsets = [
    { name: 'postgres', namespace: 'production', ready: '1/1', replicas: 1, serviceName: 'postgres-svc', age: ago(2880) },
    { name: 'prometheus', namespace: 'monitoring', ready: '1/1', replicas: 1, serviceName: 'prometheus-svc', age: ago(5760) },
  ];

  const helm = [
    { name: 'monitoring-stack', namespace: 'monitoring', chart: 'kube-prometheus-stack-58.4.0', status: 'deployed', revision: 3, age: ago(1440) },
    { name: 'cert-manager', namespace: 'cert-manager', chart: 'cert-manager-v1.14.5', status: 'deployed', revision: 1, age: ago(5760) },
    { name: 'ingress-nginx', namespace: 'ingress-nginx', chart: 'ingress-nginx-4.10.1', status: 'deployed', revision: 2, age: ago(2880) },
    { name: 'nebulaops-app', namespace: 'production', chart: 'nebulaops-22.2.0', status: 'deployed', revision: 14, age: ago(180) },
  ];

  return { namespaces, pods, deployments, services, replicasets, configmaps, secrets, events, nodes, statefulsets, helm };
}

function generateYaml(kind, name, namespace, data) {
  const labels = Object.entries(data.labels || data.selector || {}).map(([k, v]) => `    ${k}: ${v}`).join('\n');
  const ts = new Date().toISOString();
  switch (kind) {
    case 'Pod': return `apiVersion: v1
kind: Pod
metadata:
  name: ${name}
  namespace: ${namespace}
  labels:
${labels || '    app: ' + name}
  creationTimestamp: "${data.age}"
spec:
  nodeName: ${data.node || 'node-01'}
  containers:
  - name: ${name.split('-')[0]}
    image: ${data.image || 'nginx:latest'}
    imagePullPolicy: IfNotPresent
    resources:
      requests:
        cpu: 100m
        memory: 128Mi
      limits:
        cpu: 500m
        memory: 512Mi
  restartPolicy: Always
  terminationGracePeriodSeconds: 30
status:
  phase: ${data.status}
  podIP: ${data.ip || ''}
  hostIP: 10.0.1.1`;
    case 'Deployment': return `apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${name}
  namespace: ${namespace}
  labels:
${labels || '    app: ' + name}
  creationTimestamp: "${data.age}"
spec:
  replicas: ${data.replicas || 1}
  selector:
    matchLabels:
${labels || '      app: ' + name}
  strategy:
    type: ${data.strategy || 'RollingUpdate'}
    rollingUpdate:
      maxSurge: 25%
      maxUnavailable: 0
  template:
    metadata:
      labels:
${labels || '        app: ' + name}
    spec:
      containers:
      - name: ${name}
        image: ${data.image || 'nginx:latest'}
        imagePullPolicy: IfNotPresent
        resources:
          requests:
            cpu: 100m
            memory: 128Mi
          limits:
            cpu: 500m
            memory: 512Mi
status:
  availableReplicas: ${data.available || 0}
  readyReplicas: ${(data.ready || '0/0').split('/')[0]}
  replicas: ${data.replicas || 1}`;
    case 'Service': return `apiVersion: v1
kind: Service
metadata:
  name: ${name}
  namespace: ${namespace}
  creationTimestamp: "${data.age}"
spec:
  type: ${data.type || 'ClusterIP'}
  clusterIP: ${data.clusterIP || ''}
  selector:
${Object.entries(data.selector || {}).map(([k,v]) => `    ${k}: ${v}`).join('\n') || '    app: ' + name}
  ports:
${(data.ports || '80/TCP').split(',').map(p => `  - port: ${p.trim().split(':')[0].replace('/TCP','').replace('/UDP','')}\n    targetPort: ${p.trim().split(':')[0].replace('/TCP','').replace('/UDP','')}`).join('\n')}
status:
  loadBalancer: {}`;
    default: return `# ${kind} ${name} YAML\napiVersion: v1\nkind: ${kind}\nmetadata:\n  name: ${name}\n  namespace: ${namespace}`;
  }
}

function generateLogs(podName, status) {
  const lines = [];
  const ts = () => new Date().toISOString();
  if (status === 'CrashLoopBackOff') {
    lines.push(`${ts()} INFO  Starting application...`);
    lines.push(`${ts()} INFO  Loading configuration from /etc/config/app.yaml`);
    lines.push(`${ts()} ERROR Failed to connect to database: connection refused (postgres:5432)`);
    lines.push(`${ts()} ERROR Health check failed: dependency unavailable`);
    lines.push(`${ts()} FATAL Application startup failed with exit code 1`);
    lines.push(`${ts()} WARN  Restarting container... (attempt 7)`);
  } else if (status === 'Running') {
    lines.push(`${ts()} INFO  Application started successfully`);
    lines.push(`${ts()} INFO  Listening on :8080`);
    lines.push(`${ts()} INFO  Connected to database`);
    lines.push(`${ts()} INFO  GET /health 200 OK 2ms`);
    lines.push(`${ts()} INFO  GET /api/v1/users 200 OK 45ms`);
    lines.push(`${ts()} INFO  POST /api/v1/auth/token 200 OK 112ms`);
    lines.push(`${ts()} INFO  GET /api/kubernetes/snapshot 200 OK 234ms`);
    lines.push(`${ts()} INFO  Metrics scraped by Prometheus`);
  } else {
    lines.push(`${ts()} INFO  Container initializing...`);
    lines.push(`${ts()} INFO  Waiting for dependencies...`);
  }
  return lines.join('\n');
}

/* ─── CSS ─── */
const CSS = `
  :host{display:block;color:#eef6ff;font-family:Inter,ui-sans-serif,system-ui,-apple-system,sans-serif;--blue:#7adfff;--purple:#a78bfa;--green:#34d399;--red:#f87171;--amber:#fbbf24;--bg:rgba(3,7,18,.92);--card:rgba(255,255,255,.055);--border:rgba(140,180,255,.15)}
  *{box-sizing:border-box;margin:0;padding:0} button{font:inherit;cursor:pointer;color:inherit} a{color:inherit;text-decoration:none}
  .shell{display:grid;grid-template-rows:auto 1fr;min-height:650px;background:radial-gradient(circle at top left,rgba(0,216,255,.08),transparent 35%),radial-gradient(circle at right top,rgba(139,92,246,.08),transparent 30%),var(--bg)}
  /* Topbar */
  .topbar{display:flex;align-items:center;gap:10px;padding:10px 16px;border-bottom:1px solid var(--border);background:rgba(0,0,0,.25);flex-wrap:wrap}
  .topbar-title{display:flex;align-items:center;gap:8px;margin-right:12px}.topbar-title .cube{width:34px;height:34px;border-radius:10px;background:linear-gradient(145deg,#53e7ff,#7e61ff 62%,#20145c);display:grid;place-items:center;font-weight:900;font-size:11px;transform:perspective(200px) rotateX(8deg) rotateY(-10deg)}
  .topbar-title h2{font-size:15px;font-weight:700} .topbar-title small{color:var(--blue);font-size:11px;font-weight:600;letter-spacing:.12em}
  .ns-select{background:rgba(255,255,255,.08);border:1px solid var(--border);border-radius:8px;padding:5px 10px;color:#eef6ff;font-size:13px;cursor:pointer}
  .cluster-badge{background:rgba(122,223,255,.12);border:1px solid rgba(122,223,255,.25);border-radius:20px;padding:4px 12px;font-size:12px;color:var(--blue);font-weight:600}
  .refresh-btn{background:rgba(255,255,255,.07);border:1px solid var(--border);border-radius:8px;padding:5px 12px;font-size:12px;transition:.15s}.refresh-btn:hover{border-color:var(--blue);color:var(--blue)}
  /* Layout */
  .body{display:grid;grid-template-columns:220px 1fr;height:100%;overflow:hidden}
  /* Sidetree */
  .sidetree{background:rgba(0,0,0,.22);border-right:1px solid var(--border);overflow-y:auto;padding:10px 0}
  .tree-section{margin-bottom:2px}
  .tree-header{padding:6px 14px 4px;font-size:10px;font-weight:800;letter-spacing:.15em;color:rgba(122,223,255,.6);text-transform:uppercase;display:flex;align-items:center;justify-content:space-between}
  .tree-header .count{background:rgba(122,223,255,.14);border-radius:10px;padding:1px 7px;font-size:11px;font-weight:700;color:var(--blue)}
  .tree-item{display:flex;align-items:center;gap:8px;padding:7px 14px;font-size:13px;cursor:pointer;border-radius:0;transition:.12s;border-left:2px solid transparent;position:relative}
  .tree-item:hover{background:rgba(255,255,255,.06);border-left-color:rgba(122,223,255,.4)}
  .tree-item.active{background:rgba(122,223,255,.1);border-left-color:var(--blue);color:#fff}
  .tree-item .icon{width:18px;text-align:center;font-size:13px;flex-shrink:0}
  .tree-item .label{flex:1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
  .tree-item .badge{font-size:10px;padding:1px 6px;border-radius:8px;font-weight:700;flex-shrink:0}
  .tree-item .badge.ok{background:rgba(52,211,153,.15);color:var(--green)}
  .tree-item .badge.danger{background:rgba(248,113,113,.15);color:var(--red)}
  .tree-item .badge.warn{background:rgba(251,191,36,.15);color:var(--amber)}
  /* Main panel */
  .panel{overflow-y:auto;padding:20px}
  /* Resource table */
  .panel-header{display:flex;align-items:center;gap:12px;margin-bottom:16px;flex-wrap:wrap}
  .panel-header h3{font-size:18px;font-weight:700} .panel-header small{color:#92a3ca;font-size:13px}
  .search-box{background:rgba(255,255,255,.07);border:1px solid var(--border);border-radius:8px;padding:6px 12px;color:#eef6ff;font-size:13px;width:220px}.search-box:focus{outline:none;border-color:var(--blue)}
  .action-btn{background:rgba(255,255,255,.07);border:1px solid var(--border);border-radius:8px;padding:6px 13px;font-size:12px;font-weight:600;transition:.15s}
  .action-btn:hover{border-color:var(--blue);color:var(--blue)}
  .action-btn.danger-btn:hover{border-color:var(--red);color:var(--red)}
  .action-btn.green-btn{border-color:rgba(52,211,153,.3);color:var(--green)}.action-btn.green-btn:hover{border-color:var(--green)}
  .tbl{width:100%;border-collapse:collapse;font-size:13px}
  .tbl th{text-align:left;padding:7px 10px;color:#92a3ca;font-weight:600;font-size:11px;letter-spacing:.08em;text-transform:uppercase;border-bottom:1px solid var(--border)}
  .tbl td{padding:8px 10px;border-bottom:1px solid rgba(140,180,255,.07);vertical-align:middle}
  .tbl tr:hover td{background:rgba(255,255,255,.03)}
  .tbl tr.selected td{background:rgba(122,223,255,.07)}
  .name-link{color:var(--blue);cursor:pointer;font-weight:600}.name-link:hover{text-decoration:underline}
  .pill{display:inline-block;border-radius:999px;padding:3px 9px;font-size:11px;font-weight:700;white-space:nowrap}
  .pill.ok{background:rgba(52,211,153,.15);color:var(--green)}.pill.danger{background:rgba(248,113,113,.15);color:var(--red)}.pill.warn{background:rgba(251,191,36,.15);color:var(--amber)}.pill.neutral{background:rgba(122,223,255,.1);color:var(--blue)}
  /* Detail panel */
  .detail-view{display:grid;gap:14px}
  .detail-header{display:flex;align-items:flex-start;gap:14px;padding:16px;background:var(--card);border:1px solid var(--border);border-radius:16px}
  .detail-header .kind-badge{background:linear-gradient(145deg,#7e61ff,#53e7ff);border-radius:10px;padding:10px 14px;font-weight:900;font-size:12px;letter-spacing:.05em;flex-shrink:0}
  .detail-header .info{flex:1}
  .detail-header h3{font-size:19px;font-weight:700;margin-bottom:4px}
  .detail-header .meta{color:#92a3ca;font-size:13px;display:flex;gap:16px;flex-wrap:wrap;margin-top:6px}
  .detail-tabs{display:flex;gap:2px;border-bottom:1px solid var(--border);margin-bottom:14px}
  .tab{padding:8px 16px;font-size:13px;font-weight:600;cursor:pointer;border-bottom:2px solid transparent;color:#92a3ca;transition:.15s}
  .tab:hover{color:#eef6ff}.tab.active{color:var(--blue);border-bottom-color:var(--blue)}
  .tab-content{display:none}.tab-content.active{display:block}
  .info-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:10px}
  .info-card{background:var(--card);border:1px solid var(--border);border-radius:12px;padding:12px}
  .info-card .ic-label{font-size:10px;font-weight:700;letter-spacing:.12em;text-transform:uppercase;color:rgba(122,223,255,.6);margin-bottom:5px}
  .info-card .ic-value{font-size:14px;font-weight:600;word-break:break-all}
  .label-chips{display:flex;flex-wrap:wrap;gap:6px;margin-top:6px}
  .label-chip{background:rgba(167,139,250,.12);border:1px solid rgba(167,139,250,.2);border-radius:6px;padding:3px 8px;font-size:11px;font-family:ui-monospace,monospace;color:#c4b5fd}
  /* Related resources */
  .related-section{margin-top:14px}
  .related-section h4{font-size:13px;font-weight:700;color:var(--blue);letter-spacing:.05em;text-transform:uppercase;margin-bottom:10px;display:flex;align-items:center;gap:8px}
  .related-cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:10px}
  .related-card{background:var(--card);border:1px solid var(--border);border-radius:12px;padding:12px;cursor:pointer;transition:.15s}
  .related-card:hover{border-color:var(--blue);background:rgba(122,223,255,.07)}
  .related-card .rc-kind{font-size:10px;font-weight:800;letter-spacing:.12em;text-transform:uppercase;color:var(--purple);margin-bottom:4px}
  .related-card .rc-name{font-size:14px;font-weight:700;margin-bottom:2px}
  .related-card .rc-meta{font-size:12px;color:#92a3ca}
  /* Action buttons row */
  .actions-row{display:flex;gap:8px;flex-wrap:wrap;padding:12px;background:var(--card);border:1px solid var(--border);border-radius:12px}
  .act{border:1px solid var(--border);background:rgba(255,255,255,.05);border-radius:8px;padding:7px 14px;font-size:12px;font-weight:600;transition:.15s;display:flex;align-items:center;gap:6px}
  .act:hover{transform:translateY(-1px)}
  .act.act-primary{border-color:rgba(122,223,255,.35);color:var(--blue)}.act.act-primary:hover{background:rgba(122,223,255,.12)}
  .act.act-warn{border-color:rgba(251,191,36,.3);color:var(--amber)}.act.act-warn:hover{background:rgba(251,191,36,.1)}
  .act.act-danger{border-color:rgba(248,113,113,.3);color:var(--red)}.act.act-danger:hover{background:rgba(248,113,113,.12)}
  .act.act-green{border-color:rgba(52,211,153,.3);color:var(--green)}.act.act-green:hover{background:rgba(52,211,153,.1)}
  /* YAML editor */
  .yaml-editor{width:100%;min-height:360px;background:rgba(0,0,0,.3);border:1px solid var(--border);border-radius:12px;padding:14px;color:#bff7ff;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:12.5px;line-height:1.6;resize:vertical;tab-size:2}
  .yaml-editor:focus{outline:none;border-color:var(--blue)}
  .yaml-actions{display:flex;gap:8px;margin-top:10px;align-items:center}
  .yaml-status{font-size:12px;color:var(--green);display:none}
  .yaml-status.show{display:block}
  /* Logs */
  .log-area{background:rgba(0,0,0,.4);border:1px solid var(--border);border-radius:12px;padding:14px;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px;line-height:1.7;max-height:400px;overflow-y:auto;color:#bff7ff;white-space:pre-wrap}
  .log-line.error{color:var(--red)}.log-line.warn{color:var(--amber)}.log-line.info{color:#bff7ff}.log-line.fatal{color:var(--red);font-weight:700}
  .log-controls{display:flex;gap:8px;margin-bottom:10px;align-items:center}
  /* Scale modal */
  .modal-backdrop{position:fixed;inset:0;background:rgba(0,0,0,.6);display:flex;align-items:center;justify-content:center;z-index:9999}
  .modal{background:#0c1229;border:1px solid var(--border);border-radius:18px;padding:24px;min-width:340px;box-shadow:0 30px 80px rgba(0,0,0,.6)}
  .modal h3{font-size:16px;font-weight:700;margin-bottom:14px}
  .modal-input{background:rgba(255,255,255,.08);border:1px solid var(--border);border-radius:8px;padding:8px 12px;color:#eef6ff;font-size:15px;width:100%;margin:8px 0 14px}
  .modal-input:focus{outline:none;border-color:var(--blue)}
  .modal-actions{display:flex;gap:10px;justify-content:flex-end}
  /* Events */
  .event-row{display:flex;gap:12px;padding:10px;border-bottom:1px solid rgba(140,180,255,.07);align-items:flex-start}
  .event-type{font-size:11px;font-weight:700;padding:3px 8px;border-radius:6px;flex-shrink:0;margin-top:2px}
  .event-type.Warning{background:rgba(251,191,36,.15);color:var(--amber)}.event-type.Normal{background:rgba(52,211,153,.12);color:var(--green)}
  .event-msg{font-size:13px;line-height:1.5} .event-obj{font-size:11px;color:#92a3ca;margin-top:2px}
  /* Nodes */
  .node-card{background:var(--card);border:1px solid var(--border);border-radius:14px;padding:14px;margin-bottom:10px}
  .node-card h4{font-size:15px;font-weight:700;margin-bottom:8px}
  .node-stats{display:flex;gap:20px;flex-wrap:wrap}
  .node-stat{font-size:12px;color:#92a3ca} .node-stat b{color:#eef6ff;font-size:14px}
  /* Toast */
  .toast{position:fixed;bottom:24px;right:24px;background:#0c1229;border:1px solid var(--border);border-radius:12px;padding:12px 18px;font-size:13px;font-weight:600;z-index:9999;transform:translateY(100px);opacity:0;transition:.3s;box-shadow:0 10px 40px rgba(0,0,0,.4)}
  .toast.show{transform:translateY(0);opacity:1}
  .toast.toast-ok{border-color:rgba(52,211,153,.4);color:var(--green)}
  .toast.toast-err{border-color:rgba(248,113,113,.4);color:var(--red)}
  /* Overview cards */
  .overview-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:12px;margin-bottom:18px}
  .ov-card{background:var(--card);border:1px solid var(--border);border-radius:14px;padding:14px;text-align:center}
  .ov-card .ov-num{font-size:32px;font-weight:900;color:#fff;line-height:1}
  .ov-card .ov-label{font-size:11px;font-weight:700;letter-spacing:.1em;color:#92a3ca;margin-top:4px;text-transform:uppercase}
  .ov-card .ov-sub{font-size:12px;color:var(--red);margin-top:3px}
  /* Helm */
  .helm-card{background:var(--card);border:1px solid var(--border);border-radius:14px;padding:14px;margin-bottom:10px;display:flex;align-items:center;gap:14px}
  .helm-icon{font-size:28px} .helm-info{flex:1} .helm-info h4{font-size:14px;font-weight:700} .helm-info small{font-size:12px;color:#92a3ca}
  /* Empty */
  .empty{padding:30px;text-align:center;color:#92a3ca;font-size:14px}
  @media(max-width:860px){.body{grid-template-columns:1fr}.sidetree{display:none}}
`;

class NebulaopsMfeOpenlensKubernetes extends HTMLElement {
  constructor() {
    super();
    this.attachShadow({ mode: 'open' });
    const data = mockK8sData();
    this._state = {
      activeSection: 'overview',
      activeNs: 'all',
      search: '',
      selectedItem: null,
      activeTab: 'overview',
      yamlSaved: {},
      scaleModal: null,
      confirmModal: null,
      toast: null,
      ...data
    };
  }

  connectedCallback() { this.render(); }

  get ns() { return this._state.activeNs; }

  filteredBy(arr, nsKey = 'namespace') {
    const items = this.ns === 'all' ? arr : arr.filter(i => i[nsKey] === this.ns);
    const s = this._state.search.toLowerCase();
    if (!s) return items;
    return items.filter(i => JSON.stringify(i).toLowerCase().includes(s));
  }

  showToast(msg, type = 'ok') {
    this._state.toast = { msg, type };
    this.render();
    setTimeout(() => { this._state.toast = null; this.render(); }, 2800);
  }

  navigate(section, item = null) {
    this._state.activeSection = section;
    this._state.selectedItem = item;
    this._state.activeTab = 'overview';
    this._state.search = '';
    this.render();
  }

  navigateToRelated(kind, name, namespace) {
    const kindToSection = { Pod: 'pods', Deployment: 'deployments', Service: 'services', ReplicaSet: 'replicasets' };
    const section = kindToSection[kind];
    if (!section) return;
    const data = this._state[section];
    const item = data?.find(i => i.name === name && (!namespace || i.namespace === namespace));
    if (item) { this._state.activeNs = namespace || 'all'; this.navigate(section, item); }
  }

  getRelatedResources(item) {
    if (!item) return {};
    const related = {};
    // Pod → Deployment + Service + ReplicaSet
    if (item.deployment) {
      related.deployment = this._state.deployments.find(d => d.name === item.deployment && d.namespace === item.namespace);
    }
    if (item.statefulset) {
      related.statefulset = this._state.statefulsets.find(s => s.name === item.statefulset && s.namespace === item.namespace);
    }
    // Pod → Service (by label matching)
    related.services = this._state.services.filter(svc => {
      if (svc.namespace !== item.namespace) return false;
      return Object.entries(svc.selector || {}).every(([k, v]) => item.labels?.[k] === v);
    });
    // Pod → ReplicaSet
    if (item.deployment) {
      related.replicasets = this._state.replicasets.filter(rs => rs.deployment === item.deployment && rs.namespace === item.namespace);
    }
    // Deployment → Pods + Services + ReplicaSets
    if (!item.ip && item.replicas !== undefined) { // it's a deployment
      related.pods = this._state.pods.filter(p => p.deployment === item.name && p.namespace === item.namespace);
      related.services = this._state.services.filter(svc => {
        if (svc.namespace !== item.namespace) return false;
        return Object.entries(svc.selector || {}).every(([k, v]) => item.selector?.[k] === v);
      });
      related.replicasets = this._state.replicasets.filter(rs => rs.deployment === item.name && rs.namespace === item.namespace);
    }
    // Service → Pods
    if (item.clusterIP) {
      related.pods = this._state.pods.filter(p => {
        if (p.namespace !== item.namespace) return false;
        return Object.entries(item.selector || {}).every(([k, v]) => p.labels?.[k] === v);
      });
    }
    // ReplicaSet → Deployment + Pods
    if (item.desired !== undefined && item.deployment) {
      related.deployment = this._state.deployments.find(d => d.name === item.deployment && d.namespace === item.namespace);
      related.pods = this._state.pods.filter(p => p.name.startsWith(item.name));
    }
    return related;
  }

  /* ─── Action handlers ─── */
  restartPod(pod) {
    const idx = this._state.pods.findIndex(p => p.name === pod.name);
    if (idx >= 0) {
      this._state.pods[idx] = { ...pod, restarts: pod.restarts + 1, status: 'Running', age: new Date().toISOString() };
      if (this._state.selectedItem?.name === pod.name) this._state.selectedItem = this._state.pods[idx];
    }
    this.showToast(`✅ Pod ${pod.name} restarted`);
  }

  deletePod(pod) {
    this._state.pods = this._state.pods.filter(p => p.name !== pod.name);
    if (this._state.selectedItem?.name === pod.name) { this._state.selectedItem = null; }
    this._state.confirmModal = null;
    this.showToast(`🗑️ Pod ${pod.name} eliminato`);
  }

  scaleDeployment(dep, replicas) {
    const idx = this._state.deployments.findIndex(d => d.name === dep.name);
    if (idx >= 0) {
      this._state.deployments[idx] = { ...dep, replicas, ready: `${Math.min(replicas, parseInt(dep.ready))}/${replicas}`, available: replicas };
      if (this._state.selectedItem?.name === dep.name) this._state.selectedItem = this._state.deployments[idx];
    }
    this._state.scaleModal = null;
    this.showToast(`⚖️ ${dep.name} scalato a ${replicas} replica${replicas !== 1 ? 's' : ''}`);
  }

  restartDeployment(dep) {
    this.showToast(`♻️ Deployment ${dep.name} restarted (rolling restart)`);
  }

  deleteDeployment(dep) {
    this._state.deployments = this._state.deployments.filter(d => d.name !== dep.name);
    if (this._state.selectedItem?.name === dep.name) this._state.selectedItem = null;
    this._state.confirmModal = null;
    this.showToast(`🗑️ Deployment ${dep.name} eliminato`);
  }

  saveYaml(key, value) {
    this._state.yamlSaved[key] = { value, savedAt: new Date().toISOString() };
    this.showToast(`💾 YAML salvato per ${key}`);
  }

  /* ─── Render helpers ─── */
  renderTreeBadge(items, nsFilter) {
    const filtered = nsFilter === 'all' ? items : items.filter(i => i.namespace === nsFilter);
    const hasIssue = filtered.some(i => i.status === 'CrashLoopBackOff' || i.status === 'Error' || i.status === 'Failed' || (i.ready && i.ready.split('/')[0] === '0'));
    const hasPending = filtered.some(i => i.status === 'Pending' || i.status === 'Terminating');
    const cls = hasIssue ? 'danger' : hasPending ? 'warn' : 'ok';
    return `<span class="badge ${cls}">${filtered.length}</span>`;
  }

  renderSideTree() {
    const ns = this.ns;
    const sections = [
      { label: 'Cluster', items: [] },
      { label: 'Workloads', items: [
        { id: 'pods', icon: '⬡', label: 'Pods', badge: this.renderTreeBadge(this._state.pods, ns) },
        { id: 'deployments', icon: '🚀', label: 'Deployments', badge: this.renderTreeBadge(this._state.deployments, ns) },
        { id: 'statefulsets', icon: '💾', label: 'StatefulSets', badge: `<span class="badge ok">${(ns==='all'?this._state.statefulsets:this._state.statefulsets.filter(s=>s.namespace===ns)).length}</span>` },
        { id: 'replicasets', icon: '🔄', label: 'ReplicaSets', badge: `<span class="badge neutral">${(ns==='all'?this._state.replicasets:this._state.replicasets.filter(s=>s.namespace===ns)).length}</span>` },
      ]},
      { label: 'Network', items: [
        { id: 'services', icon: '🌐', label: 'Services', badge: `<span class="badge neutral">${(ns==='all'?this._state.services:this._state.services.filter(s=>s.namespace===ns)).length}</span>` },
      ]},
      { label: 'Config', items: [
        { id: 'configmaps', icon: '📋', label: 'ConfigMaps', badge: `<span class="badge neutral">${(ns==='all'?this._state.configmaps:this._state.configmaps.filter(s=>s.namespace===ns)).length}</span>` },
        { id: 'secrets', icon: '🔐', label: 'Secrets', badge: `<span class="badge neutral">${(ns==='all'?this._state.secrets:this._state.secrets.filter(s=>s.namespace===ns)).length}</span>` },
      ]},
      { label: 'Infrastructure', items: [
        { id: 'nodes', icon: '🖥️', label: 'Nodes', badge: `<span class="badge ok">${this._state.nodes.length}</span>` },
        { id: 'events', icon: '⚡', label: 'Events', badge: `<span class="badge warn">${this._state.events.length}</span>` },
      ]},
      { label: 'Helm', items: [
        { id: 'helm', icon: '⛵', label: 'Releases', badge: `<span class="badge ok">${this._state.helm.length}</span>` },
      ]},
    ];

    return `
      <div class="tree-item ${this._state.activeSection==='overview'?'active':''}" data-nav="overview">
        <span class="icon">🏠</span><span class="label">Overview</span>
      </div>
      ${sections.map(sec => `
        <div class="tree-section">
          ${sec.label !== 'Cluster' ? `<div class="tree-header">${sec.label}</div>` : ''}
          ${sec.items.map(item => `
            <div class="tree-item ${this._state.activeSection===item.id?'active':''}" data-nav="${item.id}">
              <span class="icon">${item.icon}</span>
              <span class="label">${item.label}</span>
              ${item.badge}
            </div>
          `).join('')}
        </div>
      `).join('')}
    `;
  }

  renderOverview() {
    const pods = this.filteredBy(this._state.pods);
    const criticalPods = pods.filter(p => p.status === 'CrashLoopBackOff' || p.status === 'Error' || p.status === 'Failed');
    const pendingPods = pods.filter(p => p.status === 'Pending');
    const deployments = this.filteredBy(this._state.deployments);
    const unhealthyDeps = deployments.filter(d => d.ready.split('/')[0] === '0');
    return `
      <div class="panel-header"><h3>🏠 Cluster Overview</h3><small>Namespace: ${this.ns}</small></div>
      <div class="overview-grid">
        <div class="ov-card"><div class="ov-num">${pods.length}</div><div class="ov-label">Pods</div>${criticalPods.length?`<div class="ov-sub">⚠️ ${criticalPods.length} critical</div>`:''}</div>
        <div class="ov-card"><div class="ov-num">${deployments.length}</div><div class="ov-label">Deployments</div>${unhealthyDeps.length?`<div class="ov-sub">⚠️ ${unhealthyDeps.length} down</div>`:''}</div>
        <div class="ov-card"><div class="ov-num">${this.filteredBy(this._state.services).length}</div><div class="ov-label">Services</div></div>
        <div class="ov-card"><div class="ov-num">${this._state.nodes.length}</div><div class="ov-label">Nodes</div></div>
        <div class="ov-card"><div class="ov-num">${this._state.helm.length}</div><div class="ov-label">Helm Releases</div></div>
        <div class="ov-card"><div class="ov-num">${this._state.events.filter(e=>e.type==='Warning').length}</div><div class="ov-label">Warnings</div></div>
      </div>
      ${criticalPods.length ? `
        <div style="margin-bottom:14px">
          <div class="related-section"><h4>🔴 Critical Pods</h4></div>
          <table class="tbl"><thead><tr><th>Name</th><th>Namespace</th><th>Status</th><th>Restarts</th><th>Actions</th></tr></thead>
          <tbody>${criticalPods.map(p => `<tr>
            <td><span class="name-link" data-nav="pods" data-item="${escapeHtml(p.name)}">${escapeHtml(p.name)}</span></td>
            <td>${escapeHtml(p.namespace)}</td>
            <td><span class="pill danger">${escapeHtml(p.status)}</span></td>
            <td>${p.restarts}</td>
            <td><button class="act act-primary btn-restart" data-pod="${escapeHtml(p.name)}">↺ Restart</button></td>
          </tr>`).join('')}</tbody></table>
        </div>
      ` : ''}
      <div style="margin-bottom:14px">
        <div class="related-section"><h4>⚡ Recent Events</h4></div>
        ${this._state.events.slice(0,5).map(e => `<div class="event-row">
          <span class="event-type ${e.type}">${e.type}</span>
          <div><div class="event-msg">${escapeHtml(e.message)}</div><div class="event-obj">${escapeHtml(e.object)} · ${escapeHtml(e.namespace)}</div></div>
        </div>`).join('')}
      </div>
      <div class="related-section"><h4>⛵ Helm Releases</h4></div>
      ${this._state.helm.map(h => `<div class="helm-card">
        <div class="helm-icon">⛵</div>
        <div class="helm-info"><h4>${escapeHtml(h.name)}</h4><small>${escapeHtml(h.chart)} · rev ${h.revision} · ${escapeHtml(h.namespace)}</small></div>
        <span class="pill ok">${escapeHtml(h.status)}</span>
      </div>`).join('')}
    `;
  }

  renderPodsTable() {
    const pods = this.filteredBy(this._state.pods);
    if (pods.length === 0) return `<div class="empty">No pod found</div>`;
    return `<table class="tbl">
      <thead><tr><th>Name</th><th>Namespace</th><th>Ready</th><th>Status</th><th>Restarts</th><th>Node</th><th>IP</th><th>Actions</th></tr></thead>
      <tbody>${pods.map(p => `<tr class="${this._state.selectedItem?.name===p.name?'selected':''}">
        <td><span class="name-link" data-detail="${escapeHtml(p.name)}">${escapeHtml(p.name)}</span></td>
        <td><span class="pill neutral">${escapeHtml(p.namespace)}</span></td>
        <td>${escapeHtml(p.ready)}</td>
        <td><span class="pill ${statusClass(p.status)}">${escapeHtml(p.status)}</span></td>
        <td>${p.restarts > 0 ? `<span style="color:var(--amber)">${p.restarts}</span>` : p.restarts}</td>
        <td>${escapeHtml(p.node||'—')}</td>
        <td style="font-size:12px;font-family:monospace">${escapeHtml(p.ip||'—')}</td>
        <td style="display:flex;gap:5px">
          <button class="act act-primary btn-restart" data-pod="${escapeHtml(p.name)}" title="Restart">↺</button>
          <button class="act act-danger btn-delete-pod" data-pod="${escapeHtml(p.name)}" title="Delete">🗑</button>
        </td>
      </tr>`).join('')}</tbody>
    </table>`;
  }

  renderDeploymentsTable() {
    const deps = this.filteredBy(this._state.deployments);
    if (deps.length === 0) return `<div class="empty">No deployment found</div>`;
    return `<table class="tbl">
      <thead><tr><th>Name</th><th>Namespace</th><th>Ready</th><th>Strategy</th><th>Image</th><th>Actions</th></tr></thead>
      <tbody>${deps.map(d => `<tr class="${this._state.selectedItem?.name===d.name?'selected':''}">
        <td><span class="name-link" data-detail="${escapeHtml(d.name)}">${escapeHtml(d.name)}</span></td>
        <td><span class="pill neutral">${escapeHtml(d.namespace)}</span></td>
        <td><span class="pill ${d.ready.split('/')[0]===d.ready.split('/')[1]?'ok':'danger'}">${escapeHtml(d.ready)}</span></td>
        <td><span class="pill neutral">${escapeHtml(d.strategy)}</span></td>
        <td style="font-size:12px;font-family:monospace;max-width:200px;overflow:hidden;text-overflow:ellipsis">${escapeHtml(d.image)}</td>
        <td style="display:flex;gap:5px">
          <button class="act act-green btn-scale" data-dep="${escapeHtml(d.name)}" title="Scale">⚖️</button>
          <button class="act act-primary btn-restart-dep" data-dep="${escapeHtml(d.name)}" title="Restart">↺</button>
          <button class="act act-danger btn-delete-dep" data-dep="${escapeHtml(d.name)}" title="Delete">🗑</button>
        </td>
      </tr>`).join('')}</tbody>
    </table>`;
  }

  renderServicesTable() {
    const svcs = this.filteredBy(this._state.services);
    if (svcs.length === 0) return `<div class="empty">No service found</div>`;
    return `<table class="tbl">
      <thead><tr><th>Name</th><th>Namespace</th><th>Type</th><th>Cluster IP</th><th>External IP</th><th>Ports</th></tr></thead>
      <tbody>${svcs.map(s => `<tr class="${this._state.selectedItem?.name===s.name?'selected':''}">
        <td><span class="name-link" data-detail="${escapeHtml(s.name)}">${escapeHtml(s.name)}</span></td>
        <td><span class="pill neutral">${escapeHtml(s.namespace)}</span></td>
        <td><span class="pill ${s.type==='LoadBalancer'?'ok':s.type==='NodePort'?'warn':'neutral'}">${escapeHtml(s.type)}</span></td>
        <td style="font-family:monospace;font-size:12px">${escapeHtml(s.clusterIP)}</td>
        <td style="font-family:monospace;font-size:12px">${escapeHtml(s.externalIP||'—')}</td>
        <td style="font-size:12px">${escapeHtml(s.ports)}</td>
      </tr>`).join('')}</tbody>
    </table>`;
  }

  renderReplicaSetsTable() {
    const rsets = this.filteredBy(this._state.replicasets);
    return `<table class="tbl">
      <thead><tr><th>Name</th><th>Namespace</th><th>Desired</th><th>Current</th><th>Ready</th><th>Deployment</th></tr></thead>
      <tbody>${rsets.map(rs => `<tr class="${this._state.selectedItem?.name===rs.name?'selected':''}">
        <td><span class="name-link" data-detail="${escapeHtml(rs.name)}">${escapeHtml(rs.name)}</span></td>
        <td><span class="pill neutral">${escapeHtml(rs.namespace)}</span></td>
        <td>${rs.desired}</td><td>${rs.current}</td>
        <td><span class="pill ${rs.ready===rs.desired?'ok':'warn'}">${rs.ready}</span></td>
        <td><span class="name-link" data-nav="deployments" data-item="${escapeHtml(rs.deployment)}">${escapeHtml(rs.deployment||'—')}</span></td>
      </tr>`).join('')}</tbody>
    </table>`;
  }

  renderStatefulSetsTable() {
    const ssets = this.filteredBy(this._state.statefulsets);
    return `<table class="tbl">
      <thead><tr><th>Name</th><th>Namespace</th><th>Ready</th><th>Replicas</th><th>Service</th></tr></thead>
      <tbody>${ssets.map(s => `<tr>
        <td><b>${escapeHtml(s.name)}</b></td>
        <td><span class="pill neutral">${escapeHtml(s.namespace)}</span></td>
        <td><span class="pill ok">${escapeHtml(s.ready)}</span></td>
        <td>${s.replicas}</td>
        <td>${escapeHtml(s.serviceName)}</td>
      </tr>`).join('')}</tbody>
    </table>`;
  }

  renderConfigMapsTable() {
    const cms = this.filteredBy(this._state.configmaps);
    return `<table class="tbl">
      <thead><tr><th>Name</th><th>Namespace</th><th>Keys</th></tr></thead>
      <tbody>${cms.map(c => `<tr>
        <td><b>${escapeHtml(c.name)}</b></td>
        <td><span class="pill neutral">${escapeHtml(c.namespace)}</span></td>
        <td>${c.keys}</td>
      </tr>`).join('')}</tbody>
    </table>`;
  }

  renderSecretsTable() {
    const secs = this.filteredBy(this._state.secrets);
    return `<table class="tbl">
      <thead><tr><th>Name</th><th>Namespace</th><th>Type</th><th>Keys</th></tr></thead>
      <tbody>${secs.map(s => `<tr>
        <td><b>${escapeHtml(s.name)}</b></td>
        <td><span class="pill neutral">${escapeHtml(s.namespace)}</span></td>
        <td style="font-size:12px">${escapeHtml(s.type)}</td>
        <td>${s.keys}</td>
      </tr>`).join('')}</tbody>
    </table>`;
  }

  renderNodesSection() {
    return this._state.nodes.map(n => `
      <div class="node-card">
        <h4>🖥️ ${escapeHtml(n.name)} <span class="pill ${statusClass(n.status)}">${escapeHtml(n.status)}</span> <span class="pill neutral" style="margin-left:6px">${escapeHtml(n.roles)}</span></h4>
        <div class="node-stats">
          <div class="node-stat">OS<br><b>${escapeHtml(n.os)}</b></div>
          <div class="node-stat">K8s version<br><b>${escapeHtml(n.version)}</b></div>
          <div class="node-stat">CPU<br><b>${escapeHtml(n.cpu)} cores</b></div>
          <div class="node-stat">Memory<br><b>${escapeHtml(n.memory)}</b></div>
          <div class="node-stat">Pods<br><b>${n.pods}</b></div>
        </div>
      </div>`).join('');
  }

  renderEventsSection() {
    return this._state.events.map(e => `<div class="event-row">
      <span class="event-type ${e.type}">${e.type}</span>
      <div>
        <div class="event-msg">${escapeHtml(e.message)}</div>
        <div class="event-obj">${escapeHtml(e.object)} · ${escapeHtml(e.namespace)} · count: ${e.count}</div>
      </div>
    </div>`).join('');
  }

  renderHelmSection() {
    return this._state.helm.map(h => `<div class="helm-card">
      <div class="helm-icon">⛵</div>
      <div class="helm-info">
        <h4>${escapeHtml(h.name)}</h4>
        <small>${escapeHtml(h.chart)} · namespace: ${escapeHtml(h.namespace)} · revision: ${h.revision}</small>
      </div>
      <span class="pill ok">${escapeHtml(h.status)}</span>
    </div>`).join('');
  }

  /* ─── Detail view ─── */
  renderDetail(item, kind) {
    const related = this.getRelatedResources(item);
    const yamlKey = `${kind}/${item.namespace}/${item.name}`;
    const yamlContent = this._state.yamlSaved[yamlKey]?.value || generateYaml(kind, item.name, item.namespace, item);

    const relatedHtml = () => {
      const parts = [];
      if (related.deployment) parts.push(`
        <div class="related-section"><h4>🚀 Deployment</h4></div>
        <div class="related-cards">
          <div class="related-card" data-nav="deployments" data-item="${escapeHtml(related.deployment.name)}">
            <div class="rc-kind">Deployment</div>
            <div class="rc-name">${escapeHtml(related.deployment.name)}</div>
            <div class="rc-meta">Ready: ${escapeHtml(related.deployment.ready)} · ${escapeHtml(related.deployment.namespace)}</div>
          </div>
        </div>`);
      if (related.statefulset) parts.push(`
        <div class="related-section"><h4>💾 StatefulSet</h4></div>
        <div class="related-cards">
          <div class="related-card">
            <div class="rc-kind">StatefulSet</div>
            <div class="rc-name">${escapeHtml(related.statefulset.name)}</div>
            <div class="rc-meta">Ready: ${escapeHtml(related.statefulset.ready)}</div>
          </div>
        </div>`);
      if (related.services?.length) parts.push(`
        <div class="related-section"><h4>🌐 Services</h4></div>
        <div class="related-cards">${related.services.map(s => `
          <div class="related-card" data-nav="services" data-item="${escapeHtml(s.name)}">
            <div class="rc-kind">Service</div>
            <div class="rc-name">${escapeHtml(s.name)}</div>
            <div class="rc-meta">${escapeHtml(s.type)} · ${escapeHtml(s.clusterIP)}</div>
          </div>`).join('')}
        </div>`);
      if (related.replicasets?.length) parts.push(`
        <div class="related-section"><h4>🔄 ReplicaSets</h4></div>
        <div class="related-cards">${related.replicasets.map(rs => `
          <div class="related-card" data-nav="replicasets" data-item="${escapeHtml(rs.name)}">
            <div class="rc-kind">ReplicaSet</div>
            <div class="rc-name">${escapeHtml(rs.name)}</div>
            <div class="rc-meta">Desired: ${rs.desired} · Ready: ${rs.ready}</div>
          </div>`).join('')}
        </div>`);
      if (related.pods?.length) parts.push(`
        <div class="related-section"><h4>⬡ Pods</h4></div>
        <div class="related-cards">${related.pods.map(p => `
          <div class="related-card" data-nav="pods" data-item="${escapeHtml(p.name)}">
            <div class="rc-kind">Pod</div>
            <div class="rc-name">${escapeHtml(p.name)}</div>
            <div class="rc-meta"><span class="pill ${statusClass(p.status)}" style="font-size:10px">${escapeHtml(p.status)}</span> · restarts: ${p.restarts}</div>
          </div>`).join('')}
        </div>`);
      return parts.join('');
    };

    const isPod = !!item.ip !== undefined && item.node !== undefined;
    const isDeployment = item.replicas !== undefined;

    const actionsHtml = isPod ? `
      <div class="actions-row">
        <button class="act act-primary btn-restart" data-pod="${escapeHtml(item.name)}">↺ Restart Pod</button>
        <button class="act act-warn btn-logs" data-pod="${escapeHtml(item.name)}">📜 View Logs</button>
        <button class="act act-danger btn-delete-pod" data-pod="${escapeHtml(item.name)}">🗑️ Delete Pod</button>
      </div>` : isDeployment ? `
      <div class="actions-row">
        <button class="act act-green btn-scale" data-dep="${escapeHtml(item.name)}">⚖️ Scale</button>
        <button class="act act-primary btn-restart-dep" data-dep="${escapeHtml(item.name)}">♻️ Rolling Restart</button>
        <button class="act act-danger btn-delete-dep" data-dep="${escapeHtml(item.name)}">🗑️ Delete</button>
      </div>` : '';

    const statusField = item.status || item.ready || item.type || '—';

    return `
      <div class="detail-view">
        <div class="detail-header">
          <div class="kind-badge">${kind.toUpperCase()}</div>
          <div class="info">
            <h3>${escapeHtml(item.name)}</h3>
            <div class="meta">
              <span>📁 ${escapeHtml(item.namespace || '—')}</span>
              <span><span class="pill ${statusClass(statusField)}">${escapeHtml(statusField)}</span></span>
              ${item.node ? `<span>🖥️ ${escapeHtml(item.node)}</span>` : ''}
              ${item.ip ? `<span>🔌 ${escapeHtml(item.ip)}</span>` : ''}
              ${item.replicas !== undefined ? `<span>⚖️ ${item.replicas} replica${item.replicas!==1?'s':''}</span>` : ''}
            </div>
          </div>
        </div>
        ${actionsHtml}
        <div>
          <div class="detail-tabs">
            <div class="tab ${this._state.activeTab==='overview'?'active':''}" data-tab="overview">Overview</div>
            <div class="tab ${this._state.activeTab==='related'?'active':''}" data-tab="related">Related Resources</div>
            <div class="tab ${this._state.activeTab==='yaml'?'active':''}" data-tab="yaml">YAML</div>
            ${isPod ? `<div class="tab ${this._state.activeTab==='logs'?'active':''}" data-tab="logs">Logs</div>` : ''}
          </div>
          <div class="tab-content ${this._state.activeTab==='overview'?'active':''}">
            <div class="info-grid">
              ${Object.entries(item).filter(([k,v]) => typeof v !== 'object' && k !== 'labels' && k !== 'selector').map(([k,v]) => `
                <div class="info-card"><div class="ic-label">${escapeHtml(k)}</div><div class="ic-value">${escapeHtml(String(v))}</div></div>
              `).join('')}
            </div>
            ${item.labels ? `<div style="margin-top:12px"><div class="ic-label" style="margin-bottom:6px">LABELS</div><div class="label-chips">${Object.entries(item.labels).map(([k,v]) => `<span class="label-chip">${escapeHtml(k)}=${escapeHtml(v)}</span>`).join('')}</div></div>` : ''}
            ${item.selector && Object.keys(item.selector).length ? `<div style="margin-top:12px"><div class="ic-label" style="margin-bottom:6px">SELECTOR</div><div class="label-chips">${Object.entries(item.selector).map(([k,v]) => `<span class="label-chip">${escapeHtml(k)}=${escapeHtml(v)}</span>`).join('')}</div></div>` : ''}
          </div>
          <div class="tab-content ${this._state.activeTab==='related'?'active':''}">
            ${relatedHtml() || '<div class="empty">No related resource found</div>'}
          </div>
          <div class="tab-content ${this._state.activeTab==='yaml'?'active':''}">
            <div style="margin-bottom:8px;font-size:12px;color:#92a3ca">Modifica il YAML e salva localmente. In produzione questo aggiorna la risorsa via kubectl apply.</div>
            <textarea class="yaml-editor" id="yaml-editor-${escapeHtml(item.name)}">${escapeHtml(yamlContent)}</textarea>
            <div class="yaml-actions">
              <button class="act act-green btn-save-yaml" data-key="${escapeHtml(yamlKey)}">💾 Salva YAML</button>
              <button class="act act-primary btn-copy-yaml" data-key="${escapeHtml(yamlKey)}">📋 Copia</button>
              <span class="yaml-status ${this._state.yamlSaved[yamlKey]?'show':''}" id="yaml-status">✅ Salvato il ${this._state.yamlSaved[yamlKey]?.savedAt ? new Date(this._state.yamlSaved[yamlKey].savedAt).toLocaleTimeString('it-IT') : ''}</span>
            </div>
          </div>
          ${isPod ? `<div class="tab-content ${this._state.activeTab==='logs'?'active':''}">
            <div class="log-controls">
              <span class="pill neutral">📜 Container logs</span>
              <span style="font-size:12px;color:#92a3ca">Simulati — in produzione via kubectl logs</span>
            </div>
            <div class="log-area">${generateLogs(item.name, item.status).split('\n').map(line => {
              const cls = line.includes('ERROR')||line.includes('error') ? 'error' : line.includes('WARN') ? 'warn' : line.includes('FATAL') ? 'fatal' : 'info';
              return `<div class="log-line ${cls}">${escapeHtml(line)}</div>`;
            }).join('')}</div>
          </div>` : ''}
        </div>
      </div>
    `;
  }

  renderPanel() {
    const section = this._state.activeSection;
    const selected = this._state.selectedItem;

    if (selected) {
      const kindMap = { pods:'Pod', deployments:'Deployment', services:'Service', replicasets:'ReplicaSet', statefulsets:'StatefulSet' };
      const kind = kindMap[section] || section;
      return `
        <div class="panel-header">
          <button class="act act-primary" id="btn-back">← Torna a ${section}</button>
          <h3>${kind}: ${escapeHtml(selected.name)}</h3>
        </div>
        ${this.renderDetail(selected, kind)}
      `;
    }

    const tableMap = {
      overview: () => this.renderOverview(),
      pods: () => `<div class="panel-header"><h3>⬡ Pods</h3><small>${this.filteredBy(this._state.pods).length} resources</small><input class="search-box" placeholder="Search pods…" id="search-input" value="${escapeHtml(this._state.search)}"></div>${this.renderPodsTable()}`,
      deployments: () => `<div class="panel-header"><h3>🚀 Deployments</h3><small>${this.filteredBy(this._state.deployments).length} resources</small><input class="search-box" placeholder="Search deployments…" id="search-input" value="${escapeHtml(this._state.search)}"></div>${this.renderDeploymentsTable()}`,
      services: () => `<div class="panel-header"><h3>🌐 Services</h3><small>${this.filteredBy(this._state.services).length} resources</small><input class="search-box" placeholder="Search services…" id="search-input" value="${escapeHtml(this._state.search)}"></div>${this.renderServicesTable()}`,
      replicasets: () => `<div class="panel-header"><h3>🔄 ReplicaSets</h3><small>${this.filteredBy(this._state.replicasets).length} resources</small></div>${this.renderReplicaSetsTable()}`,
      statefulsets: () => `<div class="panel-header"><h3>💾 StatefulSets</h3></div>${this.renderStatefulSetsTable()}`,
      configmaps: () => `<div class="panel-header"><h3>📋 ConfigMaps</h3></div>${this.renderConfigMapsTable()}`,
      secrets: () => `<div class="panel-header"><h3>🔐 Secrets</h3></div>${this.renderSecretsTable()}`,
      nodes: () => `<div class="panel-header"><h3>🖥️ Nodes</h3></div>${this.renderNodesSection()}`,
      events: () => `<div class="panel-header"><h3>⚡ Events</h3></div>${this.renderEventsSection()}`,
      helm: () => `<div class="panel-header"><h3>⛵ Helm Releases</h3></div>${this.renderHelmSection()}`,
    };
    return tableMap[section] ? tableMap[section]() : `<div class="empty">Sezione non trovata</div>`;
  }

  renderScaleModal() {
    const dep = this._state.scaleModal;
    if (!dep) return '';
    return `<div class="modal-backdrop" id="modal-scale">
      <div class="modal">
        <h3>⚖️ Scale Deployment: ${escapeHtml(dep.name)}</h3>
        <div style="font-size:13px;color:#92a3ca;margin-bottom:8px">Repliche attuali: <b style="color:#fff">${dep.replicas}</b></div>
        <label style="font-size:13px">New replicas:</label>
        <input type="number" class="modal-input" id="scale-input" value="${dep.replicas}" min="0" max="20">
        <div class="modal-actions">
          <button class="act act-primary" id="btn-scale-cancel">Annulla</button>
          <button class="act act-green" id="btn-scale-confirm">✅ Applica</button>
        </div>
      </div>
    </div>`;
  }

  renderConfirmModal() {
    const m = this._state.confirmModal;
    if (!m) return '';
    return `<div class="modal-backdrop" id="modal-confirm">
      <div class="modal">
        <h3>${escapeHtml(m.title)}</h3>
        <p style="font-size:13px;color:#92a3ca;margin-bottom:16px">${escapeHtml(m.message)}</p>
        <div class="modal-actions">
          <button class="act act-primary" id="btn-confirm-cancel">Annulla</button>
          <button class="act act-danger" id="btn-confirm-ok">🗑️ Elimina</button>
        </div>
      </div>
    </div>`;
  }

  renderToast() {
    const t = this._state.toast;
    if (!t) return '';
    return `<div class="toast toast-${t.type} show">${escapeHtml(t.msg)}</div>`;
  }

  render() {
    const nsOptions = ['all', ...this._state.namespaces].map(n =>
      `<option value="${n}" ${this._state.activeNs === n ? 'selected' : ''}>${n}</option>`).join('');

    this.shadowRoot.innerHTML = `
      <style>${CSS}</style>
      <div class="shell">
        <div class="topbar">
          <div class="topbar-title">
            <div class="cube">N22</div>
            <div><h2>OpenLens Kubernetes</h2><small>NEBULAOPS MFE · RUNTIME · K8s</small></div>
          </div>
          <span class="cluster-badge">☸️ cluster: nebulaops-local</span>
          <select class="ns-select" id="ns-select">${nsOptions}</select>
          <button class="refresh-btn" id="btn-refresh">⟳ Refresh</button>
          <span style="margin-left:auto;font-size:12px;color:#92a3ca">3 nodes · K8s v1.30.2</span>
        </div>
        <div class="body">
          <nav class="sidetree">${this.renderSideTree()}</nav>
          <main class="panel">${this.renderPanel()}</main>
        </div>
      </div>
      ${this.renderScaleModal()}
      ${this.renderConfirmModal()}
      ${this.renderToast()}
    `;
    this._attachEvents();
  }

  _attachEvents() {
    const sr = this.shadowRoot;

    /* Namespace select */
    sr.getElementById('ns-select')?.addEventListener('change', e => {
      this._state.activeNs = e.target.value;
      this._state.selectedItem = null;
      this.render();
    });

    /* Refresh */
    sr.getElementById('btn-refresh')?.addEventListener('click', () => {
      this.showToast('🔄 Cluster data refreshed');
    });

    /* Search */
    sr.getElementById('search-input')?.addEventListener('input', e => {
      this._state.search = e.target.value;
      this.render();
    });

    /* Back button */
    sr.getElementById('btn-back')?.addEventListener('click', () => {
      this._state.selectedItem = null;
      this._state.activeTab = 'overview';
      this.render();
    });

    /* Sidetree navigation */
    sr.querySelectorAll('.tree-item[data-nav]').forEach(el => {
      el.addEventListener('click', () => {
        const nav = el.dataset.nav;
        this._state.selectedItem = null;
        this.navigate(nav);
      });
    });

    /* Name links for detail */
    sr.querySelectorAll('[data-detail]').forEach(el => {
      el.addEventListener('click', () => {
        const name = el.dataset.detail;
        const section = this._state.activeSection;
        const data = this._state[section];
        const item = data?.find(i => i.name === name);
        if (item) { this._state.selectedItem = item; this.render(); }
      });
    });

    /* Cross-resource nav links */
    sr.querySelectorAll('[data-nav][data-item]').forEach(el => {
      el.addEventListener('click', () => {
        const nav = el.dataset.nav;
        const itemName = el.dataset.item;
        const data = this._state[nav];
        const item = data?.find(i => i.name === itemName);
        if (item) {
          this._state.activeNs = item.namespace || this._state.activeNs;
          this._state.activeSection = nav;
          this._state.selectedItem = item;
          this._state.activeTab = 'overview';
          this.render();
        }
      });
    });

    /* Tabs */
    sr.querySelectorAll('.tab[data-tab]').forEach(el => {
      el.addEventListener('click', () => {
        this._state.activeTab = el.dataset.tab;
        this.render();
      });
    });

    /* Restart pod buttons */
    sr.querySelectorAll('.btn-restart[data-pod]').forEach(el => {
      el.addEventListener('click', e => {
        e.stopPropagation();
        const pod = this._state.pods.find(p => p.name === el.dataset.pod);
        if (pod) this.restartPod(pod);
      });
    });

    /* Delete pod */
    sr.querySelectorAll('.btn-delete-pod[data-pod]').forEach(el => {
      el.addEventListener('click', e => {
        e.stopPropagation();
        const podName = el.dataset.pod;
        const pod = this._state.pods.find(p => p.name === podName);
        if (!pod) return;
        this._state.confirmModal = {
          title: `Elimina Pod: ${podName}`,
          message: `Sei sicuro di voler eliminare il pod "${podName}"? Kubernetes ne creerà uno nuovo se gestito da un controller.`,
          action: () => this.deletePod(pod)
        };
        this.render();
      });
    });

    /* Scale deployment */
    sr.querySelectorAll('.btn-scale[data-dep]').forEach(el => {
      el.addEventListener('click', e => {
        e.stopPropagation();
        const dep = this._state.deployments.find(d => d.name === el.dataset.dep);
        if (dep) { this._state.scaleModal = dep; this.render(); }
      });
    });

    /* Restart deployment */
    sr.querySelectorAll('.btn-restart-dep[data-dep]').forEach(el => {
      el.addEventListener('click', e => {
        e.stopPropagation();
        const dep = this._state.deployments.find(d => d.name === el.dataset.dep);
        if (dep) this.restartDeployment(dep);
      });
    });

    /* Delete deployment */
    sr.querySelectorAll('.btn-delete-dep[data-dep]').forEach(el => {
      el.addEventListener('click', e => {
        e.stopPropagation();
        const depName = el.dataset.dep;
        const dep = this._state.deployments.find(d => d.name === depName);
        if (!dep) return;
        this._state.confirmModal = {
          title: `Elimina Deployment: ${depName}`,
          message: `Sei sicuro di voler eliminare il deployment "${depName}" e tutti i pod gestiti?`,
          action: () => this.deleteDeployment(dep)
        };
        this.render();
      });
    });

    /* YAML Save */
    sr.querySelectorAll('.btn-save-yaml[data-key]').forEach(el => {
      el.addEventListener('click', () => {
        const key = el.dataset.key;
        const editorId = `yaml-editor-${key.split('/').pop()}`;
        const textarea = sr.querySelector('.yaml-editor');
        if (textarea) this.saveYaml(key, textarea.value);
      });
    });

    /* YAML Copy */
    sr.querySelectorAll('.btn-copy-yaml').forEach(el => {
      el.addEventListener('click', () => {
        const textarea = sr.querySelector('.yaml-editor');
        if (textarea) {
          navigator.clipboard.writeText(textarea.value).then(() => this.showToast('📋 YAML copiato negli appunti'));
        }
      });
    });

    /* Scale modal */
    sr.getElementById('btn-scale-cancel')?.addEventListener('click', () => {
      this._state.scaleModal = null; this.render();
    });
    sr.getElementById('btn-scale-confirm')?.addEventListener('click', () => {
      const val = parseInt(sr.getElementById('scale-input')?.value || '0');
      if (!isNaN(val) && val >= 0) this.scaleDeployment(this._state.scaleModal, val);
    });

    /* Confirm modal */
    sr.getElementById('btn-confirm-cancel')?.addEventListener('click', () => {
      this._state.confirmModal = null; this.render();
    });
    sr.getElementById('btn-confirm-ok')?.addEventListener('click', () => {
      this._state.confirmModal?.action?.();
    });

    /* Logs btn from overview table */
    sr.querySelectorAll('.btn-logs[data-pod]').forEach(el => {
      el.addEventListener('click', e => {
        e.stopPropagation();
        const pod = this._state.pods.find(p => p.name === el.dataset.pod);
        if (pod) { this._state.selectedItem = pod; this._state.activeTab = 'logs'; this.render(); }
      });
    });

    /* Related resource cards */
    sr.querySelectorAll('.related-card[data-nav]').forEach(el => {
      el.addEventListener('click', () => {
        const nav = el.dataset.nav;
        const name = el.dataset.item;
        const data = this._state[nav];
        const item = data?.find(i => i.name === name);
        if (item) {
          this._state.activeSection = nav;
          this._state.selectedItem = item;
          this._state.activeTab = 'overview';
          this.render();
        }
      });
    });
  }
}

customElements.define('nebulaops-mfe-openlens-kubernetes', NebulaopsMfeOpenlensKubernetes);
