export type NebulaOpsSourceState = 'READY' | 'DEGRADED' | 'UNAVAILABLE' | 'NOT_CONFIGURED' | 'ERROR' | 'LOADING';

export interface NebulaOpsApiClientOptions {
  baseUrl?: string;
  timeoutMs?: number;
  jwtKey?: string;
  userKey?: string;
  correlationIdFactory?: () => string;
}

export interface NebulaOpsRuntimeEnvelope<T = unknown> {
  live: boolean;
  realDataOnly: true;
  source: string;
  state: NebulaOpsSourceState | string;
  items?: T[];
  data?: T;
  toolStatus?: unknown;
  errors?: unknown[];
  message?: string;
  correlationId?: string;
  generatedAt: string;
}

export class NebulaOpsApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly url: string,
    readonly correlationId?: string,
    readonly body?: unknown
  ) {
    super(message);
    this.name = 'NebulaOpsApiError';
  }
}

export class NebulaOpsApiClient {
  private readonly baseUrl: string;
  private readonly timeoutMs: number;
  private readonly jwtKey: string;
  private readonly correlationIdFactory: () => string;

  constructor(options: NebulaOpsApiClientOptions = {}) {
    this.baseUrl = options.baseUrl ?? '/api';
    this.timeoutMs = options.timeoutMs ?? 12000;
    this.jwtKey = options.jwtKey ?? 'nebulaops.v24_1.jwt';
    this.correlationIdFactory = options.correlationIdFactory ?? (() => `nb-${Date.now()}-${Math.random().toString(16).slice(2)}`);
  }

  async get<T>(path: string, init: RequestInit = {}): Promise<T> {
    return this.request<T>(path, {...init, method: 'GET'});
  }

  async post<T>(path: string, body?: unknown, init: RequestInit = {}): Promise<T> {
    return this.request<T>(path, {...init, method: 'POST', body: body === undefined ? undefined : JSON.stringify(body)});
  }

  async put<T>(path: string, body?: unknown, init: RequestInit = {}): Promise<T> {
    return this.request<T>(path, {...init, method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body)});
  }

  async delete<T>(path: string, init: RequestInit = {}): Promise<T> {
    return this.request<T>(path, {...init, method: 'DELETE'});
  }

  async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const url = this.toUrl(path);
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.timeoutMs);
    const correlationId = this.correlationIdFactory();
    try {
      const headers = this.buildHeaders(init.headers, correlationId, init.body);
      const response = await fetch(url, {...init, headers, signal: controller.signal});
      const contentType = response.headers.get('content-type') || '';
      const body = contentType.includes('application/json') ? await response.json().catch(() => undefined) : await response.text().catch(() => undefined);
      if (!response.ok) {
        throw new NebulaOpsApiError(`${url} returned HTTP ${response.status}`, response.status, url, correlationId, body);
      }
      return body as T;
    } catch (error) {
      if (error instanceof NebulaOpsApiError) throw error;
      if ((error as Error).name === 'AbortError') {
        throw new NebulaOpsApiError(`${url} timed out after ${this.timeoutMs}ms`, 408, url, correlationId);
      }
      throw new NebulaOpsApiError((error as Error).message || 'NebulaOps API request failed', 0, url, correlationId);
    } finally {
      clearTimeout(timeout);
    }
  }

  assertLiveEnvelope<T>(payload: NebulaOpsRuntimeEnvelope<T>): NebulaOpsRuntimeEnvelope<T> {
    if (!payload || payload.realDataOnly !== true || payload.live !== true) {
      throw new NebulaOpsApiError('NebulaOps API payload is not a live real-data envelope', 502, this.baseUrl, payload?.correlationId, payload);
    }
    return payload;
  }

  private toUrl(path: string): string {
    if (/^https?:\/\//i.test(path)) return path;
    if (path.startsWith('/api/')) return path;
    if (path === '/api') return path;
    return `${this.baseUrl.replace(/\/$/, '')}/${path.replace(/^\//, '')}`;
  }

  private buildHeaders(input: HeadersInit | undefined, correlationId: string, body: BodyInit | null | undefined): Headers {
    const headers = new Headers(input || {});
    const token = this.readToken();
    if (token && !headers.has('Authorization')) headers.set('Authorization', `Bearer ${token}`);
    if (body !== undefined && body !== null && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
    if (!headers.has('Accept')) headers.set('Accept', 'application/json');
    headers.set('X-Correlation-Id', correlationId);
    headers.set('X-NebulaOps-Release', 'v24.1');
    return headers;
  }

  private readToken(): string {
    try { return localStorage.getItem(this.jwtKey) || ''; } catch { return ''; }
  }
}

export const nebulaOpsApiClient = new NebulaOpsApiClient();

export async function getJson<T>(url: string, options: NebulaOpsApiClientOptions = {}): Promise<T> {
  return new NebulaOpsApiClient(options).get<T>(url);
}
