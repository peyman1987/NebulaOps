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
    | 'DOCS';
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
    | 'CronJob';

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

const TASKS_KEY = 'nebulaops.v19_5.tasks';
const K8S_KEY = 'nebulaops.v19_5.k8s';
const SESSION_KEY = 'nebulaops.v19_5.session';

function yamlOf(kind: K8sKind, ns: string, name: string, replicas = 1): string {
    if (['Deployment', 'StatefulSet', 'DaemonSet'].includes(kind)) return `apiVersion: apps/v1\nkind: ${kind}\nmetadata:\n  name: ${name}\n  namespace: ${ns}\n  labels:\n    app.kubernetes.io/part-of: nebulaops-v19-5\nspec:\n  replicas: ${kind === 'DaemonSet' ? 0 : replicas}\n  selector:\n    matchLabels:\n      app: ${name}\n  template:\n    metadata:\n      labels:\n        app: ${name}\n    spec:\n      containers:\n        - name: ${name}\n          image: nginx:1.27-alpine\n          ports:\n            - containerPort: 80\n`;
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
    readonly tabs: MainTab[] = ['OVERVIEW', 'INFRA', 'TASKS', 'CONTAINERS', 'KUBERNETES', 'TERRAFORM', 'TERRAFORM STUDIO', 'HELM', 'OBSERVABILITY', 'GITOPS', 'ENVIRONMENTS', 'AI OPS', 'CICD', 'SECURITY', 'COMPLIANCE', 'VULNERABILITIES', 'FINOPS', 'BACKUPS', 'DOCS'];
    readonly columns: Status[] = ['TODO', 'IN_PROGRESS', 'REVIEW', 'DONE'];
    readonly kinds: K8sKind[] = ['Namespace', 'Deployment', 'StatefulSet', 'DaemonSet', 'Service', 'Ingress', 'ConfigMap', 'Secret', 'CronJob'];
    readonly priorities: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
    readonly activeTab = signal<MainTab>('OVERVIEW');
    readonly isAuthenticated = signal(localStorage.getItem(SESSION_KEY) === 'active');
    readonly currentUser = signal(localStorage.getItem('nebulaops.v19_5.user') || 'admin');
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
            title: 'Add FinOps budget guardrails for demo services',
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
    readonly resources = signal<K8sResource[]>(this.loadLocal(K8S_KEY, [
        res('Namespace', 'nebulaops', 'nebulaops'), res('Deployment', 'nebulaops', 'frontend', 2), res('Deployment', 'nebulaops', 'gateway-service', 2), res('StatefulSet', 'nebulaops', 'mongodb', 1), res('Service', 'nebulaops', 'gateway-service'), res('Ingress', 'nebulaops', 'nebulaops-ingress'), res('ConfigMap', 'nebulaops', 'platform-config'), res('CronJob', 'nebulaops', 'backup-simulator')
    ]));
    readonly selected = signal<K8sResource | null>(this.resources()[0] ?? null);
    readonly logs = signal<ServiceLog[]>([]);
    readonly activeNamespace = signal('all');
    readonly activeKind = signal('all');
    readonly activeLogService = signal('all');
    readonly logsAutoRefresh = signal(false);
    readonly logsRefreshSeconds = signal(5);
    readonly lastLogsRefresh = signal('never');
    readonly dockerContainers = signal<DockerContainer[]>(this.syntheticDockerContainers());
    readonly dockerImages = signal<DockerImage[]>([
        {repository: 'nebulaops/frontend', tag: 'v19.5', size: '186MB', vulnerabilities: 0, created: 'today'},
        {repository: 'nebulaops/gateway-service', tag: 'v19.5', size: '292MB', vulnerabilities: 1, created: 'today'},
        {repository: 'mongo', tag: '7', size: '739MB', vulnerabilities: 2, created: 'cached'},
        {repository: 'redis', tag: '7-alpine', size: '42MB', vulnerabilities: 0, created: 'cached'},
        {repository: 'rabbitmq', tag: '3-management', size: '286MB', vulnerabilities: 1, created: 'cached'}
    ]);
    readonly dockerVolumes = signal<DockerVolume[]>([
        {name: 'nebulaops_mongo_data', driver: 'local', mount: '/data/db', size: '1.2GB'},
        {name: 'nebulaops_redis_data', driver: 'local', mount: '/data', size: '128MB'},
        {name: 'nebulaops_grafana_data', driver: 'local', mount: '/var/lib/grafana', size: '84MB'}
    ]);
    readonly selectedDockerContainer = signal<DockerContainer | null>(null);
    readonly containerTerminal = signal('docker compose ps && kubectl get pods -A');
    readonly k8sControllers = signal<K8sController[]>(this.syntheticK8sControllers());
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
            title: 'Drain node simulation',
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
    readonly clusterEdges = signal<ClusterEdge[]>([
        {from: 'ingress', to: 'frontend', traffic: 82, status: 'hot'},
        {from: 'frontend', to: 'gateway-service', traffic: 94, status: 'critical'},
        {from: 'gateway-service', to: 'task-service', traffic: 68, status: 'hot'},
        {from: 'gateway-service', to: 'notification-service', traffic: 42, status: 'ok'},
        {from: 'task-service', to: 'mongodb', traffic: 57, status: 'ok'},
        {from: 'notification-service', to: 'rabbitmq', traffic: 38, status: 'ok'},
        {from: 'mongodb', to: 'persistent-volume', traffic: 34, status: 'ok'}
    ]);
    readonly clusterEvents = signal<ClusterEvent[]>(this.syntheticClusterEvents());
    readonly liveMetrics = signal<LiveMetric[]>([
        {label: 'Cluster CPU', value: 67, unit: '%', trend: '+8%'},
        {label: 'Cluster RAM', value: 74, unit: '%', trend: '+13%'},
        {label: 'Pod restarts', value: 3, unit: 'last 10m', trend: '+3'},
        {label: 'Ingress RPS', value: 1280, unit: 'req/s', trend: '+21%'}
    ]);
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
            status: 'v19.5'
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
            status: 'v19.5'
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
            status: 'v19.5'
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
            remediation: 'Keep demo state local; redact sensitive outputs before sharing.',
            done: false
        }
    ]);
    readonly securitySubTab = signal<SecurityTab>('SECURITY');
    readonly riskScore = signal(87);
    readonly securityScans = signal<SecurityScan[]>([
        {
            id: 'SCAN-TRIVY-API',
            tool: 'Trivy',
            target: 'gateway-service:19.5.0',
            status: 'RUNNING',
            critical: 1,
            high: 4,
            medium: 9,
            duration: '42s'
        },
        {
            id: 'SCAN-DOCKER-FE',
            tool: 'Docker',
            target: 'frontend:19.5.0',
            status: 'PASSED',
            critical: 0,
            high: 1,
            medium: 4,
            duration: '31s'
        },
        {
            id: 'SCAN-SAST-BE',
            tool: 'SAST',
            target: 'backend/**/*.java',
            status: 'FAILED',
            critical: 1,
            high: 3,
            medium: 7,
            duration: '58s'
        },
        {
            id: 'SCAN-SECRETS',
            tool: 'Secrets',
            target: 'repo tree',
            status: 'PASSED',
            critical: 0,
            high: 0,
            medium: 2,
            duration: '12s'
        },
        {
            id: 'SCAN-DEPS',
            tool: 'Dependency',
            target: 'package-lock/pom.xml',
            status: 'QUEUED',
            critical: 0,
            high: 5,
            medium: 14,
            duration: '-'
        }
    ]);
    readonly cveDashboard = signal<CveItem[]>([
        {
            cve: 'CVE-2025-7421',
            packageName: 'netty-codec-http2',
            severity: 'CRITICAL',
            image: 'gateway-service',
            fixVersion: '4.1.118+',
            exploit: 'remote DoS'
        },
        {
            cve: 'CVE-2025-2198',
            packageName: 'openssl',
            severity: 'HIGH',
            image: 'frontend-nginx',
            fixVersion: '3.3.4-r1',
            exploit: 'TLS edge'
        },
        {
            cve: 'CVE-2024-9982',
            packageName: 'lodash',
            severity: 'HIGH',
            image: 'frontend build',
            fixVersion: '4.17.22+',
            exploit: 'prototype pollution'
        },
        {
            cve: 'CVE-2024-6123',
            packageName: 'spring-web',
            severity: 'MEDIUM',
            image: 'task-service',
            fixVersion: '6.1.15+',
            exploit: 'header parsing'
        }
    ]);
    readonly complianceControls = signal<ComplianceControl[]>([
        {
            id: 'CIS-K8S-1.2.7',
            framework: 'CIS Kubernetes',
            title: 'Disable anonymous API server access',
            score: 96,
            status: 'pass'
        },
        {
            id: 'NIST-SC-7',
            framework: 'NIST 800-53',
            title: 'Network boundary protection and ingress isolation',
            score: 82,
            status: 'warn'
        },
        {
            id: 'SOC2-CC6.1',
            framework: 'SOC2',
            title: 'Least privilege service account policy',
            score: 74,
            status: 'warn'
        },
        {
            id: 'ISO-A.8.8',
            framework: 'ISO 27001',
            title: 'Technical vulnerability management',
            score: 89,
            status: 'pass'
        }
    ]);

    readonly observabilityStack = [
        {name: 'Prometheus', role: 'metrics', endpoint: 'http://localhost:9090', health: 99, signal: '2.4k series'},
        {name: 'Loki', role: 'logs', endpoint: 'http://localhost:3100', health: 96, signal: '18k log lines'},
        {name: 'Tempo', role: 'traces', endpoint: 'http://localhost:3200', health: 94, signal: '742 spans'},
        {name: 'Grafana', role: 'dashboards', endpoint: 'http://localhost:3000', health: 98, signal: '12 panels'},
        {name: 'OpenTelemetry', role: 'collector', endpoint: 'http://localhost:4318', health: 97, signal: 'OTLP active'}
    ];
    readonly traceFlow = [
        {from: 'frontend', to: 'gateway', latency: 24, status: 'ok'},
        {from: 'gateway', to: 'task-service', latency: 68, status: 'warm'},
        {from: 'task-service', to: 'mongodb', latency: 112, status: 'hot'},
        {from: 'notification-service', to: 'rabbitmq', latency: 39, status: 'ok'},
        {from: 'worker', to: 'loki', latency: 51, status: 'warm'}
    ];
    readonly latencyHeatmap = [18, 24, 31, 48, 62, 80, 96, 72, 44, 28, 35, 57, 89, 121, 76, 42];
    readonly kafkaEvents = ['task.created', 'scan.completed', 'deploy.synced', 'trace.exported', 'drift.detected', 'rollback.ready'];
    readonly gitOpsState = signal({sync: 'OutOfSync', drift: 3, revision: 'a19f5c2', health: 'Degraded'});
    readonly deploymentWaves = [
        {wave: 'wave-0', target: 'namespaces + CRDs', status: 'synced'},
        {wave: 'wave-1', target: 'databases + queues', status: 'synced'},
        {wave: 'wave-2', target: 'backend services', status: 'running'},
        {wave: 'wave-3', target: 'frontend + ingress', status: 'pending'}
    ];
    readonly commitStream = ['feat(obs): add tempo traces', 'fix(gitops): rollback gate', 'infra(env): staging namespace', 'tf: cost estimate module'];
    readonly environments = signal([
        {
            name: 'LOCAL',
            namespace: 'nebulaops-local',
            cluster: 'kind-nebula',
            health: 96,
            cost: 0,
            drift: 0,
            workspace: 'local'
        },
        {
            name: 'DEV',
            namespace: 'nebulaops-dev',
            cluster: 'dev-eu-west',
            health: 91,
            cost: 42,
            drift: 1,
            workspace: 'dev'
        },
        {
            name: 'STAGING',
            namespace: 'nebulaops-staging',
            cluster: 'stg-eu-west',
            health: 88,
            cost: 118,
            drift: 2,
            workspace: 'staging'
        },
        {
            name: 'PROD',
            namespace: 'nebulaops-prod',
            cluster: 'prod-eu-west',
            health: 99,
            cost: 410,
            drift: 0,
            workspace: 'prod'
        }
    ]);
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

    readonly threatPoints = signal<ThreatPoint[]>([
        {name: 'Secrets leak', x: 18, y: 32, severity: 'CRITICAL', vector: 'repo'},
        {name: 'Image CVE', x: 46, y: 58, severity: 'HIGH', vector: 'registry'},
        {name: 'Ingress probe', x: 72, y: 28, severity: 'MEDIUM', vector: 'edge'},
        {name: 'Dependency drift', x: 83, y: 72, severity: 'HIGH', vector: 'supply chain'}
    ]);

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
            description: 'RCA, anomaly detection and auto-fix simulation',
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
        {name: 'Optional VPS demo', monthly: 12, note: 'Small cloud instance for portfolio demo'},
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
            path: 'docs/nebulaops-v19-5-ai-ops-architecture.svg',
            why: 'AI Ops cockpit animated SVG'
        },
        {
            title: 'V19.5 DevSecOps',
            path: 'docs/V19_3_DEVSECOPS_MODULE.md',
            why: 'Security, compliance and vulnerability cockpit'
        },
        {title: 'V19.3 release notes', path: 'docs/V19_3_RELEASE_NOTES.md', why: 'DevSecOps module upgrade notes'},
        {
            title: 'V19.5 release notes',
            path: 'docs/V19_5_RELEASE_NOTES.md',
            why: 'Observability, GitOps, environments and Terraform Studio'
        },
        {
            title: 'V19.5 Observability',
            path: 'docs/V19_5_ADVANCED_OBSERVABILITY.md',
            why: 'Prometheus, Loki, Tempo, Grafana and OpenTelemetry'
        },
        {
            title: 'V19.5 GitOps Control Plane',
            path: 'docs/V19_5_GITOPS_CONTROL_PLANE.md',
            why: 'drift detection, ArgoCD live sync and rollback'
        },
        {
            title: 'V19.5 Multi-Environment Manager',
            path: 'docs/V19_5_MULTI_ENVIRONMENT_MANAGER.md',
            why: 'LOCAL, DEV, STAGING and PROD provisioning'
        },
        {
            title: 'V19.5 Smart Terraform Studio',
            path: 'docs/V19_5_SMART_TERRAFORM_STUDIO.md',
            why: 'digital twin graph, plan preview and cost estimation'
        },
        {
            title: 'V19.5 CI/CD Designer',
            path: 'docs/V19_5_CICD_PIPELINE_DESIGNER.md',
            why: 'drag-drop pipeline canvas and pipeline-engine-service'
        },
        {
            title: 'DevSecOps SVG',
            path: 'docs/nebulaops-v19-5-devsecops-module.svg',
            why: 'Radar, threat map and CVE dashboard architecture'
        }
    ];
    readonly workloads = computed(() => this.resources().filter(r => ['Deployment', 'StatefulSet', 'DaemonSet'].includes(r.kind)));
    readonly selectedNode = computed(() => {
        const selected = this.selected();
        if (!selected) return this.liveClusterNodes()[0] ?? null;
        return this.liveClusterNodes().find(n => n.id === selected.name || n.label.toLowerCase().includes(selected.name.toLowerCase())) ?? this.liveClusterNodes()[0] ?? null;
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
            localStorage.setItem('nebulaops.v19_5.user', u);
            this.currentUser.set(u);
            this.isAuthenticated.set(true);
            this.refreshAll();
        } else this.loginError.set('Credenziali demo: admin/admin oppure peyman/admin');
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

    simulatePipelineRun(): void {
        const order: PipelineDesignerStatus[] = ['success', 'success', 'running', 'queued', 'queued', 'blocked'];
        this.pipelineDesignerNodes.set(this.pipelineDesignerNodes().map((n, i) => ({
            ...n,
            status: order[i] ?? n.status
        })));
        this.selectedPipelineNode.set(this.pipelineDesignerNodes()[2] ?? null);
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

    simulateGitOpsSync(): void {
        const current = this.gitOpsState();
        this.gitOpsState.set({
            sync: current.sync === 'Synced' ? 'OutOfSync' : 'Synced',
            drift: current.sync === 'Synced' ? 3 : 0,
            revision: 'b' + Math.floor(Math.random() * 999999).toString(16),
            health: current.sync === 'Synced' ? 'Degraded' : 'Healthy'
        });
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
                this.logs.set(s.logs?.length ? s.logs : this.syntheticLogs());
                this.selected.set(this.resources()[0] ?? null);
                this.k8sState.set(s.cluster.status === 'Connected' ? 'connected' : 'error');
            } else {
                this.k8sState.set('local');
                this.logs.set(this.syntheticLogs());
            }
        });
    }

    syntheticDockerContainers(): DockerContainer[] {
        return [
            {
                id: 'c-front',
                name: 'nebulaops-frontend',
                image: 'nebulaops/frontend:v19.5',
                status: 'running',
                cpu: 12,
                memory: 148,
                ports: '4200:80',
                network: 'nebulaops-net',
                logs: ['nginx started', 'serving Angular shell', 'healthcheck OK']
            },
            {
                id: 'c-gateway',
                name: 'gateway-service',
                image: 'nebulaops/gateway-service:v19.5',
                status: 'running',
                cpu: 34,
                memory: 512,
                ports: '8080:8080',
                network: 'nebulaops-net',
                logs: ['Spring Boot started', 'Kubernetes client initialized', 'runtime proxy ready']
            },
            {
                id: 'c-mongo',
                name: 'mongodb',
                image: 'mongo:7',
                status: 'running',
                cpu: 18,
                memory: 768,
                ports: '27017:27017',
                network: 'nebulaops-net',
                logs: ['WiredTiger opened', 'checkpoint completed', 'connections: 8']
            },
            {
                id: 'c-redis',
                name: 'redis',
                image: 'redis:7-alpine',
                status: 'running',
                cpu: 5,
                memory: 96,
                ports: '6379:6379',
                network: 'nebulaops-net',
                logs: ['ready to accept connections', 'cache hit ratio 94%', 'AOF disabled for local demo']
            },
            {
                id: 'c-rabbit',
                name: 'rabbitmq',
                image: 'rabbitmq:3-management',
                status: 'running',
                cpu: 9,
                memory: 256,
                ports: '5672/15672',
                network: 'nebulaops-net',
                logs: ['management plugin enabled', 'queues mirrored', 'consumer gateway-service online']
            },
            {
                id: 'c-grafana',
                name: 'grafana',
                image: 'grafana/grafana:latest',
                status: 'running',
                cpu: 7,
                memory: 184,
                ports: '3000:3000',
                network: 'nebulaops-net',
                logs: ['provisioning dashboards', 'datasource prometheus OK', 'datasource loki OK']
            }
        ];
    }

    syntheticK8sControllers(): K8sController[] {
        return [
            {
                kind: 'Deployment',
                name: 'frontend',
                namespace: 'nebulaops',
                desired: 2,
                available: 2,
                strategy: 'RollingUpdate',
                age: '2h'
            },
            {
                kind: 'Deployment',
                name: 'gateway-service',
                namespace: 'nebulaops',
                desired: 2,
                available: 1,
                strategy: 'RollingUpdate',
                age: '2h'
            },
            {
                kind: 'StatefulSet',
                name: 'mongodb',
                namespace: 'nebulaops',
                desired: 1,
                available: 1,
                strategy: 'OrderedReady',
                age: '2h'
            },
            {
                kind: 'DaemonSet',
                name: 'otel-node-agent',
                namespace: 'observability',
                desired: 3,
                available: 3,
                strategy: 'OnDelete',
                age: '45m'
            },
            {
                kind: 'Ingress',
                name: 'nebulaops-ingress',
                namespace: 'nebulaops',
                desired: 1,
                available: 1,
                strategy: 'nginx',
                age: '2h'
            }
        ];
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
        if (action.target === 'Pod') this.simulatePodRestart();
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

Docker Desktop simulation:
${dockerSummary}

OpenLens simulation:
${k8sSummary}

Selected logs: ${selected?.name || 'none'}
${selectedLogs}`;
    }

    loadDocker(): void {
        forkJoin({containers: this.http.get<DockerContainer[]>('/api/runtime/docker/containers').pipe(catchError(() => of([])))}).subscribe(r => this.dockerContainers.set(r.containers.length ? r.containers : this.syntheticDockerContainers()));
    }

    loadHelm(): void {
        this.http.get<any[]>('/api/runtime/helm/releases?namespace=all').pipe(catchError(() => of([]))).subscribe(r => this.helmReleases.set(r));
    }

    refreshLogs(): void {
        this.http.get<ServiceLog[]>('/api/kubernetes/logs').pipe(catchError(() => of([]))).subscribe(rows => {
            this.logs.set(rows.length ? rows : this.syntheticLogs());
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

    simulatePodRestart(): void {
        const r = this.selected();
        const target = r?.name || 'gateway-service';
        const event: ClusterEvent = {
            time: new Date().toLocaleTimeString(),
            type: 'RESTART',
            target,
            message: `Pod restart animation triggered for ${target}; traffic beams rerouted while rollout recovers.`,
            severity: 'HIGH'
        };
        this.clusterEvents.set([event, ...this.clusterEvents()].slice(0, 8));
        this.liveClusterNodes.set(this.liveClusterNodes().map(n => n.id === target || n.id === 'gateway-service' ? {
            ...n,
            status: 'critical',
            cpu: Math.min(98, n.cpu + 15),
            ram: Math.min(98, n.ram + 10)
        } : n));
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
        }).pipe(catchError(() => of({status: 'demo-applied'}))).subscribe(() => {
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

    simulateSecurityScan(): void {
        const nextRisk = Math.max(41, Math.min(98, this.riskScore() + (Math.random() > 0.5 ? -4 : 3)));
        this.riskScore.set(nextRisk);
        this.securityScans.set(this.securityScans().map((s, i) => i === 0 ? {
            ...s,
            status: s.status === 'RUNNING' ? 'FAILED' : 'RUNNING',
            high: Math.max(1, s.high + (s.status === 'RUNNING' ? 1 : -1))
        } : s));
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
        return err?.error?.message || err?.message || 'API non disponibile: uso modalità demo locale';
    }

    private loadLocal<T>(key: string, fallback: T): T {
        try {
            return JSON.parse(localStorage.getItem(key) || 'null') || fallback;
        } catch {
            return fallback;
        }
    }
}
