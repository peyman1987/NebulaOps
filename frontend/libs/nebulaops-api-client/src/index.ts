export interface ApiClientOptions {
  timeoutMs?: number;
  jwtKey?: string;
}

export async function getJson<T>(url: string, options: ApiClientOptions = {}): Promise<T> {
  const timeoutMs = options.timeoutMs ?? 5000;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const token = options.jwtKey ? localStorage.getItem(options.jwtKey) : '';
    const response = await fetch(url, {
      signal: controller.signal,
      headers: token ? { Authorization: `Bearer ${token}` } : undefined
    });
    if (!response.ok) throw new Error(`${url} returned HTTP ${response.status}`);
    return await response.json() as T;
  } finally {
    clearTimeout(timer);
  }
}
