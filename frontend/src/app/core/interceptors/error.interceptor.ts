import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { EnvironmentInjector, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../services/auth.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  // Resolved on error, not when the chain is built. Constructing TranslateService loads the
  // fallback language over HttpClient, so eagerly injecting it here means the very first request
  // — which is that language load — re-enters this interceptor while TranslateService is still
  // in its own constructor. Angular throws NG0200, ngx-translate swallows it, and the app boots
  // with every translation key unresolved.
  const injector = inject(EnvironmentInjector);

  const show = (key: string) => {
    const translate = injector.get(TranslateService);
    injector.get(MatSnackBar).open(translate.instant(key), translate.instant('common.ok'), {
      duration: 5000,
      panelClass: 'snackbar-error',
    });
  };

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // Don't show snackbar for login failures (handled by login component)
      if (req.url.includes('/api/auth/login')) {
        return throwError(() => error);
      }

      if (error.status === 401) {
        // PRD-020: the token is already dead — clear locally (no doomed server logout
        // call) and never navigate/snackbar when already on the login page (loop guard).
        injector.get(AuthService).clearLocalSession();
        if (!router.url.startsWith('/login')) {
          router.navigate(['/login']);
          show('auth.sessionExpired');
        }
      } else if (error.status === 429) {
        show('auth.tooManyAttempts');
      } else if (error.status === 403) {
        const backendMessage = (error.error?.message ?? '').toLowerCase();
        const key = backendMessage.includes('member') ? 'auth.notProjectMember' : 'auth.forbidden';
        show(key);
      } else if (error.status >= 500) {
        show('errors.server');
      } else if (error.status === 0) {
        show('errors.network');
      }

      return throwError(() => error);
    })
  );
};
