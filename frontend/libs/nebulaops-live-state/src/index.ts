export interface LiveStateSnapshot {
  key: string;
  state: string;
  message?: string;
  generatedAt: string;
}

export function liveState(key: string, state: string, message?: string): LiveStateSnapshot {
  return { key, state, message, generatedAt: new Date().toISOString() };
}
