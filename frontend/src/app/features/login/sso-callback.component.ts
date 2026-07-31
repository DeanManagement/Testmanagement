import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from '../../core/services/auth.service';
import { AuthActions } from '../../store/auth/auth.actions';

/**
 * Lands the browser after an SSO round trip (PRD-012 §3.3).
 *
 * <p>The backend puts the result in the URL <em>fragment</em> — `#token=…` or `#error=…` — because
 * a fragment is never sent to a server and so stays out of access logs and `Referer` headers. This
 * component reads it, wipes it from the address bar so it cannot be shoulder-surfed or restored
 * from history, and hands the token to {@link AuthService}.
 */
@Component({
  selector: 'app-sso-callback',
  standalone: true,
  imports: [MatProgressSpinnerModule, TranslateModule],
  template: `
    <div class="callback-page">
      <mat-spinner diameter="36"></mat-spinner>
      <p>{{ 'sso.completingSignIn' | translate }}</p>
    </div>
  `,
  styles: [`
    .callback-page {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 16px;
      min-height: 100vh;
      background: var(--tm-bg);
      color: var(--tm-text-secondary);
    }
  `],
})
export class SsoCallbackComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly store = inject(Store);
  private readonly authService = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    const fragment = window.location.hash.startsWith('#') ? window.location.hash.slice(1) : '';
    const params = new URLSearchParams(fragment);
    const token = params.get('token');
    const error = params.get('error');

    // Clear before doing anything else: the token must not survive in the address bar or history.
    history.replaceState(null, '', window.location.pathname);

    if (token) {
      this.authService.adoptSsoToken(token);
      return;
    }

    this.store.dispatch(AuthActions.loginFailure({
      error: error || 'Single sign-on failed. Please try again.',
    }));
    this.router.navigate(['/login']);
  }
}
