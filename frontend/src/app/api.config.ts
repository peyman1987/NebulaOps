/**
 * v22.3 — Centralized frontend API configuration.
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
} as const;

export const APP_VERSION = '22.3';
export const APP_RELEASE = 'v22.3';
export const JWT_KEY     = 'nebulaops.v22_3.jwt';
export const USER_KEY    = 'nebulaops.v22_3.user';

// ── Keycloak OIDC (Authorization Code + PKCE) ──────────────────
export const KC_BASE         = '/keycloak';
export const KC_REALM        = 'nebulaops';
export const KC_CLIENT_ID    = 'nebulaops-frontend';
export const KC_REDIRECT_URI = `${PUBLIC_ORIGIN}/`;
export const KC_AUTH_URL     = `${KC_BASE}/realms/${KC_REALM}/protocol/openid-connect/auth`;
export const KC_TOKEN_URL    = `${KC_BASE}/realms/${KC_REALM}/protocol/openid-connect/token`;
export const KC_LOGOUT_URL   = `${KC_BASE}/realms/${KC_REALM}/protocol/openid-connect/logout`;
export const KC_USERINFO_URL = `${KC_BASE}/realms/${KC_REALM}/protocol/openid-connect/userinfo`;
