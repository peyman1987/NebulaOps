import {Component, computed, effect, inject, OnDestroy, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {CdkDragDrop, DragDropModule} from '@angular/cdk/drag-drop';
import {HttpClient} from '@angular/common/http';
import {catchError, forkJoin, of} from 'rxjs';

type Status = 'TODO' | 'IN_PROGRESS' | 'REVIEW' | 'DONE';
type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
type K8sKind =
    'Namespace'
    | 'Deployment'
    | 'ReplicaSet'
    | 'StatefulSet'
    | 'DaemonSet'
    | 'Service'
    | 'Ingress'
    | 'ConfigMap'
    | 'Secret'
    | 'CronJob';
type MainTab =
    'OVERVIEW'
    | 'TASKS'
    | 'DOCKER'
    | 'KUBERNETES'
    | 'HELM'
    | 'GRAFANA'
    | 'OBSERVABILITY'
    | 'CICD'
    | 'SECURITY'
    | 'INFRA';

interface Task {
    id: string;
    title: string;
    owner: string;
    priority: Priority;
    status: Status;
}

interface ApiTask {
    id: string;
    title: string;
    assigneeId?: string;
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
    cluster: { name: string; provider: string; version: string; status: string; };
    resources: K8sResource[];
    logs: ServiceLog[];
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

interface InfraItem {
    name: string;
    type: string;
    health: number;
    endpoint: string;
    purpose: string;
    controlUrl?: string;
    controlLabel?: string;
    internalTab?: MainTab;
    internalLabel?: string;
}

const STORAGE_KEY = 'nebulaops.v17.board.tasks';
const K8S_STORAGE_KEY = 'nebulaops.v17.k8s.resources';
const SECURITY_KEY = 'nebulaops.v17.security.findings';

const DEFAULT_TASKS: Task[] = [
    {
        id: 'V17-101',
        title: 'Implement policy-as-code guardrails',
        owner: 'DevSecOps',
        priority: 'CRITICAL',
        status: 'TODO'
    },
    {
        id: 'V17-102',
        title: 'Wire Grafana SLO dashboard for gateway latency',
        owner: 'SRE',
        priority: 'HIGH',
        status: 'IN_PROGRESS'
    },
    {
        id: 'V17-103',
        title: 'Add GitLab quality gates and Helm render validation',
        owner: 'Platform',
        priority: 'HIGH',
        status: 'REVIEW'
    },
    {
        id: 'V17-104',
        title: 'Document local WSL runbook and smoke tests',
        owner: 'DevOps',
        priority: 'MEDIUM',
        status: 'DONE'
    }
];

const DEFAULT_FINDINGS: SecurityFinding[] = [
    {
        id: 'SEC-001',
        area: 'Container',
        severity: 'HIGH',
        title: 'Images must use non-root runtime users',
        remediation: 'Keep runtime images slim and add USER where supported.',
        done: false
    },
    {
        id: 'SEC-002',
        area: 'Kubernetes',
        severity: 'CRITICAL',
        title: 'Secrets must not be committed with real values',
        remediation: 'Use secrets.example.yaml and local-only overrides.',
        done: true
    },
    {
        id: 'SEC-003',
        area: 'Gateway',
        severity: 'MEDIUM',
        title: 'Kube API access should be explicit and local only',
        remediation: 'Mount kubeconfig read-only and document scope.',
        done: false
    }
];

function mk(kind: K8sKind, namespace: string, name: string, replicas: number): K8sResource {
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

function yamlOf(kind: K8sKind, ns: string, name: string, replicas = 1): string {
    if (['Deployment', 'ReplicaSet', 'StatefulSet', 'DaemonSet'].includes(kind)) return `apiVersion: apps/v1\nkind: ${kind}\nmetadata:\n  name: ${name}\n  namespace: ${ns}\n  labels:\n    app.kubernetes.io/name: ${name}\n    app.kubernetes.io/part-of: nebulaops\nspec:\n  replicas: ${kind === 'DaemonSet' ? 0 : replicas}\n  selector:\n    matchLabels:\n      app: ${name}\n  template:\n    metadata:\n      labels:\n        app: ${name}\n        app.kubernetes.io/part-of: nebulaops\n    spec:\n      containers:\n        - name: ${name}\n          image: nginx:1.27-alpine\n          ports:\n            - containerPort: 80\n`;
    if (kind === 'Service') return `apiVersion: v1\nkind: Service\nmetadata:\n  name: ${name}\n  namespace: ${ns}\nspec:\n  type: ClusterIP\n  selector:\n    app: ${name}\n  ports:\n    - port: 80\n      targetPort: 80\n`;
    if (kind === 'Ingress') return `apiVersion: networking.k8s.io/v1\nkind: Ingress\nmetadata:\n  name: ${name}\n  namespace: ${ns}\nspec:\n  rules:\n    - host: nebulaops.local\n      http:\n        paths:\n          - path: /\n            pathType: Prefix\n            backend:\n              service:\n                name: frontend\n                port:\n                  number: 80\n`;
    if (kind === 'CronJob') return `apiVersion: batch/v1\nkind: CronJob\nmetadata:\n  name: ${name}\n  namespace: ${ns}\nspec:\n  schedule: \"*/15 * * * *\"\n  jobTemplate:\n    spec:\n      template:\n        spec:\n          restartPolicy: OnFailure\n          containers:\n            - name: ${name}\n              image: busybox:1.36\n              command: [\"sh\", \"-c\", \"date && echo nebulaops maintenance\"]\n`;
    return `apiVersion: v1\nkind: ${kind}\nmetadata:\n  name: ${name}\n  namespace: ${ns}\n`;
}

const DEFAULT_K8S: K8sResource[] = [
    mk('Namespace', 'nebulaops', 'nebulaops', 0), mk('Deployment', 'nebulaops', 'frontend', 2), mk('Deployment', 'nebulaops', 'gateway-service', 2), mk('StatefulSet', 'nebulaops', 'mongodb', 1), mk('Service', 'nebulaops', 'gateway-service', 0), mk('Ingress', 'nebulaops', 'nebulaops-ingress', 0), mk('ConfigMap', 'nebulaops', 'platform-config', 0), mk('CronJob', 'nebulaops', 'backup-simulator', 0)
];

@Component({
    selector: 'app-root',
    standalone: true,
    imports: [CommonModule, FormsModule, DragDropModule],
    templateUrl: './app.component.html',
    styleUrl: './app.component.css'
})
export class AppComponent implements OnInit, OnDestroy {
    readonly tabs: MainTab[] = ['OVERVIEW', 'TASKS', 'DOCKER', 'KUBERNETES', 'HELM', 'GRAFANA', 'OBSERVABILITY', 'CICD', 'SECURITY', 'INFRA'];
    readonly columns: Status[] = ['TODO', 'IN_PROGRESS', 'REVIEW', 'DONE'];
    readonly kinds: K8sKind[] = ['Namespace', 'Deployment', 'ReplicaSet', 'StatefulSet', 'DaemonSet', 'Service', 'Ingress', 'ConfigMap', 'Secret', 'CronJob'];
    readonly priorities: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
    readonly activeTab = signal<MainTab>('OVERVIEW');
    readonly tasks = signal<Task[]>(this.loadLocal<Task[]>(STORAGE_KEY, DEFAULT_TASKS));
    readonly resources = signal<K8sResource[]>(this.loadLocal<K8sResource[]>(K8S_STORAGE_KEY, DEFAULT_K8S));
    readonly findings = signal<SecurityFinding[]>(this.loadLocal<SecurityFinding[]>(SECURITY_KEY, DEFAULT_FINDINGS));
    readonly logs = signal<ServiceLog[]>([]);
    readonly dockerContainers = signal<any[]>([]);
    readonly dockerImages = signal<any[]>([]);
    readonly dockerStats = signal<any[]>([]);
    readonly helmReleases = signal<any[]>([]);
    readonly grafanaHealth = signal<any>({});
    readonly grafanaDashboards = signal<any[]>([]);
    readonly runtimeState = signal<'idle' | 'syncing' | 'connected' | 'error'>('idle');
    readonly isAuthenticated = signal(localStorage.getItem('nebulaops.v17.session') === 'active');
    readonly currentUser = signal(localStorage.getItem('nebulaops.v17.user') || 'admin');
    readonly loginError = signal('');
    loginForm = {username: 'admin', password: 'admin'};

    readonly syncState = signal<'local' | 'synced' | 'syncing' | 'error'>('local');
    readonly k8sState = signal<'connected' | 'syncing' | 'error'>('syncing');
    readonly apiError = signal<string>('');
    readonly activeNamespace = signal('all');
    readonly activeKind = signal('all');
    readonly activeLogService = signal('all');
    readonly logsAutoRefresh = signal(false);
    readonly logsRefreshSeconds = signal(5);
    readonly lastLogsRefresh = signal('never');
    readonly selected = signal<K8sResource | null>(this.resources()[0] ?? null);
    taskForm: Task = {id: '', title: '', owner: 'Platform', priority: 'MEDIUM', status: 'TODO'};
    k8sForm = {kind: 'Deployment' as K8sKind, namespace: 'nebulaops', name: '', replicas: 1};
    readonly pipeline: PipelineStage[] = [
        {name: 'lint', status: 'passed', duration: '18s', detail: 'YAML, Markdown, Angular type checks'},
        {name: 'unit-test', status: 'passed', duration: '42s', detail: 'Spring Boot + Go package tests'},
        {name: 'container-build', status: 'running', duration: '2m 14s', detail: 'Multi-service Docker build'},
        {name: 'helm-render', status: 'queued', duration: '-', detail: 'Template validation before local deploy'},
        {name: 'argocd-sync', status: 'blocked', duration: '-', detail: 'Manual gate for personal machine'}
    ];
    readonly infra: InfraItem[] = [
        {
            name: 'MongoDB',
            type: 'database',
            health: 98,
            endpoint: 'localhost:27017',
            purpose: 'Persistent service data',
            controlUrl: 'http://localhost:8088',
            controlLabel: 'Open Mongo Express',
            internalTab: 'DOCKER',
            internalLabel: 'Manage container'
        },
        {
            name: 'RabbitMQ',
            type: 'queue',
            health: 96,
            endpoint: 'localhost:15672',
            purpose: 'Task and notification events',
            controlUrl: 'http://localhost:15672',
            controlLabel: 'Open RabbitMQ UI',
            internalTab: 'DOCKER',
            internalLabel: 'Manage container'
        },
        {
            name: 'Redis',
            type: 'cache',
            health: 99,
            endpoint: 'localhost:6379',
            purpose: 'Low-latency cache',
            controlUrl: 'http://localhost:8089',
            controlLabel: 'Open Redis Commander',
            internalTab: 'DOCKER',
            internalLabel: 'Manage container'
        },
        {
            name: 'Prometheus',
            type: 'metrics',
            health: 93,
            endpoint: 'localhost:9090',
            purpose: 'Scraping and SLO data',
            controlUrl: 'http://localhost:9090',
            controlLabel: 'Open Prometheus',
            internalTab: 'OBSERVABILITY',
            internalLabel: 'View logs'
        },
        {
            name: 'Grafana',
            type: 'dashboard',
            health: 92,
            endpoint: 'localhost:3000',
            purpose: 'Operations dashboards',
            controlUrl: 'http://localhost:3000',
            controlLabel: 'Open Grafana',
            internalTab: 'GRAFANA',
            internalLabel: 'View dashboards'
        },
        {
            name: 'Kubernetes API',
            type: 'cluster',
            health: 94,
            endpoint: 'kubectl context',
            purpose: 'Pods, deployments, services and ingress',
            internalTab: 'KUBERNETES',
            internalLabel: 'Open K8s console'
        },
        {
            name: 'Helm',
            type: 'package manager',
            health: 91,
            endpoint: 'helm releases',
            purpose: 'Release lifecycle and uninstall actions',
            internalTab: 'HELM',
            internalLabel: 'Open Helm console'
        }
    ];
    readonly total = computed(() => this.tasks().length);
    readonly done = computed(() => this.tasks().filter(t => t.status === 'DONE').length);
    readonly critical = computed(() => this.tasks().filter(t => t.priority === 'CRITICAL').length);
    readonly namespaceOptions = computed(() => ['all', ...Array.from(new Set(this.resources().map(r => r.namespace)))]);
    readonly logServiceOptions = computed(() => ['all', ...Array.from(new Set(this.logs().map(l => l.service)))]);
    readonly workloads = computed(() => this.resources().filter(r => ['Deployment', 'ReplicaSet', 'StatefulSet', 'DaemonSet', 'CronJob'].includes(r.kind)));
    readonly services = computed(() => this.resources().filter(r => r.kind === 'Service').length);
    readonly ingress = computed(() => this.resources().filter(r => r.kind === 'Ingress').length);
    readonly visibleResources = computed(() => this.resources().filter(r => (this.activeNamespace() === 'all' || r.namespace === this.activeNamespace()) && (this.activeKind() === 'all' || r.kind === this.activeKind())));
    readonly visibleLogs = computed(() => this.logs().filter(l => this.activeLogService() === 'all' || l.service === this.activeLogService()).slice(-120).reverse());
    readonly openFindings = computed(() => this.findings().filter(f => !f.done).length);
    private readonly http = inject(HttpClient);
    private logsTimer: ReturnType<typeof setInterval> | null = null;

    constructor() {
        effect(() => localStorage.setItem(STORAGE_KEY, JSON.stringify(this.tasks())));
        effect(() => localStorage.setItem(K8S_STORAGE_KEY, JSON.stringify(this.resources())));
        effect(() => localStorage.setItem(SECURITY_KEY, JSON.stringify(this.findings())));
    }

    ngOnInit(): void {
        if (this.isAuthenticated()) {
            this.loadTasksFromApi();
            this.loadK8sFromApi();
        }
    }

    ngOnDestroy(): void {
        this.stopLogsAutoRefresh();
    }

    login(): void {
        const u = this.loginForm.username.trim();
        const p = this.loginForm.password.trim();
        if ((u === 'admin' || u === 'peyman') && p === 'admin') {
            localStorage.setItem('nebulaops.v17.session', 'active');
            localStorage.setItem('nebulaops.v17.user', u);
            this.currentUser.set(u);
            this.isAuthenticated.set(true);
            this.loginError.set('');
            this.loadTasksFromApi();
            this.loadK8sFromApi();
            return;
        }
        this.loginError.set('Credenziali non valide. Usa admin/admin per la demo locale.');
    }

    logout(): void {
        localStorage.removeItem('nebulaops.v17.session');
        this.isAuthenticated.set(false);
    }

    setTab(tab: MainTab): void {
        this.activeTab.set(tab);
        if (['KUBERNETES', 'OBSERVABILITY', 'OVERVIEW'].includes(tab)) this.loadK8sFromApi();
        if (tab === 'DOCKER') this.loadDocker();
        if (tab === 'HELM') this.loadHelm();
        if (tab === 'GRAFANA') this.loadGrafana();
        if (tab !== 'OBSERVABILITY') this.stopLogsAutoRefresh();
    }


    openInfra(item: InfraItem, event?: Event): void {
        event?.stopPropagation();
        if (item.controlUrl) window.open(item.controlUrl, '_blank', 'noopener,noreferrer');
        else if (item.internalTab) this.openInfraInternal(item, event);
    }

    openInfraInternal(item: InfraItem, event?: Event): void {
        event?.stopPropagation();
        if (!item.internalTab) return;
        this.setTab(item.internalTab);
    }

    openUrl(url: string): void {
        window.open(url, '_blank', 'noopener,noreferrer');
    }

    columnTasks(status: Status): Task[] {
        return this.tasks().filter(t => t.status === status);
    }

    priorityClass(priority: Priority): string {
        return priority.toLowerCase();
    }

    stageWidth(stage: PipelineStage): number {
        return stage.status === 'passed' ? 100 : stage.status === 'running' ? 62 : stage.status === 'queued' ? 26 : 8;
    }

    trackById(_: number, item: { id?: string; name?: string }): string {
        return item.id || item.name || String(_);
    }

    drop(event: CdkDragDrop<Task[]>, status: Status): void {
        const item = event.previousContainer.data[event.previousIndex];
        if (!item) return;
        const before = this.tasks();
        this.tasks.set(before.map(t => t.id === item.id ? {...t, status} : t));
        this.persistStatus(item.id, status, before);
    }

    createTask(): void {
        if (!this.taskForm.title.trim()) return;
        const local: Task = {...this.taskForm, id: this.taskForm.id || `LOCAL-${Date.now()}`};
        this.tasks.set([local, ...this.tasks()]);
        this.http.post<ApiTask>('/api/tasks', {
            organizationId: 'demo-org',
            projectId: 'portfolio-v17',
            title: local.title,
            priority: local.priority,
            assigneeId: local.owner,
            labels: ['portfolio', 'v17']
        }).pipe(catchError(() => of(null))).subscribe(t => {
            if (t) this.tasks.set(this.tasks().map(x => x.id === local.id ? {
                id: t.id,
                title: t.title,
                owner: t.assigneeId || local.owner,
                priority: t.priority,
                status: t.status
            } : x));
        });
        this.taskForm = {id: '', title: '', owner: 'Platform', priority: 'MEDIUM', status: 'TODO'};
    }

    deleteTask(id: string): void {
        const before = this.tasks();
        this.tasks.set(before.filter(t => t.id !== id));
        if (!id.startsWith('LOCAL') && !id.startsWith('V17-')) this.http.delete(`/api/tasks/${id}`).pipe(catchError(() => {
            this.tasks.set(before);
            return of(null);
        })).subscribe();
    }

    setNamespace(e: Event): void {
        this.activeNamespace.set((e.target as HTMLSelectElement).value);
    }

    setKind(e: Event): void {
        this.activeKind.set((e.target as HTMLSelectElement).value);
    }

    setLogService(e: Event): void {
        this.activeLogService.set((e.target as HTMLSelectElement).value);
    }


    loadDocker(): void {
        this.runtimeState.set('syncing');
        forkJoin({
            containers: this.http.get<any[]>('/api/runtime/docker/containers').pipe(catchError(err => {
                this.apiError.set(this.errorMessage(err));
                return of([]);
            })),
            images: this.http.get<any[]>('/api/runtime/docker/images').pipe(catchError(() => of([]))),
            stats: this.http.get<any[]>('/api/runtime/docker/stats').pipe(catchError(() => of([])))
        }).subscribe(r => {
            this.dockerContainers.set(r.containers);
            this.dockerImages.set(r.images);
            this.dockerStats.set(r.stats);
            this.runtimeState.set(r.containers.length ? 'connected' : 'error');
        });
    }

    dockerAction(container: any, action: string): void {
        const id = container.ID || container.Id || container.Names || container.Name;
        if (!id) return;
        this.runtimeState.set('syncing');
        this.http.post(`/api/runtime/docker/containers/${encodeURIComponent(id)}/${action}`, {}).pipe(catchError(err => {
            this.apiError.set(this.errorMessage(err));
            this.runtimeState.set('error');
            return of(null);
        })).subscribe(() => this.loadDocker());
    }

    loadHelm(): void {
        this.runtimeState.set('syncing');
        this.http.get<any[]>('/api/runtime/helm/releases?namespace=all').pipe(catchError(err => {
            this.apiError.set(this.errorMessage(err));
            this.runtimeState.set('error');
            return of([]);
        })).subscribe(rows => {
            this.helmReleases.set(rows);
            this.runtimeState.set('connected');
        });
    }

    uninstallHelm(release: any): void {
        const name = release.name || release.Name;
        const ns = release.namespace || release.Namespace || 'default';
        if (!name) return;
        this.http.post(`/api/runtime/helm/releases/${encodeURIComponent(name)}/uninstall?namespace=${encodeURIComponent(ns)}`, {}).pipe(catchError(err => {
            this.apiError.set(this.errorMessage(err));
            return of(null);
        })).subscribe(() => this.loadHelm());
    }

    loadGrafana(): void {
        this.runtimeState.set('syncing');
        forkJoin({
            health: this.http.get<any>('/api/runtime/grafana/health').pipe(catchError(err => {
                this.apiError.set(this.errorMessage(err));
                return of({status: 'unavailable'});
            })),
            dashboards: this.http.get<any[]>('/api/runtime/grafana/dashboards').pipe(catchError(() => of([])))
        }).subscribe(r => {
            this.grafanaHealth.set(r.health);
            this.grafanaDashboards.set(r.dashboards);
            this.runtimeState.set('connected');
        });
    }

    refreshLogs(): void {
        this.http.get<ServiceLog[]>('/api/kubernetes/logs').pipe(catchError(err => {
            this.apiError.set(this.errorMessage(err));
            return of([] as ServiceLog[]);
        })).subscribe(rows => {
            this.logs.set(rows.length ? rows : this.syntheticLogs());
            this.lastLogsRefresh.set(new Date().toLocaleTimeString());
        });
    }

    toggleLogsAutoRefresh(): void {
        this.logsAutoRefresh() ? this.stopLogsAutoRefresh() : this.startLogsAutoRefresh();
    }

    setLogsRefreshSeconds(e: Event): void {
        this.logsRefreshSeconds.set(Number((e.target as HTMLSelectElement).value));
        if (this.logsAutoRefresh()) this.startLogsAutoRefresh();
    }

    refreshObservability(): void {
        this.refreshLogs();
    }

    toggleObservabilityAutoRefresh(): void {
        this.toggleLogsAutoRefresh();
    }

    setObservabilityRefreshSeconds(e: Event): void {
        this.setLogsRefreshSeconds(e);
    }

    visibleObservability(): ServiceLog[] {
        return this.visibleLogs();
    }

    selectResource(r: K8sResource): void {
        this.k8sState.set('syncing');
        this.http.get<K8sResource>(`/api/kubernetes/resources/${encodeURIComponent(r.id)}`).pipe(catchError(err => {
            this.apiError.set(this.errorMessage(err));
            this.k8sState.set('error');
            return of(null);
        })).subscribe(remote => {
            const next = remote || r;
            this.selected.set(next);
            this.resources.set(this.resources().map(x => x.id === next.id ? next : x));
            this.k8sState.set(remote ? 'connected' : 'error');
        });
    }

    updateSelectedYaml(value: string): void {
        const r = this.selected();
        if (r) this.selected.set({...r, yaml: value});
    }

    applyYaml(): void {
        const r = this.selected();
        if (!r) return;
        this.k8sState.set('syncing');
        this.apiError.set('');
        this.http.post<K8sResource>('/api/kubernetes/yaml/apply', {yaml: r.yaml}).pipe(catchError(err => {
            this.apiError.set(this.errorMessage(err));
            this.k8sState.set('error');
            return of(null);
        })).subscribe(remote => {
            if (remote) {
                this.resources.set([remote, ...this.resources().filter(x => x.id !== remote.id)]);
                this.selected.set(remote);
                this.k8sState.set('connected');
                this.loadK8sFromApi();
            }
        });
    }

    createResource(): void {
        if (!this.k8sForm.name.trim()) return;
        const r = mk(this.k8sForm.kind, this.k8sForm.namespace, this.k8sForm.name, this.k8sForm.replicas);
        this.k8sState.set('syncing');
        this.http.post<K8sResource>('/api/kubernetes/resources', r).pipe(catchError(err => {
            this.apiError.set(this.errorMessage(err));
            this.k8sState.set('error');
            return of(null);
        })).subscribe(remote => {
            const next = remote || r;
            this.resources.set([next, ...this.resources().filter(x => x.id !== next.id)]);
            this.selected.set(next);
            this.k8sState.set(remote ? 'connected' : 'error');
            this.k8sForm = {kind: 'Deployment', namespace: 'nebulaops', name: '', replicas: 1};
        });
    }

    deleteResource(r: K8sResource): void {
        const before = this.resources();
        this.resources.set(before.filter(x => x.id !== r.id));
        this.http.delete(`/api/kubernetes/resources/${encodeURIComponent(r.id)}`).pipe(catchError(err => {
            this.apiError.set(this.errorMessage(err));
            return of(null);
        })).subscribe();
        if (this.selected()?.id === r.id) this.selected.set(this.resources()[0] ?? null);
    }

    scale(r: K8sResource, delta: number): void {
        const replicas = Math.max(0, r.replicas + delta);
        this.http.patch<K8sResource>(`/api/kubernetes/resources/${encodeURIComponent(r.id)}/scale`, {replicas}).pipe(catchError(err => {
            this.apiError.set(this.errorMessage(err));
            return of({...r, replicas, status: 'Local'} as K8sResource);
        })).subscribe(remote => {
            this.resources.set(this.resources().map(x => x.id === remote.id ? remote : x));
            if (this.selected()?.id === remote.id) this.selected.set(remote);
        });
    }

    toggleFinding(id: string): void {
        this.findings.set(this.findings().map(f => f.id === id ? {...f, done: !f.done} : f));
    }

    loadK8sFromApi(): void {
        this.k8sState.set('syncing');
        this.http.get<K8sSnapshot>('/api/kubernetes/snapshot').pipe(catchError(err => {
            this.apiError.set(this.errorMessage(err));
            this.k8sState.set('error');
            return of(null);
        })).subscribe(s => {
            if (s) {
                this.resources.set(s.resources.length ? s.resources as K8sResource[] : DEFAULT_K8S);
                this.logs.set(s.logs.length ? s.logs : this.syntheticLogs());
                this.selected.set(this.resources()[0] ?? null);
                this.k8sState.set(s.cluster.status === 'Connected' ? 'connected' : 'error');
            } else {
                this.logs.set(this.syntheticLogs());
            }
        });
    }

    private startLogsAutoRefresh(): void {
        this.stopLogsAutoRefresh(false);
        this.logsAutoRefresh.set(true);
        this.refreshLogs();
        this.logsTimer = setInterval(() => this.refreshLogs(), this.logsRefreshSeconds() * 1000);
    }

    private stopLogsAutoRefresh(updateState = true): void {
        if (this.logsTimer) clearInterval(this.logsTimer);
        this.logsTimer = null;
        if (updateState) this.logsAutoRefresh.set(false);
    }

    private loadTasksFromApi(): void {
        this.http.get<ApiTask[]>('/api/tasks?organizationId=demo-org').pipe(catchError(() => of([]))).subscribe(remote => {
            if (!remote.length) {
                this.seedDefaultsToApi();
                return;
            }
            this.tasks.set(remote.map(t => ({
                id: t.id,
                title: t.title,
                owner: t.assigneeId || 'Platform',
                priority: t.priority,
                status: t.status
            })));
            this.syncState.set('synced');
        });
    }

    private seedDefaultsToApi(): void {
        forkJoin(DEFAULT_TASKS.map(t => this.http.post<ApiTask>('/api/tasks', {
            organizationId: 'demo-org',
            projectId: 'portfolio-v17',
            title: t.title,
            description: `Seed task for ${t.owner}`,
            priority: t.priority,
            assigneeId: t.owner,
            labels: ['portfolio', 'v17']
        }).pipe(catchError(() => of(null))))).subscribe(results => {
            const created = results.filter((t): t is ApiTask => !!t);
            if (created.length) {
                this.tasks.set(created.map(t => ({
                    id: t.id,
                    title: t.title,
                    owner: t.assigneeId || 'Platform',
                    priority: t.priority,
                    status: t.status
                })));
                this.syncState.set('synced');
            }
        });
    }

    private persistStatus(taskId: string, status: Status, rollback: Task[]): void {
        this.syncState.set('syncing');
        if (!taskId.startsWith('V17-') && !taskId.startsWith('LOCAL')) this.http.patch<ApiTask>(`/api/tasks/${taskId}/status/${status}`, {}).pipe(catchError(() => of(null))).subscribe(result => {
            if (result) this.syncState.set('synced'); else {
                this.tasks.set(rollback);
                this.syncState.set('error');
            }
        }); else window.setTimeout(() => this.syncState.set('local'), 250);
    }

    private syntheticLogs(): ServiceLog[] {
        const now = new Date().toISOString();
        return ['gateway-service', 'task-service', 'go-cache-service', 'notification-service'].map((service, i) => ({
            time: now,
            service,
            level: i === 0 ? 'INFO' : 'DEBUG',
            message: `${service} heartbeat OK · local fallback log stream`
        }));
    }

    private errorMessage(err: any): string {
        return err?.error?.message || err?.error?.error || err?.message || 'API operation failed';
    }

    private loadLocal<T>(key: string, fallback: T): T {
        try {
            return JSON.parse(localStorage.getItem(key) || 'null') || fallback;
        } catch {
            return fallback;
        }
    }
}
