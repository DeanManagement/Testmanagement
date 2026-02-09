import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Store } from '@ngrx/store';
import { AuthActions } from '../../store/auth/auth.actions';
import { AuthUser } from '../models/auth.model';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly store = inject(Store);
  private accessToken: string | null = null;

  checkAuth(): void {
    if (!environment.auth.enabled) {
      this.store.dispatch(
        AuthActions.loginSuccess({
          user: {
            id: 'dev-user',
            username: 'developer',
            email: 'dev@localhost',
            displayName: 'Developer',
          },
        })
      );
      return;
    }

    this.http.get<AuthUser>('/api/auth/me').subscribe({
      next: (user) => this.store.dispatch(AuthActions.loginSuccess({ user })),
      error: () => {
        // Not authenticated — stay on public pages
      },
    });
  }

  login(): void {
    if (!environment.auth.enabled) {
      this.store.dispatch(
        AuthActions.loginSuccess({
          user: {
            id: 'dev-user',
            username: 'developer',
            email: 'dev@localhost',
            displayName: 'Developer',
          },
        })
      );
      return;
    }

    window.location.href = '/api/auth/login';
  }

  logout(): void {
    if (!environment.auth.enabled) {
      this.store.dispatch(AuthActions.logoutSuccess());
      return;
    }

    this.http.post('/api/auth/logout', {}).subscribe({
      next: () => this.store.dispatch(AuthActions.logoutSuccess()),
      error: () => this.store.dispatch(AuthActions.logoutSuccess()),
    });
  }

  getAccessToken(): string | null {
    return this.accessToken;
  }

  isAuthEnabled(): boolean {
    return environment.auth.enabled;
  }
}
