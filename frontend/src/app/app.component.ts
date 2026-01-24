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
  KC_REDIRECT_URI,
  PUBLIC_ORIGIN
} from './api.config';

const REMOTE_ENTRY_BUILD = 'v22.4.10-localhost-final';

type RemoteId = 'docker-desktop' | 'openlens-kubernetes' | 'task-management' | 'observability' | 'cicd-gitops' | 'terraform-studio' | 'devsecops' | 'ai-ops' | 'finops-cost' | 'infra-hub' | 'release-center' | 'policy-center' | 'progressive-delivery' | 'notification-center' | 'identity-admin';
type ServiceGroup = 'Identity' | 'Runtime' | 'Observability' | 'Data' | 'DevOps' | 'Micro Frontend' | 'Release' | 'Governance' | 'Notifications';

interface RemoteDefinition {
  id: RemoteId;
  tag: string;
  title: string;
  subtitle: string;
  icon: string;
  route: string;
  path: string;
  color: string;
  scope: string;
  group: string;
  service: string;
  status: string;
}

interface ServiceLink {
  title: string;
  subtitle: string;
  url: string;
  icon: string;
  port: string;
  group: ServiceGroup;
}

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
  readonly shellStatus = signal<'checking' | 'online' | 'degraded'>('checking');
  readonly shellStatusMessage = signal('Checking gateway and live integrations...');
  readonly quickHealth = signal<Record<string, string>>({
    gateway: 'CHECKING',
    releases: 'CHECKING',
    policies: 'CHECKING',
    notifications: 'CHECKING',
  });
  readonly commandPaletteOpen = signal(false);
  readonly sessionPanelOpen = signal(false);
  readonly search = signal('');
  readonly loadedRemotes = new Set<RemoteId>();

  readonly remotes: RemoteDefinition[] = [
    {
      id: 'docker-desktop',
      tag: 'nebulaops-mfe-docker-desktop',
      title: 'Docker Desktop',
      subtitle: 'Docker container, image, volume, network and runtime actions through the gateway.',
      icon: '🐳',
      route: '/remotes/docker-desktop/',
      color: 'cyan',
      scope: 'Runtime · Docker',
      group: 'Runtime',
      service: 'mfe-docker-desktop',
      path: '/remotes/docker-desktop/remoteEntry.js',
      status: 'remote app · independently deployable'
    },
    {
      id: 'openlens-kubernetes',
      tag: 'nebulaops-mfe-openlens-kubernetes',
      title: 'OpenLens Kubernetes',
      subtitle: 'Cluster, namespace, workload, service, event and Helm release view.',
      icon: '☸️',
      route: '/remotes/openlens-kubernetes/',
      color: 'blue',
      scope: 'Runtime · Kubernetes',
      group: 'Runtime',
      service: 'mfe-openlens-kubernetes',
      path: '/remotes/openlens-kubernetes/remoteEntry.js',
      status: 'remote app · independently deployable'
    },
    {
      id: 'task-management',
      tag: 'nebulaops-mfe-task-management',
      title: 'Task Management',
      subtitle: 'Backlog, priority, ownership, RabbitMQ events and notifications.',
      icon: '✅',
      route: '/remotes/task-management/',
      color: 'violet',
      scope: 'Delivery · Tasks',
      group: 'Delivery',
      service: 'mfe-task-management',
      path: '/remotes/task-management/remoteEntry.js',
      status: 'remote app · independently deployable'
    },
    {
      id: 'observability',
      tag: 'nebulaops-mfe-observability',
      title: 'Observability & Audit Center',
      subtitle: 'Live service health, Prometheus, Loki, Tempo, RabbitMQ, task events and audit trail.',
      icon: '📈',
      route: '/remotes/observability/',
      color: 'green',
      scope: 'SRE · Metrics/Logs/Traces/Audit',
      group: 'SRE',
      service: 'mfe-observability',
      path: '/remotes/observability/remoteEntry.js',
      status: 'remote app · independently deployable'
    },
    {
      id: 'cicd-gitops',
      tag: 'nebulaops-mfe-cicd-gitops',
      title: 'CI/CD + GitOps',
      subtitle: 'Pipeline, optional GitLab, Helm, ArgoCD and promotion flow.',
      icon: '🚀',
      route: '/remotes/cicd-gitops/',
      color: 'orange',
      scope: 'DevOps · Pipelines/GitOps',
      group: 'DevOps',
      service: 'mfe-cicd-gitops',
      path: '/remotes/cicd-gitops/remoteEntry.js',
      status: 'remote app · independently deployable'
    },
    {
      id: 'terraform-studio',
      tag: 'nebulaops-mfe-terraform-studio',
      title: 'Terraform Studio',
      subtitle: 'Local plan, validate and apply workflows for environments and Terraform modules.',
      icon: '🧱',
      route: '/remotes/terraform-studio/',
      color: 'purple',
      scope: 'IaC · Environments',
      group: 'IaC',
      service: 'mfe-terraform-studio',
      path: '/remotes/terraform-studio/remoteEntry.js',
      status: 'remote app · independently deployable'
    },
    {
      id: 'devsecops',
      tag: 'nebulaops-mfe-devsecops',
      title: 'DevSecOps',
      subtitle: 'Vulnerabilities, secret posture, Trivy, policy and hardening.',
      icon: '🛡️',
      route: '/remotes/devsecops/',
      color: 'red',
      scope: 'Security · Secrets/Vulns',
      group: 'Security',
      service: 'mfe-devsecops',
      path: '/remotes/devsecops/remoteEntry.js',
      status: 'remote app · independently deployable'
    },
    {
      id: 'ai-ops',
      tag: 'nebulaops-mfe-ai-ops',
      title: 'AI Ops',
      subtitle: 'RCA, operational insights, anomaly hints and platform assistant.',
      icon: '🤖',
      route: '/remotes/ai-ops/',
      color: 'pink',
      scope: 'AI · RCA/Assist',
      group: 'AI',
      service: 'mfe-ai-ops',
      path: '/remotes/ai-ops/remoteEntry.js',
      status: 'remote app · independently deployable'
    },
    {
      id: 'finops-cost',
      tag: 'nebulaops-mfe-finops-cost',
      title: 'FinOps Cost',
      subtitle: 'Cost analytics, resource spend, forecast and optimization.',
      icon: '💸',
      route: '/remotes/finops-cost/',
      color: 'amber',
      scope: 'Cost · Analytics',
      group: 'FinOps',
      service: 'mfe-finops-cost',
      path: '/remotes/finops-cost/remoteEntry.js',
      status: 'remote app · independently deployable'
    },
    {
      id: 'infra-hub',
      tag: 'nebulaops-mfe-infra-hub',
      title: 'INFRA Hub',
      subtitle: 'Infrastructure console for tool UIs, gateway, data plane, SSO and platform runtime.',
      icon: '🛰️',
      route: '/remotes/infra-hub/',
      color: 'cyan',
      scope: 'Infrastructure · Hub',
      group: 'Runtime',
      service: 'mfe-infra-hub',
      path: '/remotes/infra-hub/remoteEntry.js',
      status: 'remote app · independently deployable'
    },
    {
      id: 'release-center',
      tag: 'nebulaops-mfe-release-center',
      title: 'Release Center',
      subtitle: 'Lifecycle release: build, image, scan, policy gate, GitOps sync, rollout and rollback.',
      icon: '🚢',
      route: '/remotes/release-center/',
      color: 'orange',
      scope: 'Release · Promotion/Rollback',
      group: 'Release',
      service: 'mfe-release-center',
      path: '/remotes/release-center/remoteEntry.js',
      status: 'remote app · independently deployable'
    },
    {
      id: 'progressive-delivery',
      tag: 'nebulaops-mfe-progressive-delivery',
      title: 'Progressive Delivery Center',
      subtitle: 'Argo Rollouts, canary and blue-green runtime actions from live Kubernetes and Argo CD data.',
      icon: '🚢',
      route: '/remotes/progressive-delivery/',
      color: 'orange',
      scope: 'Release · Argo Rollouts',
      group: 'Release',
      service: 'mfe-progressive-delivery',
      path: '/remotes/progressive-delivery/remoteEntry.js',
      status: 'remote app · independently deployable'
    },
    {
      id: 'policy-center',
      tag: 'nebulaops-mfe-policy-center',
      title: 'Policy, Approval & Governance Center',
      subtitle: 'OPA policy engine, approval workflow, governance decisions, violations and audit-linked notifications from real runtime records.',
      icon: '⚖️',
      route: '/remotes/policy-center/',
      color: 'purple',
      scope: 'Governance · Approval · OPA',
      group: 'Governance',
      service: 'mfe-policy-center',
      path: '/remotes/policy-center/remoteEntry.js',
      status: 'remote app · independently deployable'
    },
    {
      id: 'notification-center',
      tag: 'nebulaops-mfe-notification-center',
      title: 'Notification Center',
      subtitle: 'Live notification stream, preferences, audit-linked alerts and platform events.',
      icon: '🔔',
      route: '/remotes/notification-center/',
      color: 'green',
      scope: 'Platform · Notifications/SSE',
      group: 'Notifications',
      service: 'mfe-notification-center',
      path: '/remotes/notification-center/remoteEntry.js',
      status: 'remote app · independently deployable'
    },
    {
      id: 'identity-admin',
      tag: 'nebulaops-mfe-identity-admin',
      title: 'Identity Admin',
      subtitle: 'Keycloak realm users, groups and roles with Redis-backed list cache.',
      icon: '👥',
      route: '/remotes/identity-admin/',
      color: 'blue',
      scope: 'Identity · Keycloak Realm',
      group: 'Identity',
      service: 'mfe-identity-admin',
      path: '/remotes/identity-admin/remoteEntry.js',
      status: 'remote app · independently deployable'
    }
  ] as RemoteDefinition[];

  readonly serviceLinks: ServiceLink[] = [


{
        "title": "Identity Admin MFE",
        "subtitle": "Identity · Keycloak Realm",
        "url": "/remotes/identity-admin/",
        "icon": "👥",
        "port": "/remotes",
        "group": "Identity"
},
{
        "title": "Identity Admin API",
        "subtitle": "Keycloak users, groups and roles through auth-service with Redis cache",
        "url": "/api/identity/realms/nebulaops/users",
        "icon": "👥",
        "port": "8081",
        "group": "Identity"
},

{
        "title": "Notification Center MFE",
        "subtitle": "Platform · Notifications/SSE",
        "url": "/remotes/notification-center/",
        "icon": "🔔",
        "port": "/remotes",
        "group": "Notifications"
},

{
        "title": "INFRA Hub MFE",
        "subtitle": "Infrastructure · Hub",
        "url": "/remotes/infra-hub/",
        "icon": "🛰️",
        "port": "/remotes",
        "group": "Micro Frontend"
},

{
        "title": "Progressive Delivery Center MFE",
        "subtitle": "Release · Argo Rollouts · Canary/Blue-Green",
        "url": "/remotes/progressive-delivery/",
        "icon": "🚢",
        "port": "/remotes",
        "group": "Release"
},
{
        "title": "Progressive Delivery API",
        "subtitle": "Argo Rollouts and Argo CD runtime API",
        "url": "/api/progressive-delivery/overview?namespace=all",
        "icon": "🚢",
        "port": "8102",
        "group": "Release"
},
{
        "title": "Release Center MFE",
        "subtitle": "Release · Promotion/Rollback",
        "url": "/remotes/release-center/",
        "icon": "🚢",
        "port": "/remotes",
        "group": "Release"
},
{
        "title": "Policy, Approval & Governance Center MFE",
        "subtitle": "Governance · Approval · OPA",
        "url": "/remotes/policy-center/",
        "icon": "⚖️",
        "port": "/remotes",
        "group": "Governance"
},
{
        "title": "Release Orchestrator API",
        "subtitle": "Release lifecycle backend",
        "url": "/api/releases",
        "icon": "🚢",
        "port": "8098",
        "group": "Release"
},
{
        "title": "Policy Governance API",
        "subtitle": "Policy evaluation backend",
        "url": "/api/policies",
        "icon": "⚖️",
        "port": "8100",
        "group": "Governance"
},
{
        "title": "Audit API",
        "subtitle": "Platform events and audit trail",
        "url": "/api/events",
        "icon": "🧾",
        "port": "8101",
        "group": "Observability"
},
    {
        "title": "Keycloak",
        "subtitle": "Identity Provider",
        "url": "/keycloak/",
        "icon": "🔑",
        "port": "8180",
        "group": "Identity"
    },
    {
        "title": "Gateway",
        "subtitle": "BFF + API proxy",
        "url": "/actuator/health",
        "icon": "🚪",
        "port": "8080",
        "group": "Runtime"
    },
    {
        "title": "FinOps Cost MFE",
        "subtitle": "Cost · Analytics",
        "url": "/remotes/finops-cost/",
        "icon": "💸",
        "port": "/remotes",
        "group": "Micro Frontend"
    },
    {
        "title": "AI Ops MFE",
        "subtitle": "AI · RCA/Assist",
        "url": "/remotes/ai-ops/",
        "icon": "🤖",
        "port": "/remotes",
        "group": "Micro Frontend"
    },
    {
        "title": "DevSecOps MFE",
        "subtitle": "Security · Secrets/Vulns",
        "url": "/remotes/devsecops/",
        "icon": "🛡️",
        "port": "/remotes",
        "group": "Micro Frontend"
    },
    {
        "title": "Terraform Studio MFE",
        "subtitle": "IaC · Environments",
        "url": "/remotes/terraform-studio/",
        "icon": "🧱",
        "port": "/remotes",
        "group": "Micro Frontend"
    },
    {
        "title": "CI/CD + GitOps MFE",
        "subtitle": "DevOps · Pipelines/GitOps",
        "url": "/remotes/cicd-gitops/",
        "icon": "🚀",
        "port": "/remotes",
        "group": "Micro Frontend"
    },
    {
        "title": "Observability & Audit Center MFE",
        "subtitle": "SRE · Metrics/Logs/Traces/Audit",
        "url": "/remotes/observability/",
        "icon": "📈",
        "port": "/remotes",
        "group": "Micro Frontend"
    },
    {
        "title": "Task Management MFE",
        "subtitle": "Delivery · Tasks",
        "url": "/remotes/task-management/",
        "icon": "✅",
        "port": "/remotes",
        "group": "Micro Frontend"
    },
    {
        "title": "OpenLens Kubernetes MFE",
        "subtitle": "Runtime · Kubernetes",
        "url": "/remotes/openlens-kubernetes/",
        "icon": "☸️",
        "port": "/remotes",
        "group": "Micro Frontend"
    },
    {
        "title": "Docker Desktop MFE",
        "subtitle": "Runtime · Docker",
        "url": "/remotes/docker-desktop/",
        "icon": "🐳",
        "port": "/remotes",
        "group": "Micro Frontend"
    },
    {
        "title": "Grafana",
        "subtitle": "Dashboards + traces",
        "url": "/grafana/",
        "icon": "🌀",
        "port": "3000",
        "group": "Observability"
    },
    {
        "title": "Prometheus",
        "subtitle": "Metrics",
        "url": "/prometheus/",
        "icon": "▲",
        "port": "9090",
        "group": "Observability"
    },
    {
        "title": "RabbitMQ",
        "subtitle": "Queue management SSO/native",
        "url": "http://localhost:15672",
        "icon": "✉️",
        "port": "15672",
        "group": "Data"
    },
    {
        "title": "Mongo Express",
        "subtitle": "Document database UI SSO/native",
        "url": "http://localhost:8088",
        "icon": "🍃",
        "port": "8088",
        "group": "Data"
    },
    {
        "title": "Redis Commander",
        "subtitle": "Cache browser SSO/native",
        "url": "http://localhost:8089",
        "icon": "🔴",
        "port": "8089",
        "group": "Data"
    },
    {
        "title": "GitLab",
        "subtitle": "Optional CI/CD profile",
        "url": "http://localhost:8929",
        "icon": "🦊",
        "port": "8929",
        "group": "DevOps"
    }
] as ServiceLink[];

  readonly activeRemoteConfig = computed(() => this.remotes.find(r => r?.id === this.activeRemote()) ?? this.remotes[0]);

  /** Sidebar nav groups — order and membership defined here */
  readonly remoteCount = computed(() => this.remotes.length);
  readonly liveRemoteCount = computed(() => this.remotes.filter(remote => remote.status.includes('remote app')).length);
  readonly releaseAndGovCount = computed(() => this.remotes.filter(remote => ['Release', 'Governance', 'Notifications'].includes(remote.group)).length);
  readonly shellStatusClass = computed(() => this.shellStatus());

  sidebarGroups = [
    { label: 'Identity',       icon: '👥',  groups: ['Identity'] },
    { label: 'Runtime',        icon: '⚙️',  groups: ['Runtime'] },
    { label: 'Platform Ops',   icon: '🔔',  groups: ['Notifications'] },
    { label: 'Delivery',       icon: '📦',  groups: ['Delivery'] },
    { label: 'SRE',            icon: '📡',  groups: ['SRE'] },
    { label: 'DevOps & IaC',   icon: '🔧',  groups: ['DevOps', 'IaC'] },
    { label: 'Release & Gov',  icon: '🚢',  groups: ['Release', 'Governance'] },
    { label: 'Security & AI',  icon: '🔐',  groups: ['Security', 'AI', 'FinOps'] },
  ] as const;

  remotesByGroup(groups: readonly string[]): RemoteDefinition[] {
    const term = this.search().trim().toLowerCase();
    return this.remotes
      .filter(r => groups.includes(r.group))
      .filter(item => !term || [item.title, item.subtitle, item.group, item.scope, item.route].join(' ').toLowerCase().includes(term));
  }
  readonly filteredRemotes = computed(() => {
    const term = this.search().trim().toLowerCase();
    if (!term) return this.remotes;
    return this.remotes.filter(item =>
      [item.title, item.subtitle, item.group, item.scope, item.route].join(' ').toLowerCase().includes(term)
    );
  });

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
      void this.refreshShellHealth();
    }
  }

  async refreshShellHealth(): Promise<void> {
    const checks: Array<[string, string]> = [
      ['gateway', '/actuator/health'],
      ['releases', '/api/releases'],
      ['policies', '/api/policies'],
      ['notifications', '/api/notifications/live'],
    ];
    const result: Record<string, string> = {};
    let failures = 0;
    await Promise.all(checks.map(async ([key, url]) => {
      try {
        const response = await fetch(url);
        result[key] = response.ok ? 'UP' : `HTTP ${response.status}`;
        if (!response.ok) failures += 1;
      } catch {
        result[key] = 'DOWN';
        failures += 1;
      }
    }));
    this.quickHealth.set(result);
    this.shellStatus.set(failures === 0 ? 'online' : 'degraded');
    this.shellStatusMessage.set(failures === 0
      ? 'Gateway, release, policy and notification endpoints are reachable.'
      : `${failures} live endpoint(s) are degraded. Open INFRA Hub for details.`);
  }

  async login(): Promise<void> {
    this.loginError.set('');
    try {
      const verifier = this.randomString(96);
      const challenge = await this.sha256Base64Url(verifier);
      sessionStorage.setItem('nebulaops.v22_4.pkce', verifier);
      const state = this.randomString(24);
      sessionStorage.setItem('nebulaops.v22_4.state', state);
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
      this.loginError.set('Unable to start Keycloak login. Verify that the NebulaOps reverse proxy and Keycloak are running.');
      console.error(error);
    }
  }

  logout(): void {
    localStorage.removeItem(JWT_KEY);
    localStorage.removeItem(USER_KEY);
    sessionStorage.removeItem('nebulaops.v22_4.pkce');
    sessionStorage.removeItem('nebulaops.v22_4.state');
    this.authenticated.set(false);
    const params = new URLSearchParams({
      client_id: KC_CLIENT_ID,
      post_logout_redirect_uri: KC_REDIRECT_URI
    });
    window.location.href = `${KC_LOGOUT_URL}?${params.toString()}`;
  }

  openLauncher(): void { this.appLauncherOpen.set(true); }
  openCommandPalette(): void { this.commandPaletteOpen.set(true); this.search.set(''); }
  closeCommandPalette(): void { this.commandPaletteOpen.set(false); }
  closeLauncher(): void { this.appLauncherOpen.set(false); }
  toggleSessionPanel(): void { this.sessionPanelOpen.update(v => !v); }

  handleGlobalShortcut(event: KeyboardEvent): void {
    const key = event.key.toLowerCase();
    if ((event.metaKey || event.ctrlKey) && key === 'k') {
      event.preventDefault();
      this.openCommandPalette();
    }
    if (key === 'escape') {
      this.closeCommandPalette();
      this.closeLauncher();
    }
  }

  async runCommand(remote: RemoteDefinition): Promise<void> {
    this.closeCommandPalette();
    await this.activateRemote(remote.id);
  }

  openActiveRemoteExternal(): void {
    const remote = this.remotes.find(item => item?.id === this.activeRemote());
    if (remote) window.open(`/remotes/${remote.id}/`, '_blank', 'noopener,noreferrer');
  }

  openService(link: ServiceLink): void {
    window.open(link.url, '_blank', 'noopener,noreferrer');
  }

  async activateRemote(id: RemoteId): Promise<void> {
    const def = this.remotes.find(r => r?.id === id);
    if (!def) return;
    this.remoteError.set('');
    this.loadingRemote.set(true);
    try {
      await this.loadRemote(def);
      this.activeRemote.set(id);
    } catch (error) {
      this.remoteError.set(`Remote ${def.title} is not available through ${PUBLIC_ORIGIN}/remotes/${def.id}/. Rebuild the frontend image and verify that the remote static bundle exists under frontend/remotes/${def.id}/dist/browser.`);
      console.error(error);
    } finally {
      this.loadingRemote.set(false);
    }
  }

  trackByRemote(index: number, item: RemoteDefinition): string { return item?.id ?? `remote-${index}`; }
  trackByLink(_: number, item: ServiceLink): string { return item.title; }

  private async handleKeycloakCallback(): Promise<void> {
    const url = new URL(window.location.href);
    const code = url.searchParams.get('code');
    const state = url.searchParams.get('state');
    if (!code) return;
    const expectedState = sessionStorage.getItem('nebulaops.v22_4.state');
    if (expectedState && state !== expectedState) {
      this.loginError.set('Invalid Keycloak response: state mismatch.');
      return;
    }
    const verifier = sessionStorage.getItem('nebulaops.v22_4.pkce') || '';
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
        this.loginError.set('Keycloak login callback received, but the token exchange failed. Reimport the realm and verify the nebulaops-frontend client.');
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
      if (existing && !customElements.get(def.tag)) {
        existing.remove();
      }
      const script = document.createElement('script');
      const separator = def.path.includes('?') ? '&' : '?';
      script.src = `${def.path}${separator}v=${REMOTE_ENTRY_BUILD}-${Date.now()}`;
      script.defer = true;
      script.dataset['remoteId'] = def.id;
      script.onload = () => {
        requestAnimationFrame(() => {
          if (!customElements.get(def.tag)) {
            reject(new Error(`Remote ${def.title} loaded but did not register ${def.tag}`));
            return;
          }
          this.loadedRemotes.add(def.id);
          resolve();
        });
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
