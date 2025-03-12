import {Component, computed, inject, OnDestroy, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {CdkDragDrop, DragDropModule} from '@angular/cdk/drag-drop';
import {HttpClient} from '@angular/common/http';
import {catchError, forkJoin, of} from 'rxjs';

type MainTab =
    'OVERVIEW'
    | 'TASKS'
    | 'KUBERNETES'
    | 'CONTAINERS'
    | 'TERRAFORM'
    | 'HELM'
    | 'OBSERVABILITY'
    | 'AI OPS'
    | 'CICD'
    | 'GITOPS'
    | 'ENVIRONMENTS'
    | 'TERRAFORM STUDIO'
    | 'INFRA'
    | 'SECURITY'
    | 'COMPLIANCE'
    | 'VULNERABILITIES'
    | 'FINOPS'
    | 'BACKUPS'
    | 'DOCS'
    | 'REGISTRY'
    | 'SERVICE MESH'
    | 'SECRETS'
    | 'DATABASES'
    | 'QUEUES'
    | 'SLO'
    | 'INCIDENTS'
    | 'AUDIT';
type Status = 'TODO' | 'IN_PROGRESS' | 'REVIEW' | 'DONE';
type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
type K8sKind =
    'Namespace'
    | 'Deployment'
    | 'StatefulSet'
    | 'DaemonSet'
    | 'Service'
    | 'Ingress'
    | 'ConfigMap'
    | 'Secret'
    | 'CronJob'
    | 'ReplicaSet'
    | 'Job'
    | 'Endpoint'
    | 'NetworkPolicy'
    | 'PersistentVolume'
    | 'PersistentVolumeClaim'
    | 'StorageClass';

interface Task {
    id: string;
    title: string;
    owner: string;
    priority: Priority;
    status: Status;
}

interface ServiceLog {
    time: string;
    service: string;
    level: string;
    message: string;
}

interface K8sResource {
    id: string;
    kind: K8sKind;
    namespace: string;
    name: string;
    replicas: number;
    status: string;
    yaml: string;
    updatedAt: string;
}

interface K8sSnapshot {
    cluster: { name: string; provider: string; version: string; status: string };
    resources: K8sResource[];
    logs: ServiceLog[];
}

interface DockerContainer {
    id: string;
    name: string;
    image: string;
    status: 'running' | 'stopped' | 'restarting' | 'paused';
    cpu: number;
    memory: number;
    ports: string;
    network: string;
    logs: string[];
}

interface DockerImage {
    repository: string;
    tag: string;
    size: string;
    vulnerabilities: number;
    created: string;
}

interface DockerVolume {
    name: string;
    driver: string;
    mount: string;
    size: string;
}

interface K8sController {
    kind: string;
    name: string;
    namespace: string;
    desired: number;
    available: number;
    strategy: string;
    age: string;
}

interface LensAction {
    title: string;
    target: string;
    command: string;
    severity: Priority;
}

interface TfModule {
    name: string;
    type: string;
    status: string;
    drift: number;
    resources: number;
    command: string;
}

interface PipelineStage {
    name: string;
    status: 'passed' | 'running' | 'queued' | 'blocked';
    duration: string;
    detail: string;
}

interface PipelineDesignerNode {
    id: string;
    name: string;
    status: PipelineDesignerStatus;
    icon: string;
    tool: string;
    x: number;
    y: number;
    yaml: string;
}

interface InfraLink {
    title: string;
    description: string;
    url?: string;
    tab?: MainTab;
    kind: 'external' | 'internal';
    icon: string;
    status: string;
}

interface SecurityFinding {
    id: string;
    area: string;
    severity: Priority;
    title: string;
    remediation: string;
    done: boolean;
}

type SecurityTab = 'SECURITY' | 'COMPLIANCE' | 'VULNERABILITIES';
type ScanStatus = 'PASSED' | 'RUNNING' | 'FAILED' | 'QUEUED';

interface ObservabilityStackItem {
    name: string;
    role: string;
    endpoint: string;
    health: number;
    signal: string;
}

interface TraceHop {
    from: string;
    to: string;
    latency: number;
    status: string;
}

interface GitOpsState {
    sync: string;
    drift: number;
    revision: string;
    health: string;
}

interface DeploymentWave {
    wave: string;
    target: string;
    status: string;
}

interface NebulaEnvironment {
    name: string;
    namespace: string;
    cluster: string;
    health: number;
    cost: number;
    drift: number;
    workspace: string;
}

type PipelineDesignerStatus = 'success' | 'running' | 'queued' | 'blocked';

interface SecurityScan {
    id: string;
    tool: 'Trivy' | 'Docker' | 'SAST' | 'Secrets' | 'Dependency';
    target: string;
    status: ScanStatus;
    critical: number;
    high: number;
    medium: number;
    duration: string;
}

interface CveItem {
    cve: string;
    packageName: string;
    severity: Priority;
    image: string;
    fixVersion: string;
    exploit: string;
}

interface ComplianceControl {
    id: string;
    framework: string;
    title: string;
    score: number;
    status: 'pass' | 'warn' | 'fail';
}

interface ThreatPoint {
    name: string;
    x: number;
    y: number;
    severity: Priority;
    vector: string;
}

interface AiOpsEvent {
    time: string;
    service: string;
    severity: Priority;
    title: string;
    status: string;
    recommendation: string;
}

interface PlatformTool {
    title: string;
    tab: MainTab;
    icon: string;
    description: string;
    status: string;
    category: string;
}

interface AiOpsNode {
    id: string;
    label: string;
    type: string;
    health: number;
    x: number;
    y: number;
    z: number;
    status: 'ok' | 'warn' | 'critical';
}

interface AiOpsAnalysis {
    incidentId: string;
    summary: string;
    rootCause: string;
    confidence: number;
    blastRadius: string[];
    fix: string;
    yaml: string;
    events: AiOpsEvent[];
    nodes: AiOpsNode[];
}

interface ClusterNode3D {
    id: string;
    label: string;
    role: string;
    cpu: number;
    ram: number;
    x: number;
    y: number;
    z: number;
    status: 'healthy' | 'warning' | 'critical';
}

interface ClusterEdge {
    from: string;
    to: string;
    traffic: number;
    status: 'ok' | 'hot' | 'critical';
}

interface ClusterEvent {
    time: string;
    type: string;
    target: string;
    message: string;
    severity: Priority;
}

interface LiveMetric {
    label: string;
    value: number;
    unit: string;
    trend: string;
}

interface HomeLauncher {
    title: string;
    subtitle: string;
    kind: 'internal' | 'external';
    tab?: MainTab;
    url?: string;
    icon: string;
    accent: string;
    status: string;
}

const TASKS_KEY = 'nebulaops.v20_2.tasks';
const K8S_KEY = 'nebulaops.v20_2.k8s';
const SESSION_KEY = 'nebulaops.v20_2.session';

function yamlOf(kind: K8sKind, ns: string, name: string, replicas = 1): string {
    if (['Deployment', 'StatefulSet', 'DaemonSet'].includes(kind)) return `apiVersion: apps/v1\nkind: ${kind}\nmetadata:\n  name: ${name}\n  namespace: ${ns}\n  labels:\n    app.kubernetes.io/part-of: nebulaops-v20-2\nspec:\n  replicas: ${kind === 'DaemonSet' ? 0 : replicas}\n  selector:\n    matchLabels:\n      app: ${name}\n  template:\n    metadata:\n      labels:\n        app: ${name}\n    spec:\n      containers:\n        - name: ${name}\n          image: nginx:1.27-alpine\n          ports:\n            - containerPort: 80\n`;
    if (kind === 'Service') return `apiVersion: v1\nkind: Service\nmetadata:\n  name: ${name}\n  namespace: ${ns}\nspec:\n  selector:\n    app: ${name}\n  ports:\n    - port: 80\n      targetPort: 80\n`;
    if (kind === 'Ingress') return `apiVersion: networking.k8s.io/v1\nkind: Ingress\nmetadata:\n  name: ${name}\n  namespace: ${ns}\nspec:\n  rules:\n    - host: nebulaops.local\n      http:\n        paths:\n          - path: /\n            pathType: Prefix\n            backend:\n              service:\n                name: frontend\n                port:\n                  number: 80\n`;
    if (kind === 'CronJob') return `apiVersion: batch/v1\nkind: CronJob\nmetadata:\n  name: ${name}\n  namespace: ${ns}\nspec:\n  schedule: \"*/15 * * * *\"\n  jobTemplate:\n    spec:\n      template:\n        spec:\n          restartPolicy: OnFailure\n          containers:\n            - name: ${name}\n              image: busybox:1.36\n              command: [\"sh\", \"-c\", \"date && echo nebulaops backup\"]\n`;
    return `apiVersion: v1\nkind: ${kind}\nmetadata:\n  name: ${name}\n  namespace: ${ns}\n`;
}

function res(kind: K8sKind, namespace: string, name: string, replicas = 0): K8sResource {
    return {
        id: `${kind}:${namespace}:${name}`,
        kind,
        namespace,
        name,
        replicas,
        status: kind === 'Deployment' ? 'Available' : 'Ready',
        yaml: yamlOf(kind, namespace, name, replicas),
        updatedAt: new Date().toISOString()
    };
}

@Component({
    selector: 'app-root',
    standalone: true,
    imports: [CommonModule, FormsModule, DragDropModule],
    templateUrl: './app.component.html',
    styleUrl: './app.component.css'
})
export class AppComponent implements OnInit, OnDestroy {
    readonly tabs: MainTab[] = ['OVERVIEW', 'INFRA', 'TASKS', 'CONTAINERS', 'KUBERNETES', 'REGISTRY', 'TERRAFORM', 'TERRAFORM STUDIO', 'HELM', 'GITOPS', 'CICD', 'OBSERVABILITY', 'SLO', 'INCIDENTS', 'AI OPS', 'SECURITY', 'COMPLIANCE', 'VULNERABILITIES', 'SECRETS', 'SERVICE MESH', 'DATABASES', 'QUEUES', 'ENVIRONMENTS', 'FINOPS', 'BACKUPS', 'AUDIT', 'DOCS'];
    readonly sidebarGroups = [
        {name: 'Command', tabs: ['OVERVIEW', 'INFRA', 'TASKS', 'DOCS'] as MainTab[]},
        {name: 'Runtime', tabs: ['CONTAINERS', 'KUBERNETES', 'REGISTRY', 'HELM', 'SERVICE MESH'] as MainTab[]},
        {name: 'Delivery', tabs: ['CICD', 'GITOPS', 'TERRAFORM', 'TERRAFORM STUDIO', 'ENVIRONMENTS'] as MainTab[]},
        {name: 'Reliability', tabs: ['OBSERVABILITY', 'SLO', 'INCIDENTS', 'AI OPS'] as MainTab[]},
        {name: 'Security', tabs: ['SECURITY', 'COMPLIANCE', 'VULNERABILITIES', 'SECRETS', 'AUDIT'] as MainTab[]},
        {name: 'Data & Cost', tabs: ['DATABASES', 'QUEUES', 'FINOPS', 'BACKUPS'] as MainTab[]}
    ];
    readonly sideCollapsed = signal(false);
    readonly openSideGroups = signal<Record<string, boolean>>({
        OpenLens: true,
        Workloads: true,
        Network: true,
        Storage: true,
        'Docker Desktop': true,
        Platform: true
    });
    readonly activeLensSection = signal('Cluster');
    readonly activeDockerSection = signal('Containers');
    readonly sideTree: any[] = [
        {
            name: 'OpenLens', icon: '☸', children: [
                {label: 'Cluster', tab: 'KUBERNETES', lens: 'Cluster'},
                {label: 'Nodes', tab: 'KUBERNETES', lens: 'Nodes'},
                {
                    label: 'Workloads', group: 'Workloads', children: [
                        {label: 'Overview', tab: 'KUBERNETES', lens: 'Workloads Overview'},
                        {label: 'Pods', tab: 'KUBERNETES', lens: 'Pods'},
                        {label: 'Deployments', tab: 'KUBERNETES', lens: 'Deployments'},
                        {label: 'DaemonSets', tab: 'KUBERNETES', lens: 'DaemonSets'},
                        {label: 'StatefulSets', tab: 'KUBERNETES', lens: 'StatefulSets'},
                        {label: 'ReplicaSets', tab: 'KUBERNETES', lens: 'ReplicaSets'},
                        {label: 'Jobs', tab: 'KUBERNETES', lens: 'Jobs'},
                        {label: 'CronJobs', tab: 'KUBERNETES', lens: 'CronJobs'}
                    ]
                },
                {label: 'Config', tab: 'KUBERNETES', lens: 'Config'},
                {
                    label: 'Network', group: 'Network', children: [
                        {label: 'Services', tab: 'KUBERNETES', lens: 'Services'},
                        {label: 'Endpoints', tab: 'KUBERNETES', lens: 'Endpoints'},
                        {label: 'Ingresses', tab: 'KUBERNETES', lens: 'Ingresses'},
                        {label: 'Network Policies', tab: 'KUBERNETES', lens: 'Network Policies'},
                        {label: 'Port Forwarding', tab: 'KUBERNETES', lens: 'Port Forwarding'}
                    ]
                },
                {
                    label: 'Storage', group: 'Storage', children: [
                        {label: 'Persistent Volume Claims', tab: 'KUBERNETES', lens: 'Persistent Volume Claims'},
                        {label: 'Persistent Volumes', tab: 'KUBERNETES', lens: 'Persistent Volumes'},
                        {label: 'Storage Classes', tab: 'KUBERNETES', lens: 'Storage Classes'}
                    ]
                },
                {label: 'Namespaces', tab: 'KUBERNETES', lens: 'Namespaces'},
                {label: 'Events', tab: 'KUBERNETES', lens: 'Events'},
                {label: 'Helm', tab: 'HELM', lens: 'Helm'}
            ]
        },
        {
            name: 'Docker Desktop', icon: '🐳', children: [
                {label: 'Containers', tab: 'CONTAINERS', docker: 'Containers'},
                {label: 'Images', tab: 'CONTAINERS', docker: 'Images'},
                {label: 'Volumes', tab: 'CONTAINERS', docker: 'Volumes'},
                {label: 'Builds', tab: 'CONTAINERS', docker: 'Builds'},
                {label: 'Dev Environments', tab: 'CONTAINERS', docker: 'Dev Environments'},
                {label: 'Docker Scout', tab: 'CONTAINERS', docker: 'Docker Scout'}
            ]
        },
        {
            name: 'Platform', icon: '✦', children: [
                {label: 'Dashboard', tab: 'OVERVIEW'}, {label: 'INFRA', tab: 'INFRA'}, {label: 'CI/CD', tab: 'CICD'},
                {label: 'GitOps', tab: 'GITOPS'}, {label: 'Observability', tab: 'OBSERVABILITY'}, {
                    label: 'Security',
                    tab: 'SECURITY'
                },
                {label: 'Terraform', tab: 'TERRAFORM'}, {label: 'Docs', tab: 'DOCS'}
            ]
        }
    ];
    readonly platformTools: PlatformTool[] = [
        {
            title: 'Container Registry',
            tab: 'REGISTRY',
            icon: '📦',
            description: 'image inventory, tags, SBOM, scan status, promotions',
            status: 'dynamic UI ready',
            category: 'Runtime'
        },
        {
            title: 'Service Mesh',
            tab: 'SERVICE MESH',
            icon: '🕸️',
            description: 'Istio/Linkerd traffic split, mTLS, retries and routes',
            status: 'mesh cockpit',
            category: 'Runtime'
        },
        {
            title: 'Secrets Vault',
            tab: 'SECRETS',
            icon: '🔐',
            description: 'Vault/K8s secrets rotation, expiry and policy view',
            status: 'rotation center',
            category: 'Security'
        },
        {
            title: 'Database Ops',
            tab: 'DATABASES',
            icon: '🛢️',
            description: 'MongoDB/Postgres health, connections, backups and slow queries',
            status: 'live cards',
            category: 'Data'
        },
        {
            title: 'Queues & Streams',
            tab: 'QUEUES',
            icon: '📨',
            description: 'RabbitMQ/Kafka lag, consumers, DLQ and throughput',
            status: 'event cockpit',
            category: 'Data'
        },
        {
            title: 'SLO Center',
            tab: 'SLO',
            icon: '🎯',
            description: 'availability, latency, error budget and burn-rate monitor',
            status: 'reliability',
            category: 'Reliability'
        },
        {
            title: 'Incident Room',
            tab: 'INCIDENTS',
            icon: '🚨',
            description: 'timeline, ownership, mitigation steps and postmortem notes',
            status: 'war room',
            category: 'Reliability'
        },
        {
            title: 'Audit Trail',
            tab: 'AUDIT',
            icon: '🧾',
            description: 'operator actions, deploy events and compliance evidence',
            status: 'governance',
            category: 'Security'
        }
    ];
    readonly columns: Status[] = ['TODO', 'IN_PROGRESS', 'REVIEW', 'DONE'];
    readonly kinds: K8sKind[] = ['Namespace', 'Deployment', 'StatefulSet', 'DaemonSet', 'Service', 'Ingress', 'ConfigMap', 'Secret', 'CronJob'];
    readonly priorities: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
    readonly activeTab = signal<MainTab>('OVERVIEW');
    readonly isAuthenticated = signal(localStorage.getItem(SESSION_KEY) === 'active');
    readonly currentUser = signal(localStorage.getItem('nebulaops.v20_4.user') || 'admin');
    readonly loginError = signal('');
    readonly apiError = signal('');
    readonly runtimeState = signal<'local' | 'syncing' | 'connected' | 'error'>('local');
    readonly k8sState = signal<'local' | 'syncing' | 'connected' | 'error'>('local');
    readonly tasks = signal<Task[]>(this.loadLocal(TASKS_KEY, [
        {
            id: 'V19-101',
            title: 'Provision local kind cluster with Terraform',
            owner: 'Platform',
            priority: 'CRITICAL',
            status: 'TODO'
        },
        {
            id: 'V19-102',
            title: 'Add FinOps budget guardrails for live services',
            owner: 'SRE',
            priority: 'HIGH',
            status: 'IN_PROGRESS'
        },
        {
            id: 'V19-103',
            title: 'Validate Helm render and ArgoCD sync gates',
            owner: 'DevOps',
            priority: 'HIGH',
            status: 'REVIEW'
        },
        {
            id: 'V19-104',
            title: 'Document WSL onboarding and smoke tests',
            owner: 'Peyman',
            priority: 'MEDIUM',
            status: 'DONE'
        }
    ]));
    readonly resources = signal<K8sResource[]>([]);
    readonly selected = signal<K8sResource | null>(this.resources()[0] ?? null);
    readonly logs = signal<ServiceLog[]>([]);
    readonly activeNamespace = signal('all');
    readonly activeKind = signal('all');
    readonly activeLogService = signal('all');
    readonly logsAutoRefresh = signal(false);
    readonly logsRefreshSeconds = signal(5);
    readonly lastLogsRefresh = signal('never');
    readonly dockerContainers = signal<DockerContainer[]>([]);
    readonly dockerImages = signal<DockerImage[]>([]);
    readonly dockerVolumes = signal<DockerVolume[]>([]);
    readonly selectedDockerContainer = signal<DockerContainer | null>(null);
    readonly containerTerminal = signal('docker compose ps && kubectl get pods -A');
    readonly k8sControllers = signal<K8sController[]>([]);
    readonly lensActions = signal<LensAction[]>([
        {
            title: 'Restart selected pod',
            target: 'Pod',
            command: 'kubectl rollout restart deployment/gateway-service -n nebulaops',
            severity: 'HIGH'
        },
        {
            title: 'Scale workload +1',
            target: 'Deployment',
            command: 'kubectl scale deploy/frontend --replicas=3 -n nebulaops',
            severity: 'MEDIUM'
        },
        {
            title: 'Edit service YAML',
            target: 'Service',
            command: 'kubectl edit service gateway-service -n nebulaops',
            severity: 'LOW'
        },
        {
            title: 'Describe ingress',
            target: 'Ingress',
            command: 'kubectl describe ingress nebulaops-ingress -n nebulaops',
            severity: 'LOW'
        },
        {
            title: 'Drain selected node',
            target: 'Node',
            command: 'kubectl drain kind-worker --ignore-daemonsets',
            severity: 'CRITICAL'
        }
    ]);
    readonly helmReleases = signal<any[]>([]);
    readonly grafanaHealth = signal<any>({});
    readonly terraformPlan = signal('terraform plan not executed yet');
    readonly selectedTf = signal('local-kind');
    readonly aiOpsPrompt = signal('pod gateway-service-78fdd CrashLoopBackOff after deploy, readiness probe failed, image pull warning');
    readonly aiOpsRunning = signal(false);
    readonly aiOpsAutoFix = signal(false);
    readonly aiOpsAnalysis = signal<AiOpsAnalysis>(this.fallbackAiOps());
    readonly aiOpsChat = signal<{ role: 'ai' | 'user' | 'system'; text: string; time: string }[]>([
        {
            role: 'ai',
            text: 'AI Ops Center online. Monitoring Kubernetes events, logs and dependency blast radius.',
            time: new Date().toLocaleTimeString()
        }
    ]);
    loginForm = {username: 'admin', password: 'admin'};
    taskForm: Task = {id: '', title: '', owner: 'Platform', priority: 'MEDIUM', status: 'TODO'};
    k8sForm = {kind: 'Deployment' as K8sKind, namespace: 'nebulaops', name: '', replicas: 1};
    readonly selectedClusterMode = signal<'topology' | 'logs' | 'terminal' | 'metrics' | 'events' | 'yaml'>('topology');
    readonly terminalCommand = signal('kubectl describe pod gateway-service-78fdd -n nebulaops');
    readonly liveClusterNodes = signal<ClusterNode3D[]>(this.buildClusterNodes());
    readonly clusterEdges = signal<ClusterEdge[]>([]);
    readonly liveMetrics = signal<LiveMetric[]>([]);
    readonly homeLaunchers: HomeLauncher[] = [
        {
            title: 'Grafana',
            subtitle: 'Dashboards, logs, metrics',
            kind: 'external',
            url: 'http://localhost:3000',
            icon: '◉',
            accent: 'grafana',
            status: 'localhost:3000'
        },
        {
            title: 'ArgoCD',
            subtitle: 'GitOps sync center',
            kind: 'external',
            url: 'http://localhost:8087/actuator/health',
            icon: '∞',
            accent: 'argocd',
            status: 'pipeline:8087'
        },
        {
            title: 'CI/CD Designer',
            subtitle: 'Drag & drop pipeline canvas',
            kind: 'internal',
            tab: 'CICD',
            icon: '⚡',
            accent: 'cicd',
            status: 'v20.4'
        },
        {
            title: 'INFRA',
            subtitle: 'External links hub',
            kind: 'internal',
            tab: 'INFRA',
            icon: '▣',
            accent: 'infra',
            status: 'links'
        },
        {
            title: 'Prometheus',
            subtitle: 'Metrics query engine',
            kind: 'external',
            url: 'http://localhost:9090',
            icon: '▲',
            accent: 'prometheus',
            status: 'localhost:9090'
        },
        {
            title: 'AI OPS',
            subtitle: 'RCA, anomaly, auto-fix',
            kind: 'internal',
            tab: 'AI OPS',
            icon: '✦',
            accent: 'aiops',
            status: 'cockpit'
        },
        {
            title: 'Containers',
            subtitle: 'Docker Desktop + OpenLens console',
            kind: 'internal',
            tab: 'CONTAINERS',
            icon: '🐳',
            accent: 'docker',
            status: 'runtime ops'
        },
        {
            title: 'K8S Cluster',
            subtitle: '3D topology + pod drilldown',
            kind: 'internal',
            tab: 'KUBERNETES',
            icon: '☸',
            accent: 'kubernetes',
            status: 'live'
        },
        {
            title: 'Security',
            subtitle: 'DevSecOps scans + CVE',
            kind: 'internal',
            tab: 'SECURITY',
            icon: '⬢',
            accent: 'security',
            status: 'v20.4'
        },
        {
            title: 'Helm',
            subtitle: 'Release lifecycle',
            kind: 'internal',
            tab: 'HELM',
            icon: '⎈',
            accent: 'helm',
            status: 'releases'
        },
        {
            title: 'Observability',
            subtitle: 'Prometheus · Loki · Tempo · OTel',
            kind: 'internal',
            tab: 'OBSERVABILITY',
            icon: '≋',
            accent: 'observability',
            status: 'enterprise'
        },
        {
            title: 'GitOps Control Plane',
            subtitle: 'Drift, rollback, ArgoCD live sync',
            kind: 'internal',
            tab: 'GITOPS',
            icon: '∞',
            accent: 'argocd',
            status: 'v20.4'
        },
        {
            title: 'Multi-Env Manager',
            subtitle: 'LOCAL · DEV · STAGING · PROD',
            kind: 'internal',
            tab: 'ENVIRONMENTS',
            icon: '◈',
            accent: 'infra',
            status: 'namespaces'
        },
        {
            title: 'Terraform Studio',
            subtitle: 'Digital twin graph + plan + cost',
            kind: 'internal',
            tab: 'TERRAFORM STUDIO',
            icon: 'T',
            accent: 'terraform',
            status: '3D graph'
        }
    ];

    readonly terraformModules: TfModule[] = [
        {
            name: 'local-kind',
            type: 'cluster',
            status: 'ready',
            drift: 0,
            resources: 4,
            command: 'cd infrastructure/terraform && terraform apply -var="target=kind"'
        },
        {
            name: 'observability',
            type: 'stack',
            status: 'planned',
            drift: 1,
            resources: 6,
            command: 'terraform apply -target=module.observability'
        },
        {
            name: 'gitops-bootstrap',
            type: 'argocd',
            status: 'planned',
            drift: 0,
            resources: 5,
            command: 'terraform apply -target=module.gitops'
        },
        {
            name: 'security-baseline',
            type: 'policy',
            status: 'ready',
            drift: 0,
            resources: 7,
            command: 'terraform apply -target=module.security'
        }
    ];
    readonly findings = signal<SecurityFinding[]>([
        {
            id: 'SEC-001',
            area: 'Container',
            severity: 'HIGH',
            title: 'Use non-root runtime images',
            remediation: 'Keep final images slim and run processes as non-root where practical.',
            done: false
        },
        {
            id: 'SEC-002',
            area: 'Secrets',
            severity: 'CRITICAL',
            title: 'Never commit real secrets',
            remediation: 'Use secrets.example.yaml and environment-only overrides.',
            done: true
        },
        {
            id: 'SEC-003',
            area: 'Terraform',
            severity: 'HIGH',
            title: 'Protect state and outputs',
            remediation: 'Keep local state private; redact sensitive outputs before sharing.',
            done: false
        }
    ]);
    readonly securitySubTab = signal<SecurityTab>('SECURITY');
    readonly riskScore = signal(87);
    readonly securityScans = signal<SecurityScan[]>([]);
    readonly cveDashboard = signal<CveItem[]>([]);
    readonly complianceControls = signal<ComplianceControl[]>([]);


    readonly observabilityStack = signal<ObservabilityStackItem[]>([]);
    readonly traceFlow = signal<TraceHop[]>([]);
    readonly latencyHeatmap = signal<number[]>([]);
    readonly kafkaEvents = signal<string[]>([]);
    readonly gitOpsState = signal({
        sync: 'Unavailable',
        drift: 0,
        revision: 'unknown',
        health: 'Waiting for ArgoCD live data'
    });
    readonly deploymentWaves = signal<DeploymentWave[]>([]);
    readonly commitStream = signal<string[]>([]);
    readonly environments = signal<any[]>([]);
    readonly activeEnvironment = signal('LOCAL');
    readonly terraformStudioNodes = [
        {name: 'VPC', type: 'network', x: 12, y: 42, z: 8, cost: 18, drift: 0},
        {name: 'Subnets', type: 'network', x: 29, y: 25, z: 14, cost: 12, drift: 0},
        {name: 'Load Balancer', type: 'edge', x: 46, y: 38, z: 22, cost: 44, drift: 1},
        {name: 'K8s Cluster', type: 'compute', x: 62, y: 54, z: 30, cost: 210, drift: 0},
        {name: 'MongoDB', type: 'database', x: 78, y: 30, z: 16, cost: 65, drift: 1},
        {name: 'Redis', type: 'cache', x: 84, y: 68, z: 12, cost: 25, drift: 0}
    ];
    readonly terraformPlanPreview = `Terraform will perform the following actions:\n  + module.vpc.aws_vpc.main\n  + module.eks.aws_eks_cluster.nebulaops\n  ~ module.mongodb.storage_size 20Gi -> 40Gi\n  + module.observability.helm_release.tempo\nPlan: 12 to add, 3 to change, 0 to destroy.`;
    readonly terraformCostEstimate = signal({monthly: 374, delta: 42, currency: 'EUR'});

    readonly threatPoints = signal<ThreatPoint[]>([]);


    readonly pipelineDesignerNodes = signal<PipelineDesignerNode[]>([
        {
            id: 'build', name: 'Build', status: 'success', icon: '⚙', tool: 'npm + maven', x: 7, y: 42, yaml: `build:
  stage: build
  script:
    - npm ci
    - mvn -q package`
        },
        {
            id: 'test', name: 'Test', status: 'success', icon: '✓', tool: 'unit/integration', x: 23, y: 42, yaml: `test:
  stage: test
  script:
    - npm test
    - mvn test`
        },
        {
            id: 'security',
            name: 'Security Scan',
            status: 'running',
            icon: '⬢',
            tool: 'Trivy + SAST',
            x: 39,
            y: 42,
            yaml: `security_scan:
  stage: security
  script:
    - trivy fs .
    - gitleaks detect`
        },
        {
            id: 'docker', name: 'Docker Build', status: 'queued', icon: '◆', tool: 'buildx', x: 55, y: 42, yaml: `docker_build:
  stage: package
  script:
    - docker build -t nebulaops/frontend:$CI_COMMIT_SHA frontend`
        },
        {
            id: 'helm', name: 'Helm Deploy', status: 'queued', icon: '⎈', tool: 'helm upgrade', x: 71, y: 42, yaml: `helm_deploy:
  stage: deploy
  script:
    - helm upgrade --install nebulaops infrastructure/helm`
        },
        {
            id: 'smoke', name: 'Smoke Test', status: 'blocked', icon: '☁', tool: 'curl + ArgoCD', x: 87, y: 42, yaml: `smoke_test:
  stage: verify
  script:
    - curl -f http://gateway-service:8080/actuator/health`
        }
    ]);
    readonly selectedPipelineNode = signal<PipelineDesignerNode | null>(null);
    readonly pipelineYaml = computed(() => this.pipelineDesignerNodes().map(n => n.yaml).join('\n\n'));
    readonly infraLinks: InfraLink[] = [
        {
            title: 'Grafana',
            description: 'Dashboards, Loki logs and runtime metrics',
            kind: 'external',
            url: 'http://localhost:3000',
            icon: '◉',
            status: 'localhost:3000'
        },
        {
            title: 'Redis Commander',
            description: 'Redis browser, keys, cache inspection and commands',
            kind: 'external',
            url: 'http://localhost:8089',
            icon: '◆',
            status: 'localhost:8089'
        },
        {
            title: 'Mongo Express',
            description: 'MongoDB collections, documents and indexes console',
            kind: 'external',
            url: 'http://localhost:8088',
            icon: '▰',
            status: 'localhost:8088'
        },
        {
            title: 'RabbitMQ',
            description: 'Queue dashboard, exchanges, bindings and consumers',
            kind: 'external',
            url: 'http://localhost:15672',
            icon: '✉',
            status: 'localhost:15672'
        },
        {
            title: 'Prometheus',
            description: 'Metrics query engine, targets and service discovery',
            kind: 'external',
            url: 'http://localhost:9090',
            icon: '▲',
            status: 'localhost:9090'
        },
        {
            title: 'Loki',
            description: 'Centralized logs backend for Grafana Explore',
            kind: 'external',
            url: 'http://localhost:3100/ready',
            icon: '≋',
            status: 'localhost:3100'
        },
        {
            title: 'Tempo',
            description: 'Distributed tracing backend and span storage',
            kind: 'external',
            url: 'http://localhost:3200/ready',
            icon: '⌁',
            status: 'localhost:3200'
        },
        {
            title: 'OpenTelemetry Collector',
            description: 'OTLP traces, metrics and logs ingestion endpoint',
            kind: 'external',
            url: 'http://localhost:4318',
            icon: '◎',
            status: 'localhost:4318'
        },
        {
            title: 'ArgoCD Console',
            description: 'GitOps application console, sync and rollback',
            kind: 'external',
            url: 'http://localhost:8081',
            icon: '∞',
            status: 'localhost:8081'
        },
        {
            title: 'Gateway API',
            description: 'Public API entrypoint and actuator health',
            kind: 'external',
            url: 'http://localhost:8080/actuator/health',
            icon: '⇄',
            status: 'localhost:8080'
        },
        {
            title: 'Pipeline Engine API',
            description: 'CI/CD Pipeline Designer backend service health',
            kind: 'external',
            url: 'http://localhost:8087/actuator/health',
            icon: '⚙',
            status: 'localhost:8087'
        },
        {
            title: 'ArgoCD Integration',
            description: 'Open the CI/CD designer GitOps sync and rollback gates',
            kind: 'internal',
            tab: 'CICD',
            icon: '∞',
            status: 'internal'
        },
        {
            title: 'Docker Desktop Console',
            description: 'Containers, images, volumes, networks, logs and exec terminal',
            kind: 'internal',
            tab: 'CONTAINERS',
            icon: '🐳',
            status: 'internal'
        },
        {
            title: 'OpenLens Console',
            description: 'Pods, deployments, services, controllers, ingress, scale, restart and YAML editor',
            kind: 'internal',
            tab: 'CONTAINERS',
            icon: '☸',
            status: 'internal'
        },
        {
            title: 'Kubernetes Console',
            description: '3D cluster topology, logs, events, metrics and YAML',
            kind: 'internal',
            tab: 'KUBERNETES',
            icon: '☸',
            status: 'internal'
        },
        {
            title: 'CI/CD Designer',
            description: 'Build/test/security/docker/helm/smoke pipeline canvas',
            kind: 'internal',
            tab: 'CICD',
            icon: '⚡',
            status: 'internal'
        },
        {
            title: 'DevSecOps',
            description: 'Trivy, Docker scan, SAST, secrets, CVE and compliance',
            kind: 'internal',
            tab: 'SECURITY',
            icon: '⬢',
            status: 'internal'
        },
        {
            title: 'Observability',
            description: 'Prometheus, Loki, Tempo, Grafana and OpenTelemetry',
            kind: 'internal',
            tab: 'OBSERVABILITY',
            icon: '◌',
            status: 'internal'
        },
        {
            title: 'GitOps Control Plane',
            description: 'Sync state, drift detection, live sync and visual rollback',
            kind: 'internal',
            tab: 'GITOPS',
            icon: '∞',
            status: 'internal'
        },
        {
            title: 'Multi-Environment Manager',
            description: 'LOCAL, DEV, STAGING and PROD cluster switching',
            kind: 'internal',
            tab: 'ENVIRONMENTS',
            icon: '◈',
            status: 'internal'
        },
        {
            title: 'Smart Terraform Studio',
            description: '3D infra graph, plan preview, cost and drift',
            kind: 'internal',
            tab: 'TERRAFORM STUDIO',
            icon: 'T',
            status: 'internal'
        },
        {
            title: 'AI OPS',
            description: 'RCA, anomaly detection and auto-fix from live telemetry',
            kind: 'internal',
            tab: 'AI OPS',
            icon: '✦',
            status: 'internal'
        },
        {
            title: 'FinOps',
            description: 'Cloud cost scorecards and waste reduction',
            kind: 'internal',
            tab: 'FINOPS',
            icon: '€',
            status: 'internal'
        },
        {
            title: 'Backups',
            description: 'Restore points, snapshots and DR checks',
            kind: 'internal',
            tab: 'BACKUPS',
            icon: '↺',
            status: 'internal'
        }
    ];

    readonly pipeline: PipelineStage[] = [
        {name: 'lint', status: 'passed', duration: '18s', detail: 'YAML, Markdown and Angular checks'},
        {
            name: 'terraform validate',
            status: 'passed',
            duration: '21s',
            detail: 'Infrastructure module syntax validated'
        },
        {
            name: 'container build',
            status: 'running',
            duration: '2m 10s',
            detail: 'Docker compose multi-service image build'
        },
        {name: 'helm template', status: 'queued', duration: '-', detail: 'Render Kubernetes manifests'},
        {name: 'argocd sync', status: 'blocked', duration: '-', detail: 'Manual gate for local laptop'}
    ];
    readonly costItems = [
        {name: 'Local Docker/WSL', monthly: 0, note: 'Personal machine runtime'},
        {name: 'Optional VPS showcase', monthly: 12, note: 'Small cloud instance for portfolio showcase'},
        {name: 'Object storage backups', monthly: 3, note: 'Optional remote backup bucket'},
        {name: 'Monitoring retention', monthly: 5, note: 'Optional long retention'}
    ];
    readonly docs = [
        {title: 'README', path: 'README.md', why: 'start here and feature map'},
        {title: 'V19.1 AI Ops', path: 'docs/V19_1_AI_OPS_CENTER.md', why: 'new AI Ops Center feature'},
        {title: 'V19.1 release notes', path: 'docs/V19_1_RELEASE_NOTES.md', why: 'AI Ops upgrade notes'},
        {title: 'Terraform guide', path: 'docs/TERRAFORM_V18_GUIDE.md', why: 'v18 baseline still valid for Terraform'},
        {
            title: 'Architecture SVG',
            path: 'docs/nebulaops-v20-2-ai-ops-architecture.svg',
            why: 'AI Ops cockpit animated SVG'
        },
        {
            title: 'V20.4 DevSecOps',
            path: 'docs/V19_3_DEVSECOPS_MODULE.md',
            why: 'Security, compliance and vulnerability cockpit'
        },
        {title: 'V19.3 release notes', path: 'docs/V19_3_RELEASE_NOTES.md', why: 'DevSecOps module upgrade notes'},
        {
            title: 'V20.4 release notes',
            path: 'docs/V20_1_RELEASE_NOTES.md',
            why: 'Observability, GitOps, environments and Terraform Studio'
        },
        {
            title: 'V20.4 Observability',
            path: 'docs/V20_1_ADVANCED_OBSERVABILITY.md',
            why: 'Prometheus, Loki, Tempo, Grafana and OpenTelemetry'
        },
        {
            title: 'V20.4 GitOps Control Plane',
            path: 'docs/V20_1_GITOPS_CONTROL_PLANE.md',
            why: 'drift detection, ArgoCD live sync and rollback'
        },
        {
            title: 'V20.4 Multi-Environment Manager',
            path: 'docs/V20_1_MULTI_ENVIRONMENT_MANAGER.md',
            why: 'LOCAL, DEV, STAGING and PROD provisioning'
        },
        {
            title: 'V20.4 Smart Terraform Studio',
            path: 'docs/V20_1_SMART_TERRAFORM_STUDIO.md',
            why: 'digital twin graph, plan preview and cost estimation'
        },
        {
            title: 'V20.4 CI/CD Designer',
            path: 'docs/V20_1_CICD_PIPELINE_DESIGNER.md',
            why: 'drag-drop pipeline canvas and pipeline-engine-service'
        },
        {
            title: 'DevSecOps SVG',
            path: 'docs/nebulaops-v20-2-devsecops-module.svg',
            why: 'Radar, threat map and CVE dashboard architecture'
        }
    ];
    readonly workloads = computed(() => this.resources().filter(r => ['Deployment', 'StatefulSet', 'DaemonSet'].includes(r.kind)));
    readonly selectedNode = computed<ClusterNode3D>(() => {
        const fallback: ClusterNode3D = {
            id: 'none',
            label: 'Select pod',
            role: 'none',
            cpu: 0,
            ram: 0,
            x: 50,
            y: 50,
            z: 0,
            status: 'warning'
        };
        const selected = this.selected();
        const nodes = this.liveClusterNodes();
        if (!selected) return nodes[0] ?? fallback;
        return nodes.find(n => n.id === selected.name || n.label.toLowerCase().includes(selected.name.toLowerCase())) ?? nodes[0] ?? fallback;
    });
    readonly monthlyCost = computed(() => this.costItems.reduce((sum, item) => sum + item.monthly, 0));
    readonly openRisks = computed(() => this.findings().filter(f => !f.done).length);
    private http = inject(HttpClient);
    private logsTimer: any = null;

    ngOnInit(): void {
        if (this.isAuthenticated()) {
            this.refreshAll();
        }
    }

    ngOnDestroy(): void {
        this.stopLogsAutoRefresh();
    }

    login(): void {
        const u = this.loginForm.username.trim();
        const p = this.loginForm.password.trim();
        if ((u === 'admin' || u === 'peyman') && p === 'admin') {
            localStorage.setItem(SESSION_KEY, 'active');
            localStorage.setItem('nebulaops.v20_2.user', u);
            this.currentUser.set(u);
            this.isAuthenticated.set(true);
            this.refreshAll();
        } else this.loginError.set('Credenziali locali: admin/admin oppure peyman/admin');
    }

    logout(): void {
        localStorage.removeItem(SESSION_KEY);
        this.isAuthenticated.set(false);
    }


    openInfraLink(item: InfraLink): void {
        if (item.kind === 'internal' && item.tab) {
            this.setTab(item.tab);
            return;
        }
        if (item.url) window.open(item.url, '_blank', 'noopener,noreferrer');
    }

    selectPipelineNode(node: PipelineDesignerNode): void {
        this.selectedPipelineNode.set(node);
    }

    pipelineNodeClass(node: PipelineDesignerNode): string {
        return 'designer-node ' + node.status;
    }

    pipelineDesignerDrop(event: CdkDragDrop<PipelineDesignerNode[]>): void {
        const nodes = [...this.pipelineDesignerNodes()];
        const [moved] = nodes.splice(event.previousIndex, 1);
        if (!moved) return;
        nodes.splice(event.currentIndex, 0, moved);
        this.pipelineDesignerNodes.set(nodes.map((n, i) => ({...n, x: 7 + i * 16, y: 42})));
    }

    runPipelineLive(): void {
        this.refreshAll();
    }


    openLauncher(item: HomeLauncher): void {
        if (item.kind === 'internal' && item.tab) {
            this.setTab(item.tab);
            return;
        }
        if (item.url) window.open(item.url, '_blank', 'noopener,noreferrer');
    }

    setEnvironment(name: string): void {
        this.activeEnvironment.set(name);
    }

    syncGitOpsLive(): void {
        this.loadGitOps();
    }

    toggleSidebar(): void {
        this.sideCollapsed.update(v => !v);
    }

    toggleSideGroup(name: string): void {
        const current = this.openSideGroups();
        this.openSideGroups.set({...current, [name]: !current[name]});
    }

    isSideGroupOpen(name: string): boolean {
        return this.openSideGroups()[name] !== false;
    }

    navigateSide(item: any): void {
        if (item.lens) this.activeLensSection.set(item.lens);
        if (item.docker) this.activeDockerSection.set(item.docker);
        if (item.tab) this.setTab(item.tab as MainTab);
    }

    isSideItemActive(item: any): boolean {
        return this.activeTab() === item.tab && (!item.lens || this.activeLensSection() === item.lens) && (!item.docker || this.activeDockerSection() === item.docker);
    }

    dockerSectionRows(): any[] {
        const section = this.activeDockerSection();
        if (section === 'Images') return this.dockerImages();
        if (section === 'Volumes') return this.dockerVolumes();
        if (section === 'Builds') return this.dockerBuilds();
        if (section === 'Dev Environments') return this.devEnvironments();
        if (section === 'Docker Scout') return this.dockerImages().map(i => ({
            name: i.repository,
            status: i.vulnerabilities ? 'attention' : 'clean',
            image: i.tag,
            time: 'CVE ' + i.vulnerabilities
        }));
        return this.dockerContainers();
    }


    dockerBuilds(): any[] {
        return this.dockerImages().map(i => ({
            name: i.repository,
            status: 'image present',
            image: i.tag,
            time: i.created
        }));
    }

    devEnvironments(): any[] {
        return this.environments().map(e => ({
            name: e.name,
            status: e.health >= 90 ? 'ready' : 'attention',
            image: e.cluster,
            time: e.namespace
        }));
    }

    portForwardRows(): any[] {
        return this.homeLaunchers.filter(l => l.kind === 'external').map(l => ({
            name: l.title,
            namespace: 'local',
            status: l.status || l.url,
            url: l.url
        }));
    }

    lensRows(): any[] {
        const section = this.activeLensSection();
        const resources = this.resources();
        if (section === 'Cluster') return this.liveMetrics();
        if (section === 'Nodes') return this.liveClusterNodes();
        if (section === 'Pods') return resources.filter(r => r.kind === 'Deployment').map(r => ({
            ...r,
            kind: 'Pod',
            name: r.name + '-pod'
        }));
        if (section === 'Deployments') return resources.filter(r => r.kind === 'Deployment');
        if (section === 'DaemonSets') return this.k8sControllers().filter(c => c.kind === 'DaemonSet');
        if (section === 'StatefulSets') return resources.filter(r => r.kind === 'StatefulSet');
        if (section === 'ReplicaSets') return this.k8sControllers().filter(c => c.kind === 'ReplicaSet');
        if (section === 'Jobs') return this.k8sControllers().filter(c => c.kind === 'Job');
        if (section === 'CronJobs') return resources.filter(r => r.kind === 'CronJob');
        if (section === 'Services') return resources.filter(r => r.kind === 'Service');
        if (section === 'Ingresses') return resources.filter(r => r.kind === 'Ingress');
        if (section === 'Namespaces') return Array.from(new Set(resources.map(r => r.namespace))).map(name => ({
            name,
            status: 'Active',
            kind: 'Namespace'
        }));
        if (section === 'Events') return this.clusterEvents();
        if (section === 'Config') return resources.filter(r => r.kind === 'ConfigMap' || r.kind === 'Secret');
        if (section === 'Endpoints') return resources.filter(r => r.kind === 'Endpoint');
        if (section === 'Network Policies') return resources.filter(r => r.kind === 'NetworkPolicy');
        if (section === 'Port Forwarding') return this.portForwardRows();
        if (section.includes('Persistent')) return resources.filter(r => r.kind === 'PersistentVolume' || r.kind === 'PersistentVolumeClaim');
        if (section === 'Storage Classes') return resources.filter(r => r.kind === 'StorageClass');
        return [...resources, ...this.k8sControllers()];
    }

    setTab(tab: MainTab): void {
        this.activeTab.set(tab);
        if (tab === 'COMPLIANCE') this.securitySubTab.set('COMPLIANCE');
        if (tab === 'VULNERABILITIES') this.securitySubTab.set('VULNERABILITIES');
        if (tab === 'SECURITY') this.securitySubTab.set('SECURITY');
        if (tab === 'KUBERNETES' || tab === 'OVERVIEW') this.loadK8sFromApi();
        if (tab === 'HELM') this.loadHelm();
        if (tab === 'OBSERVABILITY') this.refreshLogs(); else this.stopLogsAutoRefresh();
        if (tab === 'AI OPS') this.runAiOpsAnalysis();
    }

    refreshAll(): void {
        this.loadK8sFromApi();
        this.loadHelm();
        this.loadDocker();
        this.loadObservability();
        this.loadGitOps();
        this.loadDevSecOps();
        this.loadEnvironments();
        this.refreshLogs();
        this.runAiOpsAnalysis(false);
    }

    columnTasks(status: Status): Task[] {
        return this.tasks().filter(t => t.status === status);
    }

    drop(event: CdkDragDrop<Task[]>, status: Status): void {
        const item = event.previousContainer.data[event.previousIndex];
        if (!item) return;
        this.tasks.set(this.tasks().map(t => t.id === item.id ? {...t, status} : t));
        this.saveTasks();
    }

    createTask(): void {
        if (!this.taskForm.title.trim()) return;
        this.tasks.set([{
            ...this.taskForm,
            id: this.taskForm.id || `V19-${Date.now().toString().slice(-5)}`
        }, ...this.tasks()]);
        this.saveTasks();
        this.taskForm = {id: '', title: '', owner: 'Platform', priority: 'MEDIUM', status: 'TODO'};
    }

    deleteTask(id: string): void {
        this.tasks.set(this.tasks().filter(t => t.id !== id));
        this.saveTasks();
    }

    priorityClass(p: Priority): string {
        return p.toLowerCase();
    }

    namespaceOptions(): string[] {
        return ['all', ...Array.from(new Set(this.resources().map(r => r.namespace)))];
    }

    visibleResources(): K8sResource[] {
        return this.resources().filter(r => (this.activeNamespace() === 'all' || r.namespace === this.activeNamespace()) && (this.activeKind() === 'all' || r.kind === this.activeKind()));
    }

    setNamespace(e: Event): void {
        this.activeNamespace.set((e.target as HTMLSelectElement).value);
    }

    setKind(e: Event): void {
        this.activeKind.set((e.target as HTMLSelectElement).value);
    }

    selectResource(r: K8sResource): void {
        this.selected.set(r);
    }

    updateSelectedYaml(value: string): void {
        const r = this.selected();
        if (r) this.selected.set({...r, yaml: value});
    }

    createResource(): void {
        if (!this.k8sForm.name.trim()) return;
        const r = res(this.k8sForm.kind, this.k8sForm.namespace, this.k8sForm.name, this.k8sForm.replicas);
        this.resources.set([r, ...this.resources().filter(x => x.id !== r.id)]);
        this.selected.set(r);
        this.saveK8s();
        this.k8sForm = {kind: 'Deployment', namespace: 'nebulaops', name: '', replicas: 1};
    }

    applyYaml(): void {
        const r = this.selected();
        if (!r) return;
        this.resources.set(this.resources().map(x => x.id === r.id ? {
            ...r,
            status: 'Applied',
            updatedAt: new Date().toISOString()
        } : x));
        this.saveK8s();
    }

    deleteResource(r: K8sResource): void {
        this.resources.set(this.resources().filter(x => x.id !== r.id));
        this.selected.set(this.resources()[0] ?? null);
        this.saveK8s();
    }

    scale(r: K8sResource, delta: number): void {
        const next = {...r, replicas: Math.max(0, r.replicas + delta), status: 'Scaled'};
        this.resources.set(this.resources().map(x => x.id === r.id ? next : x));
        this.selected.set(next);
        this.saveK8s();
    }

    loadK8sFromApi(): void {
        this.k8sState.set('syncing');
        this.http.get<K8sSnapshot>('/api/kubernetes/snapshot').pipe(catchError(err => {
            this.apiError.set(this.errorMessage(err));
            return of(null);
        })).subscribe(s => {
            if (s?.resources?.length) {
                this.resources.set(s.resources);
                this.logs.set(s.logs || []);
                this.selected.set(this.resources()[0] ?? null);
                this.k8sState.set(s.cluster.status === 'Connected' ? 'connected' : 'error');
            } else {
                this.k8sState.set('error');
                this.logs.set([]);
            }
        });
    }

    selectDockerContainer(c: DockerContainer): void {
        this.selectedDockerContainer.set(c);
    }

    dockerAction(action: 'start' | 'stop' | 'restart' | 'pause', container?: DockerContainer | null): void {
        const current = container || this.selectedDockerContainer() || this.dockerContainers()[0];
        if (!current) return;
        const nextStatus: DockerContainer['status'] = action === 'stop' ? 'stopped' : action === 'pause' ? 'paused' : action === 'restart' ? 'restarting' : 'running';
        const updated = {
            ...current,
            status: nextStatus,
            logs: [`${new Date().toLocaleTimeString()} docker ${action} ${current.name}`, ...current.logs].slice(0, 8)
        };
        this.dockerContainers.set(this.dockerContainers().map(c => c.id === current.id ? updated : c));
        this.selectedDockerContainer.set(updated);
        if (action === 'restart') {
            setTimeout(() => this.dockerContainers.set(this.dockerContainers().map(c => c.id === current.id ? {
                ...c,
                status: 'running'
            } : c)), 650);
        }
    }

    pruneDocker(): void {
        this.dockerImages.set(this.dockerImages().filter(i => i.repository.startsWith('nebulaops') || i.vulnerabilities === 0));
    }

    scaleController(ctrl: K8sController, delta: number): void {
        const desired = Math.max(0, ctrl.desired + delta);
        const updated = {...ctrl, desired, available: Math.min(desired, Math.max(0, ctrl.available + delta))};
        this.k8sControllers.set(this.k8sControllers().map(c => c.name === ctrl.name && c.kind === ctrl.kind ? updated : c));
        const match = this.resources().find(r => r.name === ctrl.name && (r.kind === 'Deployment' || r.kind === 'StatefulSet' || r.kind === 'DaemonSet'));
        if (match) this.scale(match, delta);
    }

    restartController(ctrl: K8sController): void {
        this.k8sControllers.set(this.k8sControllers().map(c => c.name === ctrl.name && c.kind === ctrl.kind ? {
            ...c,
            available: Math.max(0, c.available - 1)
        } : c));
        const event: ClusterEvent = {
            time: new Date().toLocaleTimeString(),
            type: 'ROLLOUT',
            target: ctrl.name,
            message: `OpenLens-style restart issued for ${ctrl.kind}/${ctrl.name}`,
            severity: 'HIGH'
        };
        this.clusterEvents.set([event, ...this.clusterEvents()].slice(0, 8));
    }

    applyLensAction(action: LensAction): void {
        this.containerTerminal.set(action.command);
        if (action.target === 'Pod') this.restartSelectedPodLive();
    }

    containerTerminalOutput(): string {
        const selected = this.selectedDockerContainer() || this.dockerContainers()[0];
        const dockerSummary = this.dockerContainers()
            .map(c => `${c.name.padEnd(22)} ${c.status.padEnd(10)} ${c.ports}`)
            .join('\n');
        const k8sSummary = this.k8sControllers()
            .map(c => `${c.kind}/${c.name} desired=${c.desired} available=${c.available} ns=${c.namespace}`)
            .join('\n');
        const selectedLogs = (selected?.logs || []).join('\n');

        return `$ ${this.containerTerminal()}

Docker Desktop live data:
${dockerSummary}

OpenLens live data:
${k8sSummary}

Selected logs: ${selected?.name || 'none'}
${selectedLogs}`;
    }

    loadDocker(): void {
        forkJoin({
            containers: this.http.get<any[]>('/api/runtime/docker/containers').pipe(catchError(() => of([]))),
            images: this.http.get<any[]>('/api/runtime/docker/images').pipe(catchError(() => of([]))),
            volumes: this.http.get<any[]>('/api/runtime/docker/volumes').pipe(catchError(() => of([])))
        }).subscribe(r => {
            const containers = r.containers.map((x, i) => this.normalizeDockerContainer(x, i));
            this.dockerContainers.set(containers);
            if (r.images.length) this.dockerImages.set(r.images.map(x => ({
                repository: x.Repository || x.repository || '<none>',
                tag: x.Tag || x.tag || 'latest',
                size: x.Size || x.size || '-',
                vulnerabilities: Number(x.vulnerabilities || 0),
                created: x.CreatedSince || x.created || x.CreatedAt || '-'
            })));
            if (r.volumes.length) this.dockerVolumes.set(r.volumes.map(x => ({
                name: x.Name || x.name,
                driver: x.Driver || x.driver || 'local',
                mount: x.Mountpoint || x.mount || '-',
                size: x.Scope || x.size || '-'
            })));
        });
    }

    normalizeDockerContainer(x: any, i: number): DockerContainer {
        const rawStatus = String(x.status || x.Status || x.State || '').toLowerCase();
        const status: DockerContainer['status'] = rawStatus.includes('pause') ? 'paused' : rawStatus.includes('restart') ? 'restarting' : rawStatus.includes('up') || rawStatus.includes('running') ? 'running' : 'stopped';
        return {
            id: x.id || x.ID || `docker-${i}`,
            name: x.name || x.Names || x.Name || `container-${i}`,
            image: x.image || x.Image || '-',
            status,
            cpu: Number(String(x.CPUPerc || x.cpu || '0').replace('%', '')) || 0,
            memory: Number(String(x.MemUsage || x.memory || '0').split(/[A-Z]/)[0]) || 0,
            ports: x.ports || x.Ports || '-',
            network: x.network || x.Networks || '-',
            logs: x.logs || [`${x.Names || x.Name || 'container'} ${x.Status || 'detected from Docker Engine'}`]
        };
    }

    loadObservability(): void {
        this.http.get<any>('/api/platform/observability').pipe(catchError(() => of(null))).subscribe(data => {
            if (!data) return;
            this.observabilityStack.set(data.stack || []);
            const hops = data.traceFlow || [];
            this.traceFlow.set(hops);
            this.clusterEdges.set(hops.map((h: any) => ({
                from: h.from,
                to: h.to,
                traffic: Number(h.latency || 0),
                status: h.status === 'hot' ? 'critical' : h.status === 'warm' ? 'hot' : 'ok'
            })));
            this.latencyHeatmap.set(data.latencyHeatmap || []);
            this.kafkaEvents.set(data.eventStream || []);
            const stack = data.stack || [];
            const heat = data.latencyHeatmap || [];
            this.liveMetrics.set([
                {
                    label: 'Live services',
                    value: stack.filter((x: any) => x.live).length,
                    unit: '/' + stack.length,
                    trend: data.mode || 'LIVE'
                },
                {label: 'Docker CPU max', value: Math.max(0, ...heat), unit: '%', trend: 'docker stats'},
                {label: 'Docker events', value: (data.eventStream || []).length, unit: '10m', trend: 'docker events'},
                {label: 'Trace hops', value: hops.length, unit: 'links', trend: 'runtime'}
            ]);
        });
    }

    loadGitOps(): void {
        this.http.get<any>('/api/platform/gitops').pipe(catchError(() => of(null))).subscribe(data => {
            if (!data) return;
            this.gitOpsState.set(data.state || this.gitOpsState());
            this.deploymentWaves.set(data.deploymentWaves || this.deploymentWaves());
            this.commitStream.set(data.commitStream || this.commitStream());
        });
    }

    loadDevSecOps(): void {
        this.http.get<any>('/api/platform/devsecops').pipe(catchError(() => of(null))).subscribe(data => {
            if (!data) return;
            if (data.scans) this.securityScans.set(data.scans);
            if (data.cves) this.cveDashboard.set(data.cves);
            if (data.controls) this.complianceControls.set(data.controls);
            if (data.threats) this.threatPoints.set(data.threats);
        });
    }

    loadEnvironments(): void {
        this.http.get<any[]>('/api/platform/environments').pipe(catchError(() => of([]))).subscribe(rows => {
            if (rows.length) this.environments.set(rows);
        });
    }

    loadHelm(): void {
        this.http.get<any[]>('/api/runtime/helm/releases?namespace=all').pipe(catchError(() => of([]))).subscribe(r => this.helmReleases.set(r));
    }

    refreshLogs(): void {
        this.http.get<ServiceLog[]>('/api/kubernetes/logs').pipe(catchError(() => of([]))).subscribe(rows => {
            this.logs.set(rows);
            this.lastLogsRefresh.set(new Date().toLocaleTimeString());
        });
    }

    setLogService(e: Event): void {
        this.activeLogService.set((e.target as HTMLSelectElement).value);
    }

    logServiceOptions(): string[] {
        return ['all', ...Array.from(new Set(this.logs().map(l => l.service)))];
    }

    visibleLogs(): ServiceLog[] {
        return this.logs().filter(l => this.activeLogService() === 'all' || l.service === this.activeLogService()).slice(0, 80);
    }

    setLogsRefreshSeconds(e: Event): void {
        this.logsRefreshSeconds.set(Number((e.target as HTMLSelectElement).value));
        if (this.logsAutoRefresh()) this.startLogsAutoRefresh();
    }

    toggleLogsAutoRefresh(): void {
        this.logsAutoRefresh() ? this.stopLogsAutoRefresh() : this.startLogsAutoRefresh();
    }


    setClusterMode(mode: 'topology' | 'logs' | 'terminal' | 'metrics' | 'events' | 'yaml'): void {
        this.selectedClusterMode.set(mode);
    }

    selectClusterNode(node: ClusterNode3D): void {
        const match = this.resources().find(r => r.name === node.id || node.label.toLowerCase().includes(r.name.toLowerCase())) || this.resources().find(r => r.kind === 'Deployment') || null;
        if (match) this.selected.set(match);
        const severity: Priority = node.status === 'critical' ? 'CRITICAL' : node.status === 'warning' ? 'HIGH' : 'LOW';
        const event: ClusterEvent = {
            time: new Date().toLocaleTimeString(),
            type: 'SELECT',
            target: node.label,
            message: `Opened ${node.label} drilldown with logs, terminal, metrics, events and YAML.`,
            severity
        };
        this.clusterEvents.set([event, ...this.clusterEvents()].slice(0, 8));
    }

    nodeCss(node: ClusterNode3D): Record<string, string> {
        return {
            'left.%': String(node.x),
            'top.%': String(node.y),
            '--z': String(node.z),
            '--cpu': String(node.cpu),
            '--ram': String(node.ram)
        };
    }

    resourceCpu(r: K8sResource | null): number {
        if (!r) return 0;
        return Math.min(96, 28 + (r.name.length * 9 + r.replicas * 11) % 68);
    }

    resourceRam(r: K8sResource | null): number {
        if (!r) return 0;
        return Math.min(98, 34 + (r.name.length * 7 + r.kind.length * 5) % 62);
    }

    resourceRestarts(r: K8sResource | null): number {
        if (!r) return 0;
        return r.name.includes('gateway') ? 3 : r.kind === 'Deployment' ? 1 : 0;
    }

    restartSelectedPodLive(): void {
        this.refreshAll();
    }

    terminalOutput(): string {
        const r = this.selected();
        const name = r?.name || 'gateway-service';
        return `$ ${this.terminalCommand()}

Name: ${name}
Namespace: ${r?.namespace || 'nebulaops'}
Status: ${r?.status || 'Running'}
Restarts: ${this.resourceRestarts(r)}
Events:
  Warning  BackOff     restarting failed container ${name}
  Normal   Pulled      container image ready
  Normal   Started     started container ${name}

Tip: use the AI OPS tab for RCA and AUTO FIX suggestions.`;
    }

    runAiOpsAnalysis(pushChat = true): void {
        this.aiOpsRunning.set(true);
        const prompt = this.aiOpsPrompt();
        if (pushChat) this.aiOpsChat.set([...this.aiOpsChat(), {
            role: 'user',
            text: prompt,
            time: new Date().toLocaleTimeString()
        }]);
        this.http.post<AiOpsAnalysis>('/api/ai-ops/analyze', {
            prompt,
            logs: this.visibleLogs(),
            resources: this.resources()
        }).pipe(catchError(() => of(this.fallbackAiOps(prompt)))).subscribe(result => {
            this.aiOpsAnalysis.set(result);
            this.aiOpsRunning.set(false);
            if (pushChat) this.aiOpsChat.set([...this.aiOpsChat(), {
                role: 'ai',
                text: `${result.summary} Root cause: ${result.rootCause}. Suggested fix: ${result.fix}`,
                time: new Date().toLocaleTimeString()
            }]);
        });
    }

    autoFix(): void {
        const analysis = this.aiOpsAnalysis();
        this.aiOpsAutoFix.set(true);
        this.http.post('/api/ai-ops/autofix', {
            incidentId: analysis.incidentId,
            yaml: analysis.yaml,
            fix: analysis.fix
        }).pipe(catchError(() => of({status: 'unavailable'}))).subscribe(() => {
            this.aiOpsAutoFix.set(false);
            this.aiOpsChat.set([...this.aiOpsChat(), {
                role: 'system',
                text: `AUTO FIX staged: ${analysis.fix}`,
                time: new Date().toLocaleTimeString()
            }]);
        });
    }

    aiSeverityClass(severity: Priority): string {
        return `ai-sev-${severity.toLowerCase()}`;
    }

    runTerraformPlan(moduleName = this.selectedTf()): void {
        const module = this.terraformModules.find(m => m.name === moduleName) || this.terraformModules[0];
        this.selectedTf.set(module.name);
        this.terraformPlan.set(`$ terraform init\n$ terraform validate\n$ terraform plan -var=\"module=${module.name}\"\n\nPlan: ${module.resources} to add, 0 to change, ${module.drift} to replace.\n\nNext command:\n${module.command}`);
    }

    copyCommand(text: string): void {
        navigator.clipboard?.writeText(text);
    }

    toggleFinding(id: string): void {
        this.findings.set(this.findings().map(f => f.id === id ? {...f, done: !f.done} : f));
    }

    setSecuritySubTab(tab: SecurityTab): void {
        this.securitySubTab.set(tab);
    }

    scanWidth(scan: SecurityScan): number {
        return Math.min(100, 8 + scan.critical * 24 + scan.high * 9 + scan.medium * 2);
    }

    scanStatusClass(status: ScanStatus): string {
        return status.toLowerCase();
    }

    controlClass(status: ComplianceControl['status']): string {
        return `control-${status}`;
    }

    threatCss(t: ThreatPoint): Record<string, string> {
        return {'left.%': String(t.x), 'top.%': String(t.y)};
    }

    runSecurityScanLive(): void {
        this.loadDevSecOps();
    }

    stageWidth(s: PipelineStage): number {
        return s.status === 'passed' ? 100 : s.status === 'running' ? 65 : s.status === 'queued' ? 25 : 8;
    }

    private buildClusterNodes(): ClusterNode3D[] {
        return [
            {id: 'ingress', label: 'Ingress', role: 'edge', cpu: 35, ram: 28, x: 10, y: 45, z: 1, status: 'healthy'},
            {
                id: 'frontend',
                label: 'Frontend',
                role: 'pod x2',
                cpu: 58,
                ram: 51,
                x: 28,
                y: 24,
                z: 3,
                status: 'warning'
            },
            {
                id: 'gateway-service',
                label: 'Gateway',
                role: 'svc/pod x2',
                cpu: 91,
                ram: 83,
                x: 47,
                y: 43,
                z: 5,
                status: 'critical'
            },
            {
                id: 'task-service',
                label: 'Tasks',
                role: 'deployment',
                cpu: 62,
                ram: 55,
                x: 68,
                y: 24,
                z: 2,
                status: 'warning'
            },
            {
                id: 'notification-service',
                label: 'Notify',
                role: 'deployment',
                cpu: 43,
                ram: 39,
                x: 66,
                y: 67,
                z: 2,
                status: 'healthy'
            },
            {
                id: 'mongodb',
                label: 'MongoDB',
                role: 'statefulset',
                cpu: 49,
                ram: 71,
                x: 86,
                y: 45,
                z: 1,
                status: 'healthy'
            },
            {id: 'rabbitmq', label: 'RabbitMQ', role: 'queue', cpu: 33, ram: 45, x: 47, y: 75, z: 2, status: 'healthy'},
            {
                id: 'persistent-volume',
                label: 'PV',
                role: 'volume',
                cpu: 18,
                ram: 31,
                x: 88,
                y: 78,
                z: 0,
                status: 'healthy'
            }
        ];
    }

    private syntheticClusterEvents(): ClusterEvent[] {
        const now = new Date().toLocaleTimeString();
        return [
            {
                time: now,
                type: 'Warning',
                target: 'gateway-service',
                message: 'CrashLoopBackOff pulse propagated to frontend and task-service.',
                severity: 'CRITICAL'
            },
            {
                time: now,
                type: 'Normal',
                target: 'frontend',
                message: 'Ingress traffic steady at 1.2k req/s.',
                severity: 'LOW'
            },
            {
                time: now,
                type: 'Scaling',
                target: 'task-service',
                message: 'CPU above threshold; HPA recommendation generated.',
                severity: 'MEDIUM'
            },
            {
                time: now,
                type: 'Storage',
                target: 'persistent-volume',
                message: 'Volume latency normal; no PVC pressure.',
                severity: 'LOW'
            }
        ];
    }

    private fallbackAiOps(prompt = this.aiOpsPrompt()): AiOpsAnalysis {
        const now = new Date().toLocaleTimeString();
        return {
            incidentId: 'AIOPS-19-3-CRASH-042',
            summary: 'CrashLoopBackOff detected on gateway-service with readiness degradation propagated to frontend and notification-service.',
            rootCause: prompt.toLowerCase().includes('image') ? 'Image pull / tag mismatch combined with readiness probe timeout.' : 'Readiness probe timeout after deployment; downstream gateway route saturation detected.',
            confidence: 0.91,
            blastRadius: ['frontend', 'gateway-service', 'task-service', 'notification-service'],
            fix: 'Increase initialDelaySeconds, verify image tag, restart rollout and add temporary HPA guardrail.',
            yaml: `apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: gateway-service\n  namespace: nebulaops\nspec:\n  template:\n    spec:\n      containers:\n        - name: gateway-service\n          readinessProbe:\n            httpGet:\n              path: /actuator/health\n              port: 8080\n            initialDelaySeconds: 25\n            periodSeconds: 10\n`,
            events: [
                {
                    time: now,
                    service: 'gateway-service',
                    severity: 'CRITICAL',
                    title: 'Pod CrashLoopBackOff',
                    status: 'active',
                    recommendation: 'Patch readiness probe and restart rollout'
                },
                {
                    time: now,
                    service: 'frontend',
                    severity: 'HIGH',
                    title: '5xx propagation',
                    status: 'degraded',
                    recommendation: 'Route traffic to healthy gateway replicas'
                },
                {
                    time: now,
                    service: 'task-service',
                    severity: 'MEDIUM',
                    title: 'Queue latency spike',
                    status: 'watch',
                    recommendation: 'Scale worker replicas if queue depth grows'
                },
                {
                    time: now,
                    service: 'mongodb',
                    severity: 'LOW',
                    title: 'No storage anomaly',
                    status: 'stable',
                    recommendation: 'No action'
                }
            ],
            nodes: [
                {id: 'frontend', label: 'Frontend', type: 'edge', health: 72, x: 14, y: 24, z: 1, status: 'warn'},
                {id: 'gateway', label: 'Gateway', type: 'api', health: 18, x: 42, y: 38, z: 4, status: 'critical'},
                {id: 'tasks', label: 'Tasks', type: 'svc', health: 66, x: 69, y: 22, z: 2, status: 'warn'},
                {id: 'mongo', label: 'MongoDB', type: 'db', health: 96, x: 78, y: 66, z: 1, status: 'ok'},
                {id: 'notify', label: 'Notify', type: 'svc', health: 61, x: 30, y: 72, z: 3, status: 'warn'}
            ]
        };
    }

    private saveTasks(): void {
        localStorage.setItem(TASKS_KEY, JSON.stringify(this.tasks()));
    }

    private saveK8s(): void {
        localStorage.setItem(K8S_KEY, JSON.stringify(this.resources()));
    }

    private startLogsAutoRefresh(): void {
        this.stopLogsAutoRefresh(false);
        this.logsAutoRefresh.set(true);
        this.refreshLogs();
        this.logsTimer = setInterval(() => this.refreshLogs(), this.logsRefreshSeconds() * 1000);
    }

    private stopLogsAutoRefresh(update = true): void {
        if (this.logsTimer) clearInterval(this.logsTimer);
        this.logsTimer = null;
        if (update) this.logsAutoRefresh.set(false);
    }

    private syntheticLogs(): ServiceLog[] {
        const now = new Date().toISOString();
        return ['gateway-service', 'task-service', 'terraform-runner', 'go-cache-service', 'notification-service'].map((service, i) => ({
            time: now,
            service,
            level: i === 2 ? 'PLAN' : 'INFO',
            message: `${service} OK · v19.1 AI Ops telemetry`
        }));
    }

    private errorMessage(err: any): string {
        return err?.error?.message || err?.message || 'API non disponibile: nessun dato live disponibile';
    }

    private loadLocal<T>(key: string, fallback: T): T {
        try {
            return JSON.parse(localStorage.getItem(key) || 'null') || fallback;
        } catch {
            return fallback;
        }
    }

    readonly clusterEvents = signal<ClusterEvent[]>([
        {
            time: new Date().toLocaleTimeString(),
            type: 'Warning',
            target: 'gateway-service',
            message: 'Initial cluster state loaded',
            severity: 'MEDIUM'
        }
    ]);
}
