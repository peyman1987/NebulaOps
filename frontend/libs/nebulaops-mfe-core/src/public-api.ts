import { Type, Provider } from '@angular/core';
import { HttpInterceptorFn, provideHttpClient, withInterceptors } from '@angular/common/http';
import { createApplication } from '@angular/platform-browser';
import { createCustomElement } from '@angular/elements';
import { provideAnimations } from '@angular/platform-browser/animations';

export const NEBULAOPS_JWT_KEY = 'nebulaops.v22_2.jwt';
export const NEBULAOPS_USER_KEY = 'nebulaops.v22_2.user';

export interface NebulaOpsMfeMetadata {
  tagName: string;
  title?: string;
  port?: number;
  gatewayBasePath?: string;
}

export interface NebulaOpsMfeBootstrapOptions<T> extends NebulaOpsMfeMetadata {
  component: Type<T>;
  providers?: Provider[];
  redefine?: boolean;
}

export function readNebulaOpsToken(): string {
  return localStorage.getItem(NEBULAOPS_JWT_KEY) ?? '';
}

export function readNebulaOpsUser(): string {
  return localStorage.getItem(NEBULAOPS_USER_KEY) ?? 'nebulaops-user';
}

export const nebulaOpsJwtInterceptor: HttpInterceptorFn = (req, next) => {
  const token = readNebulaOpsToken();
  if (token && shouldAttachAuthorization(req.url)) {
    return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
  }
  return next(req);
};

export function shouldAttachAuthorization(url: string): boolean {
  const lowered = url.toLowerCase();
  return !lowered.includes('/auth/') && !lowered.includes('/realms/') && !lowered.includes('/protocol/openid-connect/');
}

export function gatewayUrl(path: string): string {
  const normalized = path.startsWith('/') ? path : `/${path}`;
  return `/api${normalized}`;
}

export async function bootstrapNebulaOpsMfe<T>(options: NebulaOpsMfeBootstrapOptions<T>): Promise<void> {
  if (customElements.get(options.tagName) && !options.redefine) {
    return;
  }

  const app = await createApplication({
    providers: [
      provideAnimations(),
      provideHttpClient(withInterceptors([nebulaOpsJwtInterceptor])),
      ...(options.providers ?? []),
    ],
  });

  const element = createCustomElement(options.component, { injector: app.injector });
  customElements.define(options.tagName, element);
}
