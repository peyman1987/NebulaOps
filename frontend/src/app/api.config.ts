/**
 * v21.3 — Centralized frontend API configuration.
 * All HTTP calls read URLs from here. Synced from config/platform.yml.
 */
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
    // Pod actions
    deletePod:  (ns: string, name: string) => `${API_BASE}/kubernetes/pods/${ns}/${name}`,
    restartPod: (ns: string, name: string) => `${API_BASE}/kubernetes/pods/${ns}/${name}/restart`,
    podLogs:    (ns: string, name: string) => `${API_BASE}/kubernetes/pods/${ns}/${name}/logs`,
    describePod:(ns: string, name: string) => `${API_BASE}/kubernetes/pods/${ns}/${name}/describe`,
    // Deployment actions
    scaleDeployment:   (ns: string, name: string) => `${API_BASE}/kubernetes/deployments/${ns}/${name}/scale`,
    restartDeployment: (ns: string, name: string) => `${API_BASE}/kubernetes/deployments/${ns}/${name}/restart`,
    deploymentYaml:    (ns: string, name: string) => `${API_BASE}/kubernetes/deployments/${ns}/${name}/yaml`,
    describeDeployment:(ns: string, name: string) => `${API_BASE}/kubernetes/deployments/${ns}/${name}/describe`,
    // Service actions
    serviceYaml:    (ns: string, name: string) => `${API_BASE}/kubernetes/services/${ns}/${name}/yaml`,
    describeService:(ns: string, name: string) => `${API_BASE}/kubernetes/services/${ns}/${name}/describe`,
    // Ingress actions
    ingressYaml:    (ns: string, name: string) => `${API_BASE}/kubernetes/ingresses/${ns}/${name}/yaml`,
    describeIngress:(ns: string, name: string) => `${API_BASE}/kubernetes/ingresses/${ns}/${name}/describe`,
    // StatefulSet actions
    scaleStatefulSet:   (ns: string, name: string) => `${API_BASE}/kubernetes/statefulsets/${ns}/${name}/scale`,
    restartStatefulSet: (ns: string, name: string) => `${API_BASE}/kubernetes/statefulsets/${ns}/${name}/restart`,
    // Node actions
    cordonNode:  (name: string) => `${API_BASE}/kubernetes/nodes/${name}/cordon`,
    uncordonNode:(name: string) => `${API_BASE}/kubernetes/nodes/${name}/uncordon`,
    drainNode:   (name: string) => `${API_BASE}/kubernetes/nodes/${name}/drain`,
    describeNode:(name: string) => `${API_BASE}/kubernetes/nodes/${name}/describe`,
    // Namespaces
    createNamespace: `${API_BASE}/kubernetes/namespaces`,
    // Generic
    apply:  `${API_BASE}/kubernetes/apply`,
    delete: `${API_BASE}/kubernetes/delete`,
  },

  runtime: {
    dockerContainers: `${API_BASE}/runtime/docker/containers`,
    dockerImages:     `${API_BASE}/runtime/docker/images`,
    dockerVolumes:    `${API_BASE}/runtime/docker/volumes`,
    dockerNetworks:   `${API_BASE}/runtime/docker/networks`,
    helmReleases:     `${API_BASE}/runtime/helm/releases?namespace=all`,
    // Container actions
    containerStart:   (id: string) => `${API_BASE}/runtime/docker/containers/${id}/start`,
    containerStop:    (id: string) => `${API_BASE}/runtime/docker/containers/${id}/stop`,
    containerRestart: (id: string) => `${API_BASE}/runtime/docker/containers/${id}/restart`,
    containerPause:   (id: string) => `${API_BASE}/runtime/docker/containers/${id}/pause`,
    containerUnpause: (id: string) => `${API_BASE}/runtime/docker/containers/${id}/unpause`,
    containerRemove:  (id: string) => `${API_BASE}/runtime/docker/containers/${id}`,
    containerLogs:    (id: string) => `${API_BASE}/runtime/docker/containers/${id}/logs`,
    containerStats:   (id: string) => `${API_BASE}/runtime/docker/containers/${id}/stats`,
    // Image actions
    imageRemove:  (id: string) => `${API_BASE}/runtime/docker/images/${id}`,
    imagesPrune:  `${API_BASE}/runtime/docker/images/prune`,
    // Volume actions
    volumeRemove: (name: string) => `${API_BASE}/runtime/docker/volumes/${name}`,
    volumesPrune: `${API_BASE}/runtime/docker/volumes/prune`,
    // Network actions
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
  },
} as const;

export const APP_VERSION = '21.3';
export const APP_RELEASE = 'v21.3';
export const JWT_KEY     = 'nebulaops.v21_3.jwt';
export const USER_KEY    = 'nebulaops.v21_3.user';
