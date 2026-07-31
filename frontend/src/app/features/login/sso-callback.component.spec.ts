import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { provideStore } from '@ngrx/store';
import { provideTranslateService } from '@ngx-translate/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SsoCallbackComponent } from './sso-callback.component';
import { authReducer } from '../../store/auth/auth.reducer';

/**
 * The callback carries a session token in the URL fragment, so the assertions that matter are that
 * it is consumed, wiped from the address bar, and that a failure never leaves a stale token behind.
 */
describe('SsoCallbackComponent', () => {

  let http: HttpTestingController;
  let router: Router;

  /** A JWT that expires well in the future; only the exp claim is read client-side. */
  function token(expSecondsFromNow: number): string {
    const payload = { sub: 'user-1', exp: Math.floor(Date.now() / 1000) + expSecondsFromNow };
    const encode = (o: object) => btoa(JSON.stringify(o)).replace(/=/g, '');
    return `${encode({ alg: 'HS256' })}.${encode(payload)}.signature`;
  }

  function renderWithFragment(fragment: string) {
    window.history.replaceState(null, '', `/login/callback${fragment}`);
    const fixture = TestBed.createComponent(SsoCallbackComponent);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(async () => {
    localStorage.removeItem('auth_token');
    await TestBed.configureTestingModule({
      imports: [SsoCallbackComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideStore({ auth: authReducer }),
        provideTranslateService(),
        provideAnimationsAsync(),
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    localStorage.removeItem('auth_token');
    window.history.replaceState(null, '', '/');
    vi.restoreAllMocks();
  });

  it('stores a valid token and loads the user', () => {
    const jwt = token(3600);
    renderWithFragment(`#token=${encodeURIComponent(jwt)}`);

    expect(localStorage.getItem('auth_token')).toBe(jwt);
    http.expectOne('/api/auth/me').flush({ id: 'user-1', email: 'a@b.c', displayName: 'A', systemAdmin: false });
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('wipes the fragment so the token does not linger in the address bar', () => {
    renderWithFragment(`#token=${encodeURIComponent(token(3600))}`);

    expect(window.location.hash).toBe('');
    http.expectOne('/api/auth/me').flush({ id: 'u', email: 'a@b.c', displayName: 'A', systemAdmin: false });
  });

  it('clears the fragment even when the callback carries an error', () => {
    renderWithFragment('#error=Something%20went%20wrong');

    expect(window.location.hash).toBe('');
    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('rejects an already-expired token without storing it', () => {
    renderWithFragment(`#token=${encodeURIComponent(token(-60))}`);

    expect(localStorage.getItem('auth_token')).toBeNull();
    http.expectNone('/api/auth/me');
  });

  it('drops the token when the identity lookup fails', () => {
    renderWithFragment(`#token=${encodeURIComponent(token(3600))}`);

    http.expectOne('/api/auth/me').error(new ProgressEvent('unauthorized'), { status: 401 });

    // A token the server will not honour must not be left behind for the next page load.
    expect(localStorage.getItem('auth_token')).toBeNull();
  });

  it('sends the user back to login when the fragment is empty', () => {
    renderWithFragment('');

    expect(localStorage.getItem('auth_token')).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });
});
