/**
 * v21.2 — Centralized frontend API configuration.
 * All HTTP calls in app.component.ts read URLs from here.
 * Synced from config/platform.yml via scripts/sync-config.sh.
 */
export const API_BASE = '/api';

export const API = {
  health:                `${API_BASE}/health`,
  auth: {
    login:                `${API_BASE}/auth/login`,
    register:             `${API_BASE}/auth/register`,
  },
  tasks: {
    list: (org: string) => `${API_BASE}/tasks?organizationId=${encodeURIComponent(org)}`,
    create:               `${API_BASE}/tasks`,
    update: (id: string) => `${API_BASE}/tasks/${encodeURIComponent(id)}`,
    delete: (id: string) => `${API_BASE}/tasks/${encodeURIComponent(id)}`,
    status: (id: string, s: string) => `${API_BASE}/tasks/${encodeURIComponent(id)}/status/${s}`,
  },
  kubernetes: {
    snapshot:             `${API_BASE}/kubernetes/snapshot`,
    logs:                 `${API_BASE}/kubernetes/logs`,
  },
  runtime: {
    dockerContainers:     `${API_BASE}/runtime/docker/containers`,
    dockerImages:         `${API_BASE}/runtime/docker/images`,
    dockerVolumes:        `${API_BASE}/runtime/docker/volumes`,
    helmReleases:         `${API_BASE}/runtime/helm/releases?namespace=all`,
  },
  platform: {
    observability:        `${API_BASE}/platform/observability`,
    gitops:               `${API_BASE}/platform/gitops`,
    devsecops:            `${API_BASE}/platform/devsecops`,
    environments:         `${API_BASE}/platform/environments`,
  },
  aiOps: {
    analyze:              `${API_BASE}/ai-ops/analyze`,
    autofix:              `${API_BASE}/ai-ops/autofix`,
  },
} as const;

export const APP_VERSION = '21.2';
export const APP_RELEASE = 'v21.2';
