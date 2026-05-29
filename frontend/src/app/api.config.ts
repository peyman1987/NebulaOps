/**
 * v24.1 — Centralized frontend API configuration.
 * Public browser access is served through the same reverse-proxy origin:
 *   http://nebulaops.localhost
 *
 * API, Keycloak and remote MFE URLs intentionally avoid localhost:port
 * hard-coding so the shell can run behind a single domain/origin.
 */
export const PUBLIC_ORIGIN = typeof window !== 'undefined'
  ? window.location.origin
  : 'http://nebulaops.localhost';

export const API_BASE = '/api';

export const API = {
  health: `${API_BASE}/health`,

  auth: {
    login:    `${API_BASE}/auth/login`,
    register: `${API_BASE}/auth/register`,
    me:       `${API_BASE}/auth/me`,
    refresh:  `${API_BASE}/auth/refresh`,
    logout:   `${API_BASE}/auth/logout`,
  },

  tasks: {
    list:   (org: string) => `${API_BASE}/tasks?organizationId=${encodeURIComponent(org)}`,
    create: `${API_BASE}/tasks`,
    update: (id: string) => `${API_BASE}/tasks/${encodeURIComponent(id)}`,
    delete: (id: string) => `${API_BASE}/tasks/${encodeURIComponent(id)}`,
    status: (id: string, s: string) => `${API_BASE}/tasks/${encodeURIComponent(id)}/status/${s}`,
  },

  kubernetes: {
    snapshot:  `${API_BASE}/kubernetes/snapshot`,
    logs:      `${API_BASE}/kubernetes/logs`,
    deletePod:  (ns: string, name: string) => `${API_BASE}/kubernetes/pods/${ns}/${name}`,
    restartPod: (ns: string, name: string) => `${API_BASE}/kubernetes/pods/${ns}/${name}/restart`,
    podLogs:    (ns: string, name: string) => `${API_BASE}/kubernetes/pods/${ns}/${name}/logs`,
    describePod:(ns: string, name: string) => `${API_BASE}/kubernetes/pods/${ns}/${name}/describe`,
    scaleDeployment:   (ns: string, name: string) => `${API_BASE}/kubernetes/deployments/${ns}/${name}/scale`,
    restartDeployment: (ns: string, name: string) => `${API_BASE}/kubernetes/deployments/${ns}/${name}/restart`,
    deploymentYaml:    (ns: string, name: string) => `${API_BASE}/kubernetes/deployments/${ns}/${name}/yaml`,
    describeDeployment:(ns: string, name: string) => `${API_BASE}/kubernetes/deployments/${ns}/${name}/describe`,
    serviceYaml:    (ns: string, name: string) => `${API_BASE}/kubernetes/services/${ns}/${name}/yaml`,
    describeService:(ns: string, name: string) => `${API_BASE}/kubernetes/services/${ns}/${name}/describe`,
    ingressYaml:    (ns: string, name: string) => `${API_BASE}/kubernetes/ingresses/${ns}/${name}/yaml`,
    describeIngress:(ns: string, name: string) => `${API_BASE}/kubernetes/ingresses/${ns}/${name}/describe`,
    scaleStatefulSet:   (ns: string, name: string) => `${API_BASE}/kubernetes/statefulsets/${ns}/${name}/scale`,
    restartStatefulSet: (ns: string, name: string) => `${API_BASE}/kubernetes/statefulsets/${ns}/${name}/restart`,
    cordonNode:  (name: string) => `${API_BASE}/kubernetes/nodes/${name}/cordon`,
    uncordonNode:(name: string) => `${API_BASE}/kubernetes/nodes/${name}/uncordon`,
    drainNode:   (name: string) => `${API_BASE}/kubernetes/nodes/${name}/drain`,
    describeNode:(name: string) => `${API_BASE}/kubernetes/nodes/${name}/describe`,
    createNamespace: `${API_BASE}/kubernetes/namespaces`,
    apply:  `${API_BASE}/kubernetes/apply`,
    delete: `${API_BASE}/kubernetes/delete`,
  },

  runtime: {
    dockerContainers: `${API_BASE}/runtime/docker/containers`,
    dockerImages:     `${API_BASE}/runtime/docker/images`,
    dockerVolumes:    `${API_BASE}/runtime/docker/volumes`,
    dockerNetworks:   `${API_BASE}/runtime/docker/networks`,
    diagnostics:      `${API_BASE}/runtime/diagnostics`,
    helmReleases:     `${API_BASE}/runtime/helm/releases?namespace=all`,
    containerStart:   (id: string) => `${API_BASE}/runtime/docker/containers/${id}/start`,
    containerStop:    (id: string) => `${API_BASE}/runtime/docker/containers/${id}/stop`,
    containerRestart: (id: string) => `${API_BASE}/runtime/docker/containers/${id}/restart`,
    containerPause:   (id: string) => `${API_BASE}/runtime/docker/containers/${id}/pause`,
    containerUnpause: (id: string) => `${API_BASE}/runtime/docker/containers/${id}/unpause`,
    containerRemove:  (id: string) => `${API_BASE}/runtime/docker/containers/${id}`,
    containerLogs:    (id: string) => `${API_BASE}/runtime/docker/containers/${id}/logs`,
    containerStats:   (id: string) => `${API_BASE}/runtime/docker/containers/${id}/stats`,
    imageRemove:  (id: string) => `${API_BASE}/runtime/docker/images/${id}`,
    imagesPrune:  `${API_BASE}/runtime/docker/images/prune`,
    volumeRemove: (name: string) => `${API_BASE}/runtime/docker/volumes/${name}`,
    volumesPrune: `${API_BASE}/runtime/docker/volumes/prune`,
    networksPrune: `${API_BASE}/runtime/docker/networks/prune`,
  },

  platform: {
    observability: `${API_BASE}/platform/observability`,
    gitops:        `${API_BASE}/platform/gitops`,
    devsecops:     `${API_BASE}/platform/devsecops`,
    environments:  `${API_BASE}/platform/environments`,
  },

  aiOps: {
    analyze: `${API_BASE}/ai-ops/analyze`,
    autofix: `${API_BASE}/ai-ops/autofix`,
    incidents: `${API_BASE}/ai-ops/incidents`,
    incidentAnalyze: `${API_BASE}/ai-ops/incidents/analyze`,
    incidentRunbook: (id: string) => `${API_BASE}/ai-ops/incidents/${id}/runbook`,
  },

  release: {
    list: `${API_BASE}/releases`,
    detail: (id: string) => `${API_BASE}/releases/${id}`,
    create: `${API_BASE}/releases`,
    promote: (id: string) => `${API_BASE}/releases/${id}/promote`,
    rollback: (id: string) => `${API_BASE}/releases/${id}/rollback`,
    timeline: (id: string) => `${API_BASE}/releases/${id}/timeline`,
    healthImpact: (id: string) => `${API_BASE}/releases/${id}/health-impact`,
  },

  policy: {
    list: `${API_BASE}/policies`,
    create: `${API_BASE}/policies`,
    update: (id: string) => `${API_BASE}/policies/${id}`,
    delete: (id: string) => `${API_BASE}/policies/${id}`,
    evaluate: `${API_BASE}/policies/evaluate`,
    evaluations: `${API_BASE}/policies/evaluations`,
  },

  progressiveDelivery: {
    overview: (namespace: string = 'all') => `${API_BASE}/progressive-delivery/overview?namespace=${encodeURIComponent(namespace)}`,
    rollouts: (namespace: string = 'all') => `${API_BASE}/progressive-delivery/rollouts?namespace=${encodeURIComponent(namespace)}`,
    applications: `${API_BASE}/progressive-delivery/applications`,
    analysisRuns: (namespace: string = 'all') => `${API_BASE}/progressive-delivery/analysis-runs?namespace=${encodeURIComponent(namespace)}`,
    experiments: (namespace: string = 'all') => `${API_BASE}/progressive-delivery/experiments?namespace=${encodeURIComponent(namespace)}`,
    promote: (namespace: string, name: string, full: boolean = false) => `${API_BASE}/progressive-delivery/rollouts/${encodeURIComponent(namespace)}/${encodeURIComponent(name)}/promote${full ? '?full=true' : ''}`,
    abort: (namespace: string, name: string) => `${API_BASE}/progressive-delivery/rollouts/${encodeURIComponent(namespace)}/${encodeURIComponent(name)}/abort`,
    restart: (namespace: string, name: string) => `${API_BASE}/progressive-delivery/rollouts/${encodeURIComponent(namespace)}/${encodeURIComponent(name)}/restart`,
    syncApplication: (app: string) => `${API_BASE}/progressive-delivery/applications/${encodeURIComponent(app)}/sync`,
  },

  audit: {
    events: `${API_BASE}/events`,
    auditEvents: `${API_BASE}/audit/events`,
    correlation: (id: string) => `${API_BASE}/audit/correlation/${id}`,
  },

  notifications: {
    list: `${API_BASE}/notifications/live`,
    stream: `${API_BASE}/notifications/stream`,
    preferences: `${API_BASE}/notifications/preferences`,
    markRead: (id: string) => `${API_BASE}/notifications/${id}/read`,
  },

  identity: {
    users:  (realm: string) => `${API_BASE}/identity/realms/${encodeURIComponent(realm)}/users`,
    groups: (realm: string) => `${API_BASE}/identity/realms/${encodeURIComponent(realm)}/groups`,
    roles:  (realm: string) => `${API_BASE}/identity/realms/${encodeURIComponent(realm)}/roles`,
    disableUser:  (realm: string, id: string) => `${API_BASE}/identity/realms/${encodeURIComponent(realm)}/users/${encodeURIComponent(id)}/disable`,
    disableGroup: (realm: string, id: string) => `${API_BASE}/identity/realms/${encodeURIComponent(realm)}/groups/${encodeURIComponent(id)}/disable`,
    disableRole:  (realm: string, name: string) => `${API_BASE}/identity/realms/${encodeURIComponent(realm)}/roles/${encodeURIComponent(name)}/disable`,
  },

  cost: {
    summary: `${API_BASE}/cost/summary`,
    services: `${API_BASE}/cost/services`,
    forecast: `${API_BASE}/cost/forecast`,
    budget: `${API_BASE}/cost/budget`,
    anomalies: `${API_BASE}/cost/anomalies`,
    recommendations: `${API_BASE}/cost/recommendations`,
  },

  devsecopsV23: {
    imageScan: `${API_BASE}/devsecops/scan/image`,
    repositoryScan: `${API_BASE}/devsecops/scan/repository`,
    vulnerabilities: `${API_BASE}/devsecops/vulnerabilities`,
    sbom: (image: string) => `${API_BASE}/devsecops/sbom/${encodeURIComponent(image)}`,
  },

  docs: {
    index: `${API_BASE}/docs`,
    service: (name: string) => `${API_BASE}/docs/${encodeURIComponent(name)}`,
  },

  extensions: {
    list: `${API_BASE}/extensions`,
    console: `${API_BASE}/extensions/console`,
    summary: `${API_BASE}/extensions/summary`,
    status: (id: string) => `${API_BASE}/extensions/${encodeURIComponent(id)}/status`,
    start: (id: string) => `${API_BASE}/extensions/${encodeURIComponent(id)}/start`,
    stop: (id: string) => `${API_BASE}/extensions/${encodeURIComponent(id)}/stop`,
    restart: (id: string) => `${API_BASE}/extensions/${encodeURIComponent(id)}/restart`,
    diagnostics: (id: string) => `${API_BASE}/extensions/${encodeURIComponent(id)}/diagnostics`,
  },
} as const;

export const APP_VERSION = '24.1';
export const APP_RELEASE = 'v24.1';
export const JWT_KEY     = 'nebulaops.v24_1.jwt';
export const USER_KEY    = 'nebulaops.v24_1.user';

// ── Keycloak OIDC (Authorization Code + PKCE) ──────────────────
export const KC_BASE         = '/keycloak';
export const KC_REALM        = 'nebulaops';
export const KC_CLIENT_ID    = 'nebulaops-frontend';
export const KC_REDIRECT_URI = `${PUBLIC_ORIGIN}/`;
export const KC_AUTH_URL     = `${KC_BASE}/realms/${KC_REALM}/protocol/openid-connect/auth`;
export const KC_TOKEN_URL    = `${KC_BASE}/realms/${KC_REALM}/protocol/openid-connect/token`;
export const KC_LOGOUT_URL   = `${KC_BASE}/realms/${KC_REALM}/protocol/openid-connect/logout`;
export const KC_USERINFO_URL = `${KC_BASE}/realms/${KC_REALM}/protocol/openid-connect/userinfo`;


// ── Typed API client v24.1 ─────────────────────────────────────
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface ApiRequestOptions<TBody = unknown> {
  method?: HttpMethod;
  body?: TBody;
  headers?: Record<string, string>;
  signal?: AbortSignal;
}

export interface ExtensionStatus {
  id: 'apiforge' | 'kubebridge' | 'contract-hub' | string;
  title: string;
  state: 'RUNNING' | 'STOPPED' | 'DEGRADED' | 'UNKNOWN' | string;
  deployment?: string;
  service?: string;
  readyReplicas?: number;
  replicas?: number;
  openUrl?: string;
}

export interface HealthResponse { status: string; service: string; version: string; timestamp?: string; }
export interface PageResult<T> { items: T[]; total?: number; live?: boolean; generatedAt?: string; }

export class NebulaApiClient {
  constructor(private readonly base: string = API_BASE, private readonly tokenProvider: () => string | null = () => localStorage.getItem(JWT_KEY)) {}

  async request<TResponse, TBody = unknown>(path: string, options: ApiRequestOptions<TBody> = {}): Promise<TResponse> {
    const headers: Record<string, string> = { Accept: 'application/json', ...(options.headers || {}) };
    const token = this.tokenProvider();
    if (token) headers.Authorization = `Bearer ${token}`;
    let body: BodyInit | undefined;
    if (options.body !== undefined) {
      headers['Content-Type'] = headers['Content-Type'] || 'application/json';
      body = typeof options.body === 'string' ? options.body : JSON.stringify(options.body);
    }
    const response = await fetch(path.startsWith('/api') ? path : `${this.base}${path}`, {
      method: options.method || (body ? 'POST' : 'GET'),
      headers,
      body,
      signal: options.signal,
    });
    if (!response.ok) {
      const text = await response.text().catch(() => '');
      throw new Error(`NebulaOps API ${response.status} ${response.statusText}: ${text}`);
    }
    if (response.status === 204) return undefined as TResponse;
    return response.json() as Promise<TResponse>;
  }

  health() { return this.request<HealthResponse>(API.health); }
  login(body: { email: string; password: string }) { return this.request<{ accessToken: string; refreshToken: string; tokenType: string; user: unknown }>(API.auth.login, { method: 'POST', body }); }
  me() { return this.request<{ user: unknown }>(API.auth.me); }

  tasks(org: string) { return this.request<PageResult<unknown> | unknown[]>(API.tasks.list(org)); }
  kubernetesSnapshot() { return this.request<Record<string, unknown>>(API.kubernetes.snapshot); }
  dockerContainers() { return this.request<Record<string, unknown>>(API.runtime.dockerContainers); }
  runtimeDiagnostics() { return this.request<Record<string, unknown>>(API.runtime.diagnostics); }
  observability() { return this.request<Record<string, unknown>>(API.platform.observability); }
  prometheus(query = 'up') { return this.request<Record<string, unknown>>(`${API_BASE}/platform/observability/prometheus?query=${encodeURIComponent(query)}`); }
  loki(query = '{job=~".+"}') { return this.request<Record<string, unknown>>(`${API_BASE}/platform/observability/loki?query=${encodeURIComponent(query)}`); }
  gitops() { return this.request<Record<string, unknown>>(API.platform.gitops); }
  devsecops(path = '.') { return this.request<Record<string, unknown>>(`${API.platform.devsecops}?path=${encodeURIComponent(path)}`); }
  environments() { return this.request<unknown[]>(API.platform.environments); }
  aiAnalyze(body: Record<string, unknown>) { return this.request<Record<string, unknown>>(API.aiOps.analyze, { method: 'POST', body }); }
  releases() { return this.request<Record<string, unknown>>(API.release.list); }
  policies() { return this.request<Record<string, unknown>>(API.policy.list); }
  progressiveOverview(namespace = 'all') { return this.request<Record<string, unknown>>(API.progressiveDelivery.overview(namespace)); }
  auditEvents() { return this.request<Record<string, unknown>>(API.audit.events); }
  notifications() { return this.request<Record<string, unknown>>(API.notifications.list); }
  identityUsers(realm = 'nebulaops') { return this.request<unknown[]>(API.identity.users(realm)); }
  costSummary() { return this.request<Record<string, unknown>>(API.cost.summary); }
  docsIndex() { return this.request<Record<string, unknown>>(API.docs.index); }

  extensions() { return this.request<{ live: boolean; items: ExtensionStatus[] }>(`${API_BASE}/extensions`); }
  extensionSummary() { return this.request<Record<string, unknown>>(API.extensions.summary); }
  extensionStatus(id: string) { return this.request<ExtensionStatus>(`${API_BASE}/extensions/${encodeURIComponent(id)}/status`); }
  startExtension(id: string) { return this.request<Record<string, unknown>>(`${API_BASE}/extensions/${encodeURIComponent(id)}/start`, { method: 'POST' }); }
  stopExtension(id: string) { return this.request<Record<string, unknown>>(`${API_BASE}/extensions/${encodeURIComponent(id)}/stop`, { method: 'POST' }); }
  restartExtension(id: string) { return this.request<Record<string, unknown>>(`${API_BASE}/extensions/${encodeURIComponent(id)}/restart`, { method: 'POST' }); }
  extensionDiagnostics(id: string) { return this.request<Record<string, unknown>>(API.extensions.diagnostics(id)); }
}

export const nebulaApi = new NebulaApiClient();
