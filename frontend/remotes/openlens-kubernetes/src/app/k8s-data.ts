// ── Mock Kubernetes data (replaces live gateway calls in standalone mode) ──
export interface K8sPod {
  name: string; namespace: string; status: string; ready: string;
  restarts: number; node: string; ip: string;
  labels: Record<string, string>; deployment?: string; statefulset?: string;
  age: string; image: string;
}
export interface K8sDeployment {
  name: string; namespace: string; ready: string; upToDate: number;
  available: number; replicas: number; strategy: string; image: string;
  age: string; selector: Record<string, string>;
}
export interface K8sService {
  name: string; namespace: string; type: string; clusterIP: string;
  externalIP: string; ports: string; selector: Record<string, string>; age: string;
}
export interface K8sReplicaSet {
  name: string; namespace: string; desired: number; current: number;
  ready: number; deployment: string; age: string;
}
export interface K8sNode {
  name: string; status: string; roles: string; version: string;
  os: string; cpu: string; memory: string; pods: number; age: string;
}
export interface K8sStatefulSet {
  name: string; namespace: string; ready: string; replicas: number;
  serviceName: string; age: string;
}
export interface K8sEvent {
  type: string; reason: string; message: string; object: string;
  namespace: string; count: number; age: string;
}
export interface K8sConfigMap { name: string; namespace: string; keys: number; age: string; }
export interface K8sSecret     { name: string; namespace: string; type: string; keys: number; age: string; }
export interface HelmRelease   { name: string; namespace: string; chart: string; status: string; revision: number; age: string; }

export interface ClusterSnapshot {
  namespaces:   string[];
  pods:         K8sPod[];
  deployments:  K8sDeployment[];
  services:     K8sService[];
  replicasets:  K8sReplicaSet[];
  configmaps:   K8sConfigMap[];
  secrets:      K8sSecret[];
  events:       K8sEvent[];
  nodes:        K8sNode[];
  statefulsets: K8sStatefulSet[];
  helm:         HelmRelease[];
}

const ago = (m: number) => new Date(Date.now() - m * 60_000).toISOString();

export function mockCluster(): ClusterSnapshot {
  return {
    namespaces: ['default', 'kube-system', 'monitoring', 'production', 'staging'],
    pods: [
      { name:'api-gateway-7d9f8b-xk2pl', namespace:'production', status:'Running',          ready:'2/2', restarts:0, node:'node-01', ip:'10.0.1.12', labels:{app:'api-gateway',version:'v2.1'}, deployment:'api-gateway', age:ago(340), image:'nginx:1.27-alpine' },
      { name:'api-gateway-7d9f8b-mz9qr', namespace:'production', status:'Running',          ready:'2/2', restarts:1, node:'node-02', ip:'10.0.1.13', labels:{app:'api-gateway',version:'v2.1'}, deployment:'api-gateway', age:ago(300), image:'nginx:1.27-alpine' },
      { name:'frontend-6c5b8-wq3nt',     namespace:'production', status:'Running',          ready:'1/1', restarts:0, node:'node-01', ip:'10.0.1.14', labels:{app:'frontend',version:'v22.2'},   deployment:'frontend',     age:ago(180), image:'nebulaops-frontend:22.2' },
      { name:'auth-service-5f7d9-pk1mn', namespace:'production', status:'CrashLoopBackOff', ready:'0/1', restarts:7, node:'node-02', ip:'10.0.1.15', labels:{app:'auth-service'},                deployment:'auth-service', age:ago(90),  image:'nebulaops-auth:1.4' },
      { name:'postgres-0',               namespace:'production', status:'Running',          ready:'1/1', restarts:0, node:'node-03', ip:'10.0.1.16', labels:{app:'postgres'},                    statefulset:'postgres',    age:ago(2880),image:'postgres:16-alpine' },
      { name:'prometheus-0',             namespace:'monitoring', status:'Running',          ready:'1/1', restarts:0, node:'node-01', ip:'10.0.2.10', labels:{app:'prometheus'},                  statefulset:'prometheus',  age:ago(5760),image:'prom/prometheus:v2.52' },
      { name:'grafana-7b4c9-lz8vx',      namespace:'monitoring', status:'Running',          ready:'1/1', restarts:0, node:'node-02', ip:'10.0.2.11', labels:{app:'grafana'},                     deployment:'grafana',      age:ago(1440),image:'grafana/grafana:10.4' },
      { name:'ai-ops-6d8f7-wr2km',       namespace:'production', status:'Pending',          ready:'0/1', restarts:0, node:'',       ip:'',          labels:{app:'ai-ops',version:'v1.0'},       deployment:'ai-ops',       age:ago(5),   image:'nebulaops-ai:1.0' },
      { name:'coredns-787d4-nq5xp',      namespace:'kube-system',status:'Running',          ready:'1/1', restarts:0, node:'node-01', ip:'10.96.0.10',labels:{app:'coredns'},                     deployment:'coredns',      age:ago(20160),image:'registry.k8s.io/coredns:v1.11.1' },
    ],
    deployments: [
      { name:'api-gateway',  namespace:'production', ready:'2/2', upToDate:2, available:2, replicas:2, strategy:'RollingUpdate', image:'nginx:1.27-alpine',          age:ago(340),  selector:{app:'api-gateway'} },
      { name:'frontend',     namespace:'production', ready:'1/1', upToDate:1, available:1, replicas:1, strategy:'RollingUpdate', image:'nebulaops-frontend:22.2',     age:ago(180),  selector:{app:'frontend'} },
      { name:'auth-service', namespace:'production', ready:'0/1', upToDate:1, available:0, replicas:1, strategy:'RollingUpdate', image:'nebulaops-auth:1.4',          age:ago(90),   selector:{app:'auth-service'} },
      { name:'grafana',      namespace:'monitoring', ready:'1/1', upToDate:1, available:1, replicas:1, strategy:'Recreate',      image:'grafana/grafana:10.4',        age:ago(1440), selector:{app:'grafana'} },
      { name:'ai-ops',       namespace:'production', ready:'0/1', upToDate:1, available:0, replicas:1, strategy:'RollingUpdate', image:'nebulaops-ai:1.0',            age:ago(5),    selector:{app:'ai-ops'} },
      { name:'coredns',      namespace:'kube-system',ready:'1/1', upToDate:1, available:1, replicas:1, strategy:'RollingUpdate', image:'registry.k8s.io/coredns:v1.11.1',age:ago(20160),selector:{app:'coredns'} },
    ],
    services: [
      { name:'api-gateway-svc', namespace:'production', type:'LoadBalancer', clusterIP:'10.96.1.10', externalIP:'203.0.113.10', ports:'80:31080/TCP, 443:31443/TCP', selector:{app:'api-gateway'}, age:ago(340) },
      { name:'frontend-svc',    namespace:'production', type:'ClusterIP',    clusterIP:'10.96.1.11', externalIP:'',            ports:'4200:32000/TCP',              selector:{app:'frontend'},    age:ago(180) },
      { name:'auth-svc',        namespace:'production', type:'ClusterIP',    clusterIP:'10.96.1.12', externalIP:'',            ports:'8081:32001/TCP',              selector:{app:'auth-service'},age:ago(90) },
      { name:'postgres-svc',    namespace:'production', type:'ClusterIP',    clusterIP:'10.96.1.13', externalIP:'',            ports:'5432/TCP',                    selector:{app:'postgres'},    age:ago(2880) },
      { name:'prometheus-svc',  namespace:'monitoring', type:'ClusterIP',    clusterIP:'10.96.2.10', externalIP:'',            ports:'9090/TCP',                    selector:{app:'prometheus'},  age:ago(5760) },
      { name:'grafana-svc',     namespace:'monitoring', type:'NodePort',     clusterIP:'10.96.2.11', externalIP:'',            ports:'3000:30300/TCP',              selector:{app:'grafana'},     age:ago(1440) },
      { name:'kubernetes',      namespace:'default',    type:'ClusterIP',    clusterIP:'10.96.0.1',  externalIP:'',            ports:'443/TCP',                     selector:{},                  age:ago(20160) },
    ],
    replicasets: [
      { name:'api-gateway-7d9f8b', namespace:'production', desired:2, current:2, ready:2, deployment:'api-gateway',  age:ago(340) },
      { name:'api-gateway-8e2c1a', namespace:'production', desired:0, current:0, ready:0, deployment:'api-gateway',  age:ago(1200) },
      { name:'frontend-6c5b8',     namespace:'production', desired:1, current:1, ready:1, deployment:'frontend',     age:ago(180) },
      { name:'auth-service-5f7d9', namespace:'production', desired:1, current:1, ready:0, deployment:'auth-service', age:ago(90) },
      { name:'grafana-7b4c9',      namespace:'monitoring', desired:1, current:1, ready:1, deployment:'grafana',      age:ago(1440) },
      { name:'ai-ops-6d8f7',       namespace:'production', desired:1, current:1, ready:0, deployment:'ai-ops',       age:ago(5) },
    ],
    configmaps: [
      { name:'api-gateway-config',  namespace:'production', keys:3, age:ago(340) },
      { name:'prometheus-config',   namespace:'monitoring', keys:5, age:ago(5760) },
      { name:'grafana-datasources', namespace:'monitoring', keys:2, age:ago(1440) },
      { name:'coredns',             namespace:'kube-system',keys:1, age:ago(20160) },
    ],
    secrets: [
      { name:'postgres-credentials', namespace:'production', type:'Opaque',                          keys:2, age:ago(2880) },
      { name:'registry-pull-secret', namespace:'production', type:'kubernetes.io/dockerconfigjson',  keys:1, age:ago(5760) },
      { name:'tls-cert',             namespace:'production', type:'kubernetes.io/tls',               keys:2, age:ago(720) },
      { name:'keycloak-admin',       namespace:'production', type:'Opaque',                          keys:3, age:ago(340) },
    ],
    events: [
      { type:'Warning', reason:'BackOff',    message:'Back-off restarting failed container auth-service',                          object:'Pod/auth-service-5f7d9-pk1mn', namespace:'production', count:14, age:ago(3) },
      { type:'Normal',  reason:'Scheduled',  message:'Successfully assigned production/ai-ops-6d8f7-wr2km to node-02',            object:'Pod/ai-ops-6d8f7-wr2km',       namespace:'production', count:1,  age:ago(5) },
      { type:'Warning', reason:'Failed',     message:'Failed to pull image "nebulaops-ai:1.0": rpc error: code = Unknown',         object:'Pod/ai-ops-6d8f7-wr2km',       namespace:'production', count:3,  age:ago(5) },
      { type:'Normal',  reason:'Started',    message:'Started container api-gateway',                                             object:'Pod/api-gateway-7d9f8b-xk2pl', namespace:'production', count:1,  age:ago(338) },
    ],
    nodes: [
      { name:'node-01', status:'Ready', roles:'control-plane', version:'v1.30.2', os:'linux/amd64', cpu:'4',  memory:'16Gi', pods:3, age:ago(20160) },
      { name:'node-02', status:'Ready', roles:'worker',        version:'v1.30.2', os:'linux/amd64', cpu:'8',  memory:'32Gi', pods:4, age:ago(18000) },
      { name:'node-03', status:'Ready', roles:'worker',        version:'v1.30.2', os:'linux/amd64', cpu:'8',  memory:'32Gi', pods:2, age:ago(16800) },
    ],
    statefulsets: [
      { name:'postgres',   namespace:'production', ready:'1/1', replicas:1, serviceName:'postgres-svc',  age:ago(2880) },
      { name:'prometheus', namespace:'monitoring', ready:'1/1', replicas:1, serviceName:'prometheus-svc',age:ago(5760) },
    ],
    helm: [
      { name:'monitoring-stack', namespace:'monitoring',    chart:'kube-prometheus-stack-58.4.0', status:'deployed', revision:3,  age:ago(1440) },
      { name:'cert-manager',     namespace:'cert-manager',  chart:'cert-manager-v1.14.5',         status:'deployed', revision:1,  age:ago(5760) },
      { name:'ingress-nginx',    namespace:'ingress-nginx', chart:'ingress-nginx-4.10.1',          status:'deployed', revision:2,  age:ago(2880) },
      { name:'nebulaops-app',    namespace:'production',    chart:'nebulaops-22.2.0',              status:'deployed', revision:14, age:ago(180) },
    ],
  };
}

export function statusClass(s: string): string {
  const st = (s ?? '').toLowerCase();
  if (['running','active','deployed','ready','available','succeeded','healthy'].some(k => st.includes(k))) return 'ok';
  if (['error','failed','crashloop','oomkilled','evicted','unhealthy'].some(k => st.includes(k))) return 'danger';
  if (['pending','terminating','unknown','waiting','warning'].some(k => st.includes(k))) return 'warn';
  return 'neutral';
}

export function generatePodYaml(pod: K8sPod): string {
  const labels = Object.entries(pod.labels).map(([k,v]) => `    ${k}: ${v}`).join('\n');
  return `apiVersion: v1
kind: Pod
metadata:
  name: ${pod.name}
  namespace: ${pod.namespace}
  labels:
${labels}
spec:
  nodeName: ${pod.node || 'node-01'}
  containers:
  - name: ${pod.name.split('-')[0]}
    image: ${pod.image}
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
  phase: ${pod.status}
  podIP: ${pod.ip}`;
}

export function generateDeploymentYaml(dep: K8sDeployment): string {
  const labels = Object.entries(dep.selector).map(([k,v]) => `    ${k}: ${v}`).join('\n');
  return `apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${dep.name}
  namespace: ${dep.namespace}
  labels:
${labels}
spec:
  replicas: ${dep.replicas}
  selector:
    matchLabels:
${labels}
  strategy:
    type: ${dep.strategy}
    ${dep.strategy === 'RollingUpdate' ? `rollingUpdate:\n      maxSurge: 25%\n      maxUnavailable: 0` : ''}
  template:
    metadata:
      labels:
${labels}
    spec:
      containers:
      - name: ${dep.name}
        image: ${dep.image}
        imagePullPolicy: IfNotPresent
        resources:
          requests:
            cpu: 100m
            memory: 128Mi
          limits:
            cpu: 500m
            memory: 512Mi
status:
  availableReplicas: ${dep.available}
  replicas: ${dep.replicas}`;
}

export function generateServiceYaml(svc: K8sService): string {
  const sel = Object.entries(svc.selector).map(([k,v]) => `    ${k}: ${v}`).join('\n');
  return `apiVersion: v1
kind: Service
metadata:
  name: ${svc.name}
  namespace: ${svc.namespace}
spec:
  type: ${svc.type}
  clusterIP: ${svc.clusterIP}
  selector:
${sel || '    # no selector'}
  ports:
  - port: ${svc.ports.split(':')[0].replace('/TCP','').replace('/UDP','')}
    targetPort: ${svc.ports.split(':')[0].replace('/TCP','').replace('/UDP','')}
    protocol: TCP`;
}

export function podLogs(pod: K8sPod): string {
  const ts = () => new Date().toISOString();
  if (pod.status === 'CrashLoopBackOff') {
    return [
      `${ts()} INFO  Starting application...`,
      `${ts()} INFO  Loading configuration from /etc/config/app.yaml`,
      `${ts()} ERROR Failed to connect to database: connection refused (postgres:5432)`,
      `${ts()} ERROR Health check failed: dependency unavailable`,
      `${ts()} FATAL Application startup failed with exit code 1`,
      `${ts()} WARN  Restarting container... (attempt ${pod.restarts})`,
    ].join('\n');
  }
  if (pod.status === 'Running') {
    return [
      `${ts()} INFO  Application started successfully`,
      `${ts()} INFO  Listening on :8080`,
      `${ts()} INFO  Connected to database`,
      `${ts()} INFO  GET /health 200 OK 2ms`,
      `${ts()} INFO  GET /api/v1/users 200 OK 45ms`,
      `${ts()} INFO  POST /api/v1/auth/token 200 OK 112ms`,
      `${ts()} INFO  Metrics scraped by Prometheus`,
    ].join('\n');
  }
  return `${ts()} INFO  Container initializing...\n${ts()} INFO  Waiting for dependencies...`;
}
