import {NebulaOpsApiClient, NebulaOpsRuntimeEnvelope} from '../../nebulaops-api-client/src';
import {SourceState, normalizeSourceState} from '../../nebulaops-live-state/src';

export type RuntimeState = SourceState | 'UNKNOWN';

export interface RuntimeEnvelope<T = unknown> extends NebulaOpsRuntimeEnvelope<T> {}

export interface NebulaOpsRemoteBootstrapOptions<T = unknown> {
  tag: string;
  endpoint: string;
  render: (host: HTMLElement, envelope: RuntimeEnvelope<T>) => void;
  loading?: (host: HTMLElement) => void;
  failed?: (host: HTMLElement, error: Error) => void;
  client?: NebulaOpsApiClient;
}

export function normalizeRuntimeState(value: unknown): RuntimeState {
  const state = String(value ?? '').toUpperCase();
  if (['LOADING', 'READY', 'DEGRADED', 'UNAVAILABLE', 'NOT_CONFIGURED', 'ERROR'].includes(state)) return normalizeSourceState(state);
  return 'UNKNOWN';
}

export function assertRealDataOnly<T extends RuntimeEnvelope>(payload: T): T {
  if (payload.realDataOnly !== true || payload.live !== true) {
    throw new Error('NebulaOps runtime payload must be live and declare realDataOnly=true. Mock/static operational data is not accepted.');
  }
  return payload;
}

export function bootstrapNebulaRemote<T = unknown>(options: NebulaOpsRemoteBootstrapOptions<T>): void {
  if (customElements.get(options.tag)) return;
  const client = options.client ?? new NebulaOpsApiClient();
  customElements.define(options.tag, class NebulaOpsRemoteElement extends HTMLElement {
    async connectedCallback(): Promise<void> {
      options.loading?.(this);
      try {
        const envelope = assertRealDataOnly(await client.get<RuntimeEnvelope<T>>(options.endpoint));
        options.render(this, envelope);
      } catch (error) {
        if (options.failed) options.failed(this, error as Error);
        else this.innerHTML = `<section class="nebula-empty-state"><strong>Runtime unavailable</strong><p>${escapeHtml((error as Error).message)}</p></section>`;
      }
    }
  });
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>'"]/g, (ch) => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[ch] || ch));
}
