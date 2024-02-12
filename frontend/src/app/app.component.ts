import {Component, computed, effect, inject, OnDestroy, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {CdkDragDrop, DragDropModule} from '@angular/cdk/drag-drop';
import {HttpClient} from '@angular/common/http';
import {catchError, forkJoin, of} from 'rxjs';

type Status = 'TODO' | 'IN_PROGRESS' | 'REVIEW' | 'DONE';
type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
type K8sKind = 'Namespace' | 'Deployment' | 'ReplicaSet' | 'Service' | 'Ingress' | 'ConfigMap' | 'Secret';
type MainTab = 'TASK' | 'KUBERNETES';

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

const STORAGE_KEY = 'nebulaops.board.tasks.v3';
const K8S_STORAGE_KEY = 'nebulaops.k8s.resources.v1';
const DEFAULT_TASKS: Task[] = [
    {id: 'T-101', title: 'MongoDB aggregate workload dashboard', owner: 'Platform', priority: 'HIGH', status: 'TODO'},
    {id: 'T-102', title: 'RabbitMQ task event contract', owner: 'Backend', priority: 'CRITICAL', status: 'IN_PROGRESS'},
    {id: 'T-103', title: 'Angular CDK Kanban persistence', owner: 'Frontend', priority: 'HIGH', status: 'REVIEW'},
    {id: 'T-104', title: 'Gateway smoke tests', owner: 'DevOps', priority: 'MEDIUM', status: 'DONE'}
];
const DEFAULT_K8S: K8sResource[] = [
    mk('Namespace', 'nebulaops', 'nebulaops', 0), mk('Deployment', 'nebulaops', 'frontend', 2), mk('Deployment', 'nebulaops', 'gateway-service', 2), mk('Deployment', 'nebulaops', 'task-service', 2), mk('Service', 'nebulaops', 'frontend', 0), mk('Service', 'nebulaops', 'gateway-service', 0), mk('Ingress', 'nebulaops', 'nebulaops-ingress', 0), mk('ConfigMap', 'nebulaops', 'nebulaops-config', 0)
];

function mk(kind: K8sKind, namespace: string, name: string, replicas: number): K8sResource {
    return {
        id: `${kind.toLowerCase()}:${namespace}:${name}`,
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
    if (kind === 'Deployment' || kind === 'ReplicaSet') return `apiVersion: apps/v1\nkind: ${kind}\nmetadata:\n  name: ${name}\n  namespace: ${ns}\nspec:\n  replicas: ${replicas}\n  selector:\n    matchLabels:\n      app: ${name}\n  template:\n    metadata:\n      labels:\n        app: ${name}\n    spec:\n      containers:\n        - name: ${name}\n          image: nebulaops/${name}:latest\n          ports:\n            - containerPort: 8080\n`;
    if (kind === 'Service') return `apiVersion: v1\nkind: Service\nmetadata:\n  name: ${name}\n  namespace: ${ns}\nspec:\n  type: ClusterIP\n  selector:\n    app: ${name}\n  ports:\n    - port: 80\n      targetPort: 8080\n`;
    if (kind === 'Ingress') return `apiVersion: networking.k8s.io/v1\nkind: Ingress\nmetadata:\n  name: ${name}\n  namespace: ${ns}\nspec:\n  rules:\n    - host: nebulaops.local\n      http:\n        paths:\n          - path: /\n            pathType: Prefix\n            backend:\n              service:\n                name: frontend\n                port:\n                  number: 80\n`;
    return `apiVersion: v1\nkind: ${kind}\nmetadata:\n  name: ${name}\n  namespace: ${ns}\n`;
}

@Component({
    selector: 'app-root',
    standalone: true,
    imports: [CommonModule, FormsModule, DragDropModule],
    templateUrl: './app.component.html',
    styleUrl: './app.component.css'
})
export class AppComponent implements OnInit, OnDestroy {
    readonly columns: Status[] = ['TODO', 'IN_PROGRESS', 'REVIEW', 'DONE'];
    readonly activeTab = signal<MainTab>('TASK');
    readonly kinds: K8sKind[] = ['Namespace', 'Deployment', 'ReplicaSet', 'Service', 'Ingress', 'ConfigMap', 'Secret'];
    readonly priorities: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
    readonly tasks = signal<Task[]>(this.loadLocalTasks());
    readonly resources = signal<K8sResource[]>(this.loadLocalK8s());
    readonly logs = signal<ServiceLog[]>([]);
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
    total = computed(() => this.tasks().length);
    done = computed(() => this.tasks().filter(t => t.status === 'DONE').length);
    critical = computed(() => this.tasks().filter(t => t.priority === 'CRITICAL').length);
    namespaceOptions = computed(() => ['all', ...Array.from(new Set(this.resources().map(r => r.namespace)))]);
    logServiceOptions = computed(() => ['all', ...Array.from(new Set(this.logs().map(l => l.service)))]);
    deployments = computed(() => this.resources().filter(r => r.kind === 'Deployment' || r.kind === 'ReplicaSet'));
    services = computed(() => this.resources().filter(r => r.kind === 'Service').length);
    ingress = computed(() => this.resources().filter(r => r.kind === 'Ingress').length);
    visibleResources = computed(() => this.resources().filter(r => (this.activeNamespace() === 'all' || r.namespace === this.activeNamespace()) && (this.activeKind() === 'all' || r.kind === this.activeKind())));
    visibleLogs = computed(() => this.logs().filter(l => this.activeLogService() === 'all' || l.service === this.activeLogService()));
    private readonly http = inject(HttpClient);
    private logsTimer: ReturnType<typeof setInterval> | null = null;

    constructor() {
        effect(() => localStorage.setItem(STORAGE_KEY, JSON.stringify(this.tasks())));
        effect(() => localStorage.setItem(K8S_STORAGE_KEY, JSON.stringify(this.resources())));
    }

    ngOnInit(): void {
        this.loadTasksFromApi();
        this.loadK8sFromApi();
    }

    ngOnDestroy(): void {
        this.stopLogsAutoRefresh();
    }

    columnTasks(status: Status): Task[] {
        return this.tasks().filter(t => t.status === status);
    }

    setTab(tab: MainTab): void {
        this.activeTab.set(tab);
        if (tab === 'KUBERNETES') {
            this.loadK8sFromApi();
        } else {
            this.stopLogsAutoRefresh();
        }
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
            projectId: 'portfolio',
            title: local.title,
            priority: local.priority,
            assigneeId: local.owner,
            labels: ['portfolio']
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
        if (!id.startsWith('LOCAL') && !id.startsWith('T-')) this.http.delete(`/api/tasks/${id}`).pipe(catchError(() => of(null))).subscribe();
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

    refreshLogs(): void {
        this.http.get<ServiceLog[]>('/api/kubernetes/logs').pipe(catchError(err => {
            this.apiError.set(this.errorMessage(err));
            return of([] as ServiceLog[]);
        })).subscribe(rows => {
            this.logs.set(rows);
            this.lastLogsRefresh.set(new Date().toLocaleTimeString());
        });
    }

    toggleLogsAutoRefresh(): void {
        if (this.logsAutoRefresh()) {
            this.stopLogsAutoRefresh();
        } else {
            this.startLogsAutoRefresh();
        }
    }

    setLogsRefreshSeconds(e: Event): void {
        const value = Number((e.target as HTMLSelectElement).value);
        this.logsRefreshSeconds.set(value);
        if (this.logsAutoRefresh()) {
            this.startLogsAutoRefresh();
        }
    }

    selectResource(r: K8sResource): void {
        this.k8sState.set('syncing');
        this.http.get<K8sResource>(`/api/kubernetes/resources/${encodeURIComponent(r.id)}`).pipe(catchError(err => {
            this.apiError.set(this.errorMessage(err));
            this.k8sState.set('error');
            return of(null);
        })).subscribe(remote => {
            if (remote) {
                this.selected.set(remote);
                this.resources.set(this.resources().map(x => x.id === remote.id ? remote : x));
                this.k8sState.set('connected');
            }
        });
    }

    updateSelectedYaml(value: string): void {
        const r = this.selected();
        if (!r) return;
        this.selected.set({...r, yaml: value});
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
        this.apiError.set('');
        this.http.post<K8sResource>('/api/kubernetes/resources', r).pipe(catchError(err => {
            this.apiError.set(this.errorMessage(err));
            this.k8sState.set('error');
            return of(null);
        })).subscribe(remote => {
            if (remote) {
                this.resources.set([remote, ...this.resources().filter(x => x.id !== remote.id)]);
                this.selected.set(remote);
                this.k8sState.set('connected');
                this.loadK8sFromApi();
                this.k8sForm = {kind: 'Deployment', namespace: 'nebulaops', name: '', replicas: 1};
            }
        });
    }

    deleteResource(r: K8sResource): void {
        const before = this.resources();
        this.k8sState.set('syncing');
        this.apiError.set('');
        this.http.delete(`/api/kubernetes/resources/${encodeURIComponent(r.id)}`).pipe(catchError(err => {
            this.apiError.set(this.errorMessage(err));
            this.k8sState.set('error');
            return of(null);
        })).subscribe(result => {
            if (result) {
                this.resources.set(before.filter(x => x.id !== r.id));
                if (this.selected()?.id === r.id) this.selected.set(this.resources()[0] ?? null);
                this.k8sState.set('connected');
            }
        });
    }

    scale(r: K8sResource, delta: number): void {
        const replicas = Math.max(0, r.replicas + delta);
        this.k8sState.set('syncing');
        this.apiError.set('');
        this.http.patch<K8sResource>(`/api/kubernetes/resources/${encodeURIComponent(r.id)}/scale`, {replicas}).pipe(catchError(err => {
            this.apiError.set(this.errorMessage(err));
            this.k8sState.set('error');
            return of(null);
        })).subscribe(remote => {
            if (remote) {
                this.resources.set(this.resources().map(x => x.id === remote.id ? remote : x));
                if (this.selected()?.id === remote.id) this.selected.set(remote);
                this.k8sState.set('connected');
            }
        });
    }

    loadK8sFromApi(): void {
        this.k8sState.set('syncing');
        this.http.get<K8sSnapshot>('/api/kubernetes/snapshot').pipe(catchError(err => {
            this.apiError.set(this.errorMessage(err));
            this.k8sState.set('error');
            return of(null);
        })).subscribe(s => {
            if (s) {
                this.resources.set(s.resources);
                this.logs.set(s.logs);
                this.selected.set(s.resources[0] ?? null);
                this.k8sState.set(s.cluster.status === 'Connected' ? 'connected' : 'error');
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
        if (this.logsTimer) {
            clearInterval(this.logsTimer);
            this.logsTimer = null;
        }
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
            projectId: 'portfolio',
            title: t.title,
            description: `Seed task for ${t.owner}`,
            priority: t.priority,
            assigneeId: t.owner,
            labels: ['portfolio']
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
        if (!taskId.startsWith('T-') && !taskId.startsWith('LOCAL')) this.http.patch<ApiTask>(`/api/tasks/${taskId}/status/${status}`, {}).pipe(catchError(() => of(null))).subscribe(result => {
            if (result) this.syncState.set('synced'); else {
                this.tasks.set(rollback);
                this.syncState.set('error');
            }
        }); else window.setTimeout(() => this.syncState.set('local'), 250);
    }

    private errorMessage(err: any): string {
        return err?.error?.message || err?.error?.error || err?.message || 'API operation failed';
    }

    private loadLocalTasks(): Task[] {
        try {
            return JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null') || DEFAULT_TASKS;
        } catch {
            return DEFAULT_TASKS;
        }
    }

    private loadLocalK8s(): K8sResource[] {
        try {
            return JSON.parse(localStorage.getItem(K8S_STORAGE_KEY) || 'null') || DEFAULT_K8S;
        } catch {
            return DEFAULT_K8S;
        }
    }
}
