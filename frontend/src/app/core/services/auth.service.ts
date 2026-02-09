import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Store } from '@ngrx/store';
import { AuthActions } from '../../store/auth/auth.actions';
import { AuthUser, LoginResponse } from '../models/auth.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly store = inject(Store);
  private readonly TOKEN_KEY = 'auth_token';

  checkAuth(): void {
    const token = localStorage.getItem(this.TOKEN_KEY);
    if (!token) {
      return;
    }

    this.http.get<AuthUser>('/api/auth/me').subscribe({
      next: (user) => this.store.dispatch(AuthActions.loginSuccess({ user })),
      error: () => {
        localStorage.removeItem(this.TOKEN_KEY);
      },
    });
  }

  login(email: string, password: string): void {
    this.store.dispatch(AuthActions.login());
    this.http.post<LoginResponse>('/api/auth/login', { email, password }).subscribe({
      next: (response) => {
        localStorage.setItem(this.TOKEN_KEY, response.token);
        this.store.dispatch(AuthActions.loginSuccess({ user: response.user }));
      },
      error: (err) => {
        this.store.dispatch(
          AuthActions.loginFailure({ error: err.error?.message || 'Invalid email or password' })
        );
      },
    });
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    this.store.dispatch(AuthActions.logoutSuccess());
  }

  getAccessToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }
}
