export type RuntimeState = 'UP' | 'DEGRADED' | 'DOWN' | 'UNAVAILABLE' | 'NOT_CONFIGURED' | 'UNKNOWN';

export interface RuntimeEnvelope<T = unknown> {
  live: boolean;
  realDataOnly: true;
  state?: RuntimeState | string;
  items?: T[];
  toolStatus?: unknown;
  generatedAt?: string;
  message?: string;
}

export function normalizeRuntimeState(value: unknown): RuntimeState {
  const state = String(value ?? '').toUpperCase();
  if (['UP', 'DEGRADED', 'DOWN', 'UNAVAILABLE', 'NOT_CONFIGURED'].includes(state)) return state as RuntimeState;
  return 'UNKNOWN';
}

export function assertRealDataOnly<T extends RuntimeEnvelope>(payload: T): T {
  if (payload.realDataOnly !== true) {
    throw new Error('NebulaOps runtime payload must declare realDataOnly=true. Mock/static operational data is not accepted.');
  }
  return payload;
}
