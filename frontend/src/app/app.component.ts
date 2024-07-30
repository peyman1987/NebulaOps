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
    | 'TERRAFORM'
    | 'HELM'
    | 'OBSERVABILITY'
    | 'AI OPS'
    | 'CICD'
    | 'SECURITY'
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

interface SecurityFinding {
    id: string;
    area: string;
    severity: Priority;
    title: string;
    remediation: string;
    done: boolean;
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

const TASKS_KEY = 'nebulaops.v19_2.tasks';
const K8S_KEY = 'nebulaops.v19_2.k8s';
const SESSION_KEY = 'nebulaops.v19_2.session';

function yamlOf(kind: K8sKind, ns: string, name: string, replicas = 1): string {
    if (['Deployment', 'StatefulSet', 'DaemonSet'].includes(kind)) return `apiVersion: apps/v1\nkind: ${kind}\nmetadata:\n  name: ${name}\n  namespace: ${ns}\n  labels:\n    app.kubernetes.io/part-of: nebulaops-v19-2\nspec:\n  replicas: ${kind === 'DaemonSet' ? 0 : replicas}\n  selector:\n    matchLabels:\n      app: ${name}\n  template:\n    metadata:\n      labels:\n        app: ${name}\n    spec:\n      containers:\n        - name: ${name}\n          image: nginx:1.27-alpine\n          ports:\n            - containerPort: 80\n`;
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
    readonly tabs: MainTab[] = ['OVERVIEW', 'TASKS', 'KUBERNETES', 'TERRAFORM', 'HELM', 'OBSERVABILITY', 'AI OPS', 'CICD', 'SECURITY', 'FINOPS', 'BACKUPS', 'DOCS'];
    readonly columns: Status[] = ['TODO', 'IN_PROGRESS', 'REVIEW', 'DONE'];
    readonly kinds: K8sKind[] = ['Namespace', 'Deployment', 'StatefulSet', 'DaemonSet', 'Service', 'Ingress', 'ConfigMap', 'Secret', 'CronJob'];
    readonly priorities: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
    readonly activeTab = signal<MainTab>('OVERVIEW');
    readonly isAuthenticated = signal(localStorage.getItem(SESSION_KEY) === 'active');
    readonly currentUser = signal(localStorage.getItem('nebulaops.v19_2.user') || 'admin');
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
    readonly dockerContainers = signal<any[]>([]);
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
            path: 'docs/diagrams/nebulaops-v19-2-ai-ops-architecture.svg',
            why: 'AI Ops cockpit animated SVG'
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
            localStorage.setItem('nebulaops.v19_2.user', u);
            this.currentUser.set(u);
            this.isAuthenticated.set(true);
            this.refreshAll();
        } else this.loginError.set('Credenziali demo: admin/admin oppure peyman/admin');
    }

    logout(): void {
        localStorage.removeItem(SESSION_KEY);
        this.isAuthenticated.set(false);
    }

    setTab(tab: MainTab): void {
        this.activeTab.set(tab);
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

    loadDocker(): void {
        forkJoin({containers: this.http.get<any[]>('/api/runtime/docker/containers').pipe(catchError(() => of([])))}).subscribe(r => this.dockerContainers.set(r.containers));
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
            incidentId: 'AIOPS-19-2-CRASH-042',
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
