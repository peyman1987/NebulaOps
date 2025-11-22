import {CommonModule} from '@angular/common';
import {Component, CUSTOM_ELEMENTS_SCHEMA, OnInit, computed, inject, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {
  APP_RELEASE,
  APP_VERSION,
  JWT_KEY,
  USER_KEY,
  KC_AUTH_URL,
  KC_TOKEN_URL,
  KC_LOGOUT_URL,
  KC_CLIENT_ID,
  KC_REDIRECT_URI
} from './api.config';
import { REMOTES, SERVICE_LINKS, SIDEBAR_GROUPS, RemoteDefinition, RemoteId, ServiceLink, remoteSearchText } from './platform/catalog';



interface RemoteSummaryCard {
  label: string;
  value: string;
  detail: string;
}

interface DiagramNode {
  label: string;
  detail: string;
  icon: string;
  x: number;
  y: number;
  width: number;
  tone: 'primary' | 'normal' | 'warn' | 'success';
}

interface DiagramEdge {
  d: string;
  label: string;
}

interface DiagramStat {
  label: string;
  value: string;
}

interface RemoteDiagram {
  kicker: string;
  title: string;
  description: string;
  edges: DiagramEdge[];
  nodes: DiagramNode[];
  stats: DiagramStat[];
}

const DIAGRAMS: Record<RemoteId, RemoteDiagram> = {
  'docker-desktop': {
    kicker: 'DOCKER RUNTIME TOPOLOGY',
    title: 'Container lifecycle and image operations',
    description: 'This view follows the Docker path: shell navigation loads the remote entry, the Docker MFE calls the gateway, and runtime actions are executed against the Docker socket exposed to backend services.',
    edges: [
      { d: 'M70 110 C135 60 190 60 250 110', label: 'remote entry' },
      { d: 'M290 110 C350 55 430 60 500 110', label: 'gateway API' },
      { d: 'M290 140 C345 190 420 190 490 145', label: 'runtime socket' },
    ],
    nodes: [
      { icon: '🧭', label: 'Shell', detail: 'session + navigation', x: 34, y: 82, width: 96, tone: 'normal' },
      { icon: '🐳', label: 'Docker MFE', detail: 'containers/images', x: 210, y: 76, width: 132, tone: 'primary' },
      { icon: '🚪', label: 'Gateway', detail: '/api/docker', x: 426, y: 82, width: 104, tone: 'success' },
      { icon: '⚙️', label: 'Docker Engine', detail: 'socket actions', x: 392, y: 154, width: 138, tone: 'warn' },
    ],
    stats: [
      { label: 'Scope', value: 'containers · images · volumes' },
      { label: 'Backend path', value: 'gateway → runtime service' },
    ],
  },
  'infra-hub': {
    kicker: 'INFRA CONSOLE HUB',
    title: 'Central entry point for local platform consoles',
    description: 'INFRA Hub maps the operational endpoints that were available in the previous version and exposes them as a dedicated MFE: observability tools, data consoles, gateway checks and GitOps access points.',
    edges: [
      { d: 'M70 112 C150 52 230 62 285 108', label: 'console catalog' },
      { d: 'M285 108 C350 52 440 54 506 110', label: 'open console' },
      { d: 'M285 138 C350 185 425 184 500 148', label: 'health endpoints' },
    ],
    nodes: [
      { icon: '🏗️', label: 'INFRA Hub', detail: 'service catalog', x: 28, y: 82, width: 130, tone: 'primary' },
      { icon: '📊', label: 'Observe', detail: 'Grafana/Prometheus', x: 218, y: 74, width: 132, tone: 'success' },
      { icon: '🗄️', label: 'Data', detail: 'Mongo/Redis/RabbitMQ', x: 398, y: 74, width: 142, tone: 'normal' },
      { icon: '∞', label: 'GitOps', detail: 'ArgoCD/Gateway', x: 398, y: 150, width: 142, tone: 'warn' },
    ],
    stats: [
      { label: 'Console set', value: 'observability · data · GitOps' },
      { label: 'Access model', value: 'external links + health checks' },
    ],
  },
  'openlens-kubernetes': {
    kicker: 'KUBERNETES WORKLOAD MAP',
    title: 'Namespace, workload and Helm release navigation',
    description: 'The Kubernetes remote is centered on cluster state: workloads, services, config, events, nodes and Helm releases are grouped so operational issues can be inspected without leaving the shell.',
    edges: [
      { d: 'M76 112 C135 64 205 64 270 112', label: 'cluster query' },
      { d: 'M270 112 C340 55 420 60 505 112', label: 'workload state' },
      { d: 'M270 142 C335 190 420 190 500 148', label: 'events + helm' },
    ],
    nodes: [
      { icon: '☸️', label: 'Cluster', detail: 'namespaces', x: 34, y: 82, width: 104, tone: 'primary' },
      { icon: '⬡', label: 'Pods', detail: 'readiness/logs', x: 220, y: 76, width: 108, tone: 'success' },
      { icon: '🚀', label: 'Deployments', detail: 'scale/restart', x: 398, y: 76, width: 132, tone: 'normal' },
      { icon: '⛵', label: 'Helm', detail: 'release status', x: 410, y: 152, width: 104, tone: 'warn' },
    ],
    stats: [
      { label: 'K8s sections', value: 'pods · deployments · services' },
      { label: 'Ops actions', value: 'scale · restart · inspect' },
    ],
  },
  'task-management': {
    kicker: 'DELIVERY EVENT FLOW',
    title: 'Backlog ownership and notification loop',
    description: 'Task Management focuses on portfolio work items. Tasks move through ownership and priority states, while RabbitMQ-backed events keep notifications and downstream consumers aligned.',
    edges: [
      { d: 'M70 110 C145 52 215 60 282 110', label: 'task event' },
      { d: 'M282 110 C340 60 425 60 505 110', label: 'notification fanout' },
      { d: 'M282 140 C340 190 420 188 500 148', label: 'audit trail' },
    ],
    nodes: [
      { icon: '✅', label: 'Backlog', detail: 'task CRUD', x: 32, y: 82, width: 110, tone: 'primary' },
      { icon: '👤', label: 'Owner', detail: 'assignment', x: 222, y: 76, width: 104, tone: 'normal' },
      { icon: '✉️', label: 'RabbitMQ', detail: 'events', x: 410, y: 78, width: 110, tone: 'warn' },
      { icon: '🔔', label: 'Notify', detail: 'subscribers', x: 410, y: 152, width: 110, tone: 'success' },
    ],
    stats: [
      { label: 'Domain', value: 'tasks · priorities · owners' },
      { label: 'Async path', value: 'RabbitMQ events' },
    ],
  },
  'observability': {
    kicker: 'OBSERVABILITY SIGNAL GRAPH',
    title: 'Metrics, logs and traces correlation',
    description: 'The observability remote separates each telemetry signal while keeping the investigation path clear: metrics from Prometheus, logs from Loki, traces from Tempo and dashboards in Grafana.',
    edges: [
      { d: 'M74 112 C135 54 210 54 276 112', label: 'metrics' },
      { d: 'M276 112 C342 54 430 56 505 112', label: 'dashboards' },
      { d: 'M276 142 C342 188 420 188 502 148', label: 'traces/logs' },
    ],
    nodes: [
      { icon: '📈', label: 'Prometheus', detail: 'metrics', x: 24, y: 82, width: 126, tone: 'primary' },
      { icon: '≋', label: 'Loki', detail: 'logs', x: 230, y: 76, width: 92, tone: 'normal' },
      { icon: '⌁', label: 'Tempo', detail: 'traces', x: 404, y: 76, width: 108, tone: 'warn' },
      { icon: '🌀', label: 'Grafana', detail: 'dashboards', x: 396, y: 152, width: 124, tone: 'success' },
    ],
    stats: [
      { label: 'Signals', value: 'metrics · logs · traces' },
      { label: 'Dashboard', value: 'Grafana overview' },
    ],
  },
  'cicd-gitops': {
    kicker: 'DELIVERY PIPELINE GRAPH',
    title: 'Build, scan, package and GitOps promotion',
    description: 'The CI/CD remote visualizes deployment promotion. It links pipeline status, container packaging, Helm delivery and ArgoCD synchronization into one operational flow.',
    edges: [
      { d: 'M70 112 C140 62 210 62 280 112', label: 'pipeline' },
      { d: 'M280 112 C350 60 425 60 505 112', label: 'sync' },
      { d: 'M280 142 C345 190 420 190 500 148', label: 'promotion gate' },
    ],
    nodes: [
      { icon: '🧪', label: 'Build/Test', detail: 'CI stages', x: 28, y: 82, width: 124, tone: 'primary' },
      { icon: '📦', label: 'Image', detail: 'package', x: 230, y: 76, width: 104, tone: 'normal' },
      { icon: '⛵', label: 'Helm', detail: 'release', x: 410, y: 76, width: 100, tone: 'warn' },
      { icon: '∞', label: 'ArgoCD', detail: 'sync/rollback', x: 398, y: 152, width: 124, tone: 'success' },
    ],
    stats: [
      { label: 'Delivery model', value: 'CI pipeline + GitOps sync' },
      { label: 'Promotion', value: 'Helm release gates' },
    ],
  },
  'terraform-studio': {
    kicker: 'IAC PLAN TO APPLY GRAPH',
    title: 'Terraform module, workspace and drift path',
    description: 'Terraform Studio is modeled around infrastructure state: reusable modules, environment workspaces, plan output, apply history and drift checks.',
    edges: [
      { d: 'M70 112 C138 54 214 58 280 112', label: 'plan' },
      { d: 'M280 112 C350 58 430 60 505 112', label: 'apply' },
      { d: 'M280 142 C340 188 420 188 500 148', label: 'drift' },
    ],
    nodes: [
      { icon: '🧱', label: 'Module', detail: 'source/version', x: 34, y: 82, width: 108, tone: 'primary' },
      { icon: '🌐', label: 'Workspace', detail: 'env state', x: 220, y: 76, width: 126, tone: 'normal' },
      { icon: '📋', label: 'Plan', detail: 'diff preview', x: 410, y: 76, width: 104, tone: 'warn' },
      { icon: '✅', label: 'Apply', detail: 'run history', x: 410, y: 152, width: 104, tone: 'success' },
    ],
    stats: [
      { label: 'IaC scope', value: 'modules · workspaces · state' },
      { label: 'Controls', value: 'plan · validate · apply' },
    ],
  },
  'devsecops': {
    kicker: 'SECURITY SCAN GRAPH',
    title: 'Vulnerability, secret and policy workflow',
    description: 'The DevSecOps remote is specific to security evidence. Findings are grouped by package vulnerability, leaked secret posture, remediation action and policy status.',
    edges: [
      { d: 'M72 112 C140 55 210 56 280 112', label: 'scan' },
      { d: 'M280 112 C350 54 430 58 505 112', label: 'triage' },
      { d: 'M280 142 C344 188 420 188 500 148', label: 'remediate' },
    ],
    nodes: [
      { icon: '🛡️', label: 'Scanner', detail: 'Trivy/SAST', x: 28, y: 82, width: 118, tone: 'primary' },
      { icon: '🔑', label: 'Secrets', detail: 'leak posture', x: 228, y: 76, width: 112, tone: 'warn' },
      { icon: '🐞', label: 'CVEs', detail: 'packages', x: 412, y: 76, width: 100, tone: 'normal' },
      { icon: '🔧', label: 'Fix', detail: 'remediation', x: 410, y: 152, width: 104, tone: 'success' },
    ],
    stats: [
      { label: 'Coverage', value: 'vulnerabilities · secrets' },
      { label: 'Output', value: 'policy and fix actions' },
    ],
  },
  'ai-ops': {
    kicker: 'AI OPERATIONS LOOP',
    title: 'Incident, RCA and assisted remediation',
    description: 'AI Ops turns telemetry and incidents into an RCA loop. The remote asks the AI engine for analysis and returns suggested remediation steps for platform operators.',
    edges: [
      { d: 'M70 112 C140 60 210 60 280 112', label: 'signals' },
      { d: 'M280 112 C350 56 430 58 505 112', label: 'RCA' },
      { d: 'M280 142 C345 190 420 190 500 148', label: 'autofix hint' },
    ],
    nodes: [
      { icon: '🚨', label: 'Incident', detail: 'active event', x: 34, y: 82, width: 112, tone: 'warn' },
      { icon: '🤖', label: 'AI Engine', detail: 'analysis', x: 222, y: 76, width: 120, tone: 'primary' },
      { icon: '🔎', label: 'RCA', detail: 'root cause', x: 414, y: 76, width: 94, tone: 'normal' },
      { icon: '🛠️', label: 'Action', detail: 'fix proposal', x: 402, y: 152, width: 118, tone: 'success' },
    ],
    stats: [
      { label: 'AI path', value: 'incident → RCA → action' },
      { label: 'Engine', value: 'ai-engine service' },
    ],
  },
  'finops-cost': {
    kicker: 'FINOPS COST MODEL',
    title: 'Cost allocation and rightsizing workflow',
    description: 'FinOps Cost visualizes spend by namespace and service. Forecast, waste, rightsizing and monthly trend data are kept in one service-specific remote.',
    edges: [
      { d: 'M70 112 C140 55 210 58 280 112', label: 'usage input' },
      { d: 'M280 112 C350 58 430 58 505 112', label: 'cost model' },
      { d: 'M280 142 C345 190 420 190 500 148', label: 'savings' },
    ],
    nodes: [
      { icon: '💸', label: 'Spend', detail: 'namespace cost', x: 34, y: 82, width: 108, tone: 'primary' },
      { icon: '📊', label: 'Forecast', detail: 'month end', x: 220, y: 76, width: 118, tone: 'normal' },
      { icon: '📦', label: 'Resources', detail: 'CPU/memory', x: 398, y: 76, width: 126, tone: 'warn' },
      { icon: '✅', label: 'Rightsize', detail: 'potential saving', x: 398, y: 152, width: 126, tone: 'success' },
    ],
    stats: [
      { label: 'Cost view', value: 'namespace · service · resource' },
      { label: 'Optimization', value: 'forecast + rightsizing' },
    ],
  },
};

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {
  private readonly http = inject(HttpClient);

  readonly version = APP_VERSION;
  readonly release = APP_RELEASE;
  readonly loginError = signal('');
  readonly authenticated = signal(Boolean(localStorage.getItem(JWT_KEY)));
  readonly currentUser = signal(localStorage.getItem(USER_KEY) || 'peyman@nebulaops.local');
  readonly activeRemote = signal<RemoteId>('docker-desktop');
  readonly loadingRemote = signal(false);
  readonly remoteError = signal('');
  readonly appLauncherOpen = signal(false);
  readonly sessionPanelOpen = signal(false);
  readonly search = signal('');
  readonly remoteSearch = signal('');
  readonly loadedRemotes = new Set<RemoteId>();

  readonly remotes: RemoteDefinition[] = REMOTES;

  readonly serviceLinks: ServiceLink[] = SERVICE_LINKS;

  readonly activeRemoteConfig = computed(() => this.remotes.find(r => r.id === this.activeRemote()) ?? this.remotes[0]);

  readonly activeRemoteControlCards = computed<RemoteSummaryCard[]>(() => {
    const remote = this.activeRemoteConfig();
    return [
      {
        label: 'Remote entry',
        value: `:${remote.port}/remoteEntry.js`,
        detail: 'Angular Element bundle loaded by the shell'
      },
      {
        label: 'Web component',
        value: `<${remote.tag}>`,
        detail: 'isolated UI contract with independent release lifecycle'
      },
      {
        label: 'Gateway API',
        value: `/api/${remote.id}`,
        detail: 'REST proxy secured with the Keycloak Bearer token'
      },
      {
        label: 'Container',
        value: remote.service,
        detail: remote.status
      }
    ];
  });

  readonly activeRemoteRunbook = computed(() => {
    const remote = this.activeRemoteConfig();
    return [
      `Start the ${remote.service} container and verify port ${remote.port}.`,
      `Check that ${remote.path} returns a valid JavaScript bundle.`,
      'Validate the Keycloak token and Authorization header through the gateway.',
      'Use “Open standalone” to separate shell loading issues from remote application issues.'
    ];
  });

  readonly activeRemoteDiagram = computed(() => DIAGRAMS[this.activeRemote()]);

  /** Sidebar nav groups — catalog driven, with the INFRA section restored. */
  readonly sidebarGroups = SIDEBAR_GROUPS;

  remotesByGroup(groups: readonly string[]): RemoteDefinition[] {
    const term = this.remoteSearch().trim().toLowerCase();
    return this.remotes.filter(r => groups.includes(r.group) && (!term || remoteSearchText(r).includes(term)));
  }

  readonly filteredRemoteCount = computed(() =>
    this.remotes.filter(r => !this.remoteSearch().trim() || remoteSearchText(r).includes(this.remoteSearch().trim().toLowerCase())).length
  );
  readonly filteredLinks = computed(() => {
    const term = this.search().trim().toLowerCase();
    if (!term) return this.serviceLinks;
    return this.serviceLinks.filter(item =>
      [item.title, item.subtitle, item.group, item.port].join(' ').toLowerCase().includes(term)
    );
  });

  ngOnInit(): void {
    this.handleKeycloakCallback();
    if (this.authenticated()) {
      void this.activateRemote(this.activeRemote());
    }
  }

  async login(): Promise<void> {
    this.loginError.set('');
    try {
      const verifier = this.randomString(96);
      const challenge = await this.sha256Base64Url(verifier);
      sessionStorage.setItem('nebulaops.v22_2.pkce', verifier);
      const state = this.randomString(24);
      sessionStorage.setItem('nebulaops.v22_2.state', state);
      const params = new URLSearchParams({
        client_id: KC_CLIENT_ID,
        redirect_uri: KC_REDIRECT_URI,
        response_type: 'code',
        scope: 'openid profile email',
        state,
        code_challenge: challenge,
        code_challenge_method: 'S256'
      });
      window.location.href = `${KC_AUTH_URL}?${params.toString()}`;
    } catch (error) {
      this.loginError.set('Unable to start Keycloak login. Verify that Keycloak is running on localhost:8180.');
      console.error(error);
    }
  }

  logout(): void {
    localStorage.removeItem(JWT_KEY);
    localStorage.removeItem(USER_KEY);
    sessionStorage.removeItem('nebulaops.v22_2.pkce');
    sessionStorage.removeItem('nebulaops.v22_2.state');
    this.authenticated.set(false);
    const params = new URLSearchParams({
      client_id: KC_CLIENT_ID,
      post_logout_redirect_uri: KC_REDIRECT_URI
    });
    window.location.href = `${KC_LOGOUT_URL}?${params.toString()}`;
  }

  openLauncher(): void { this.appLauncherOpen.set(true); }
  closeLauncher(): void { this.appLauncherOpen.set(false); }
  toggleSessionPanel(): void { this.sessionPanelOpen.update(v => !v); }

  openService(link: ServiceLink): void {
    window.open(link.url, '_blank', 'noopener,noreferrer');
  }

  async activateRemote(id: RemoteId): Promise<void> {
    const def = this.remotes.find(r => r.id === id);
    if (!def) return;
    this.remoteError.set('');
    this.loadingRemote.set(true);
    try {
      await this.loadRemote(def);
      this.activeRemote.set(id);
    } catch (error) {
      this.remoteError.set(`Remote ${def.title} is not available on localhost:${def.port}. Start docker compose and verify the dedicated container ${def.service}.`);
      console.error(error);
    } finally {
      this.loadingRemote.set(false);
    }
  }

  trackByRemote(_: number, item: RemoteDefinition): string { return item.id; }
  trackByLink(_: number, item: ServiceLink): string { return item.title; }

  private async handleKeycloakCallback(): Promise<void> {
    const url = new URL(window.location.href);
    const code = url.searchParams.get('code');
    const state = url.searchParams.get('state');
    if (!code) return;
    const expectedState = sessionStorage.getItem('nebulaops.v22_2.state');
    if (expectedState && state !== expectedState) {
      this.loginError.set('Invalid Keycloak response: state mismatch.');
      return;
    }
    const verifier = sessionStorage.getItem('nebulaops.v22_2.pkce') || '';
    const body = new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: KC_CLIENT_ID,
      redirect_uri: KC_REDIRECT_URI,
      code,
      code_verifier: verifier
    });
    this.http.post<any>(KC_TOKEN_URL, body.toString(), {
      headers: new HttpHeaders({'Content-Type': 'application/x-www-form-urlencoded'})
    }).subscribe({
      next: token => {
        localStorage.setItem(JWT_KEY, token.access_token || '');
        const decoded = this.decodeJwt(token.access_token || '');
        const user = decoded?.preferred_username || decoded?.email || decoded?.name || 'nebulaops-user';
        localStorage.setItem(USER_KEY, user);
        this.currentUser.set(user);
        this.authenticated.set(true);
        history.replaceState({}, document.title, KC_REDIRECT_URI);
        void this.activateRemote(this.activeRemote());
      },
      error: err => {
        this.loginError.set('Keycloak login was received, but the token exchange failed. Reimport the realm and verify the nebulaops-frontend client.');
        console.error(err);
      }
    });
  }

  private loadRemote(def: RemoteDefinition): Promise<void> {
    if (customElements.get(def.tag) || this.loadedRemotes.has(def.id)) {
      return Promise.resolve();
    }
    return new Promise((resolve, reject) => {
      const existing = document.querySelector<HTMLScriptElement>(`script[data-remote-id="${def.id}"]`);
      if (existing) {
        existing.addEventListener('load', () => resolve());
        existing.addEventListener('error', () => reject(new Error(`Cannot load ${def.path}`)));
        return;
      }
      const script = document.createElement('script');
      script.src = def.path;
      script.defer = true;
      script.dataset['remoteId'] = def.id;
      script.onload = () => {
        this.loadedRemotes.add(def.id);
        resolve();
      };
      script.onerror = () => reject(new Error(`Cannot load ${def.path}`));
      document.body.appendChild(script);
    });
  }

  private randomString(length: number): string {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
    const values = new Uint8Array(length);
    crypto.getRandomValues(values);
    return Array.from(values, value => chars[value % chars.length]).join('');
  }

  private async sha256Base64Url(value: string): Promise<string> {
    const data = new TextEncoder().encode(value);
    const hash = await crypto.subtle.digest('SHA-256', data);
    return btoa(String.fromCharCode(...new Uint8Array(hash)))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/g, '');
  }

  private decodeJwt(token: string): any {
    try {
      const payload = token.split('.')[1];
      if (!payload) return null;
      return JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
    } catch {
      return null;
    }
  }
}
