export type SourceState = 'LOADING' | 'READY' | 'DEGRADED' | 'UNAVAILABLE' | 'NOT_CONFIGURED' | 'ERROR';

export interface LiveStateSnapshot {
  key: string;
  source: string;
  state: SourceState;
  message?: string;
  correlationId?: string;
  generatedAt: string;
}

export function normalizeSourceState(value: unknown): SourceState {
  const state = String(value ?? '').toUpperCase();
  if (['LOADING', 'READY', 'DEGRADED', 'UNAVAILABLE', 'NOT_CONFIGURED', 'ERROR'].includes(state)) return state as SourceState;
  return 'ERROR';
}

export function liveState(key: string, state: SourceState | string, message?: string, source = 'runtime', correlationId?: string): LiveStateSnapshot {
  return { key, source, state: normalizeSourceState(state), message, correlationId, generatedAt: new Date().toISOString() };
}

export function unavailableState(key: string, source: string, message: string): LiveStateSnapshot {
  return liveState(key, 'UNAVAILABLE', message, source);
}

export function notConfiguredState(key: string, source: string, message: string): LiveStateSnapshot {
  return liveState(key, 'NOT_CONFIGURED', message, source);
}
