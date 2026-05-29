import { createApplication }        from '@angular/platform-browser';
import { createCustomElement }       from '@angular/elements';
import { provideHttpClient, withInterceptors, HttpInterceptorFn } from '@angular/common/http';
import { provideAnimations }         from '@angular/platform-browser/animations';
import { AppComponent }              from './app/app.component';

const JWT_KEY = 'nebulaops.v24_1.jwt';

const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem(JWT_KEY) ?? '';
  if (token && !req.url.includes('/auth/')) {
    return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
  }
  return next(req);
};

(async () => {
  await (window as any).__NEBULAOPS_AUTH_READY__?.catch?.(() => undefined);
  const app = await createApplication({
    providers: [
      provideAnimations(),
      provideHttpClient(withInterceptors([jwtInterceptor])),
    ],
  });
  const el = createCustomElement(AppComponent, { injector: app.injector });
  customElements.define('nebulaops-mfe-operational-issues', el);
})();
