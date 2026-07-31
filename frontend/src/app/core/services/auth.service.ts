import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { Observable, tap } from 'rxjs';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';
import { AuthActions } from '../../store/auth/auth.actions';
import { AuthUser, LoginResponse } from '../models/auth.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly store = inject(Store);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly TOKEN_KEY = 'auth_token';

  private expiryTimer: ReturnType<typeof setTimeout> | null = null;

  checkAuth(): void {
    const token = localStorage.getItem(this.TOKEN_KEY);
    if (!token) {
      this.store.dispatch(AuthActions.authInitialized());
      return;
    }

    // PRD-020: a token that is already expired is dead weight — clear it instead of
    // firing a doomed request and flashing an error.
    if (this.isExpired(token)) {
      localStorage.removeItem(this.TOKEN_KEY);
      this.store.dispatch(AuthActions.authInitialized());
      return;
    }

    this.scheduleExpiryLogout(token);
    this.http.get<AuthUser>('/api/auth/me').subscribe({
      next: (user) => this.store.dispatch(AuthActions.loginSuccess({ user })),
      error: () => {
        localStorage.removeItem(this.TOKEN_KEY);
        this.store.dispatch(AuthActions.authInitialized());
      },
    });
  }

  login(email: string, password: string): void {
    this.store.dispatch(AuthActions.login());
    this.http.post<LoginResponse>('/api/auth/login', { email, password }).subscribe({
      next: (response) => {
        localStorage.setItem(this.TOKEN_KEY, response.token);
        this.scheduleExpiryLogout(response.token);
        this.store.dispatch(AuthActions.loginSuccess({ user: response.user }));
        if (response.forcePasswordChange) {
          this.router.navigate(['/change-password']);
        }
      },
      error: (err) => {
        this.store.dispatch(
          AuthActions.loginFailure({ error: err.error?.message || 'Invalid email or password' })
        );
      },
    });
  }

  /**
   * PRD-020: the backend rotates token_version on password change (old tokens die) and
   * returns a fresh token so the current session continues — store it.
   */
  changePassword(currentPassword: string, newPassword: string): Observable<{ token: string }> {
    return this.http
      .post<{ token: string }>('/api/auth/change-password', { currentPassword, newPassword })
      .pipe(
        tap((response) => {
          localStorage.setItem(this.TOKEN_KEY, response.token);
          this.scheduleExpiryLogout(response.token);
        })
      );
  }

  /**
   * PRD-020: real logout — tells the backend to invalidate all outstanding tokens
   * (best effort), then clears local state either way.
   */
  logout(): void {
    const hadToken = !!localStorage.getItem(this.TOKEN_KEY);
    if (hadToken) {
      this.http.post<void>('/api/auth/logout', {}).subscribe({
        next: () => undefined,
        error: () => undefined, // local logout proceeds regardless
      });
    }
    this.clearLocalSession();
  }

  /** Clears local state only — used by the 401 interceptor where the token is already dead. */
  clearLocalSession(): void {
    if (this.expiryTimer) {
      clearTimeout(this.expiryTimer);
      this.expiryTimer = null;
    }
    localStorage.removeItem(this.TOKEN_KEY);
    this.store.dispatch(AuthActions.logoutSuccess());
  }

  getAccessToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  private tokenExpiryMs(token: string): number | null {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return typeof payload.exp === 'number' ? payload.exp * 1000 : null;
    } catch {
      return null;
    }
  }

  private isExpired(token: string): boolean {
    const exp = this.tokenExpiryMs(token);
    return exp !== null && exp <= Date.now();
  }

  /** Auto-logout shortly before the token expires, with a heads-up snackbar (PRD-020). */
  private scheduleExpiryLogout(token: string): void {
    if (this.expiryTimer) {
      clearTimeout(this.expiryTimer);
      this.expiryTimer = null;
    }
    const exp = this.tokenExpiryMs(token);
    if (exp === null) {
      return;
    }
    const fireIn = Math.max(0, exp - Date.now() - 60_000); // 1 min before expiry
    this.expiryTimer = setTimeout(() => {
      this.snackBar.open(
        this.translate.instant('auth.sessionExpiring'),
        this.translate.instant('common.ok'),
        { duration: 8000 }
      );
      this.clearLocalSession();
      this.router.navigate(['/login']);
    }, fireIn);
  }
}
