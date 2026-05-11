import {bootstrapApplication} from '@angular/platform-browser';
import {provideAnimations} from '@angular/platform-browser/animations';
import {provideHttpClient, withInterceptors, HttpInterceptorFn} from '@angular/common/http';
import {AppComponent} from './app/app.component';
import {JWT_KEY} from './app/api.config';

/**
 * v23.4 — JWT Auth Interceptor.
 * Automatically adds Authorization: Bearer <token> to every HTTP request
 * when a JWT is present in localStorage. Skips /api/auth/login and /api/auth/register.
 */
const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem(JWT_KEY) || '';
  const isAuthEndpoint = req.url.includes('/auth/login') || req.url.includes('/auth/register');

  if (token && !isAuthEndpoint) {
    const authReq = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    });
    return next(authReq);
  }
  return next(req);
};

bootstrapApplication(AppComponent, {
  providers: [
    provideAnimations(),
    provideHttpClient(withInterceptors([jwtInterceptor]))
  ]
}).catch(err => console.error(err));
