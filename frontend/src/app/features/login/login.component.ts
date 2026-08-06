import { Component, DestroyRef, inject, OnInit, signal} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from '../../core/services/auth.service';
import { SsoApiService } from '../../core/services/sso-api.service';
import { AuthConfig } from '../../shared/models/sso.model';
import { selectIsAuthenticated, selectAuthLoading, selectAuthError } from '../../store/auth/auth.selectors';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    RouterLink,
    AsyncPipe,
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly ssoApi = inject(SsoApiService);

  loading$ = this.store.select(selectAuthLoading);
  error$ = this.store.select(selectAuthError);

  email = '';
  password = '';

  /**
   * Starts optimistic: local login is shown until the server says otherwise, so a slow or failed
   * config call leaves people with a usable form rather than a dead screen.
   */
  readonly authConfig = signal<AuthConfig>({ localLoginEnabled: true, providers: [] });

  ngOnInit(): void {
    this.store.select(selectIsAuthenticated).pipe(takeUntilDestroyed(this.destroyRef)).subscribe((isAuth) => {
      if (isAuth) {
        this.router.navigate(['/dashboard']);
      }
    });

    this.ssoApi.getAuthConfig().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (config) => this.authConfig.set(config),
      error: () => undefined,
    });
  }

  onLogin(): void {
    this.authService.login(this.email, this.password);
  }

  /**
   * A full page navigation, not an XHR: the authorization-code flow needs the browser itself to
   * follow redirects to the IdP and back.
   */
  signInWith(slug: string): void {
    window.location.href = `/oauth2/authorization/${encodeURIComponent(slug)}`;
  }
}
