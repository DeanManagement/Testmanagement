import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { take } from 'rxjs/operators';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { LocalizedDatePipe } from '../../shared/pipes/localized-date.pipe';
import { SsoApiService } from '../../core/services/sso-api.service';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { SaveSsoProviderRequest, SsoProtocol, SsoProvider } from '../../shared/models/sso.model';

/**
 * The scopes each protocol cannot work without. The backend adds these regardless; prefilling them
 * means an admin sees what will be requested rather than discovering it after the fact.
 */
const DEFAULT_SCOPES: Record<SsoProtocol, string> = {
  OIDC: 'openid,profile,email',
  GITHUB: 'read:user,user:email',
};

/**
 * System-admin screen for single sign-on providers (PRD-012 §3.3).
 *
 * <p>Two fields here are security switches rather than preferences, and the form labels them as
 * such: trusting a provider's email for account linking, and disabling password sign-in.
 */
@Component({
  selector: 'app-sso-settings',
  standalone: true,
  imports: [
    RouterLink,
    FormsModule,
    LocalizedDatePipe,
    MatButtonModule,
    MatIconModule,
    MatSlideToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    TranslateModule,
  ],
  templateUrl: './sso-settings.component.html',
  styleUrl: './sso-settings.component.scss',
})
export class SsoSettingsComponent implements OnInit {
  private readonly api = inject(SsoApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  providers: SsoProvider[] = [];
  localLoginEnabled = true;
  loading = true;
  saving = false;
  testingId: string | null = null;

  formOpen = false;
  editingId: string | null = null;
  editingSecretSet = false;

  formSlug = '';
  formDisplayName = '';
  formProtocol: SsoProtocol = 'OIDC';
  formIssuerUri = '';
  formClientId = '';
  formClientSecret = '';
  formScopes = DEFAULT_SCOPES.OIDC;
  formEmailClaim = 'email';
  formNameClaim = 'name';
  formAdminClaim = '';
  formAdminClaimValue = '';
  formTrustEmail = false;
  formAutoProvision = true;
  formActive = true;

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading = true;
    this.api.getProviders().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (providers) => {
        this.providers = providers;
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.loading = false;
        this.cdr.markForCheck();
      },
    });
    this.api.getSettings().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (settings) => {
        this.localLoginEnabled = settings.localLoginEnabled;
        this.cdr.markForCheck();
      },
      error: () => undefined,
    });
  }

  get hasActiveProvider(): boolean {
    return this.providers.some((p) => p.active);
  }

  get isGitHub(): boolean {
    return this.formProtocol === 'GITHUB';
  }

  /**
   * Swaps the prefilled scopes when the protocol changes. Only while creating: the protocol of a
   * saved provider is fixed, because an OIDC subject and a GitHub user id are not the same kind of
   * identifier and reinterpreting stored identities under the other one would mislink accounts.
   */
  onProtocolChange(protocol: SsoProtocol): void {
    this.formProtocol = protocol;
    this.formScopes = DEFAULT_SCOPES[protocol];
    if (protocol === 'GITHUB') {
      this.formAdminClaim = '';
      this.formAdminClaimValue = '';
    }
  }

  get canSave(): boolean {
    if (!this.formSlug.trim() || !this.formDisplayName.trim()
      || !this.formIssuerUri.trim() || !this.formClientId.trim()) {
      return false;
    }
    // A secret is only mandatory the first time; later saves may keep the stored one.
    return this.editingId !== null || this.formClientSecret.trim().length > 0;
  }

  openCreate(): void {
    this.formOpen = true;
    this.editingId = null;
    this.editingSecretSet = false;
    this.formSlug = '';
    this.formDisplayName = '';
    this.formProtocol = 'OIDC';
    this.formIssuerUri = '';
    this.formClientId = '';
    this.formClientSecret = '';
    this.formScopes = DEFAULT_SCOPES.OIDC;
    this.formEmailClaim = 'email';
    this.formNameClaim = 'name';
    this.formAdminClaim = '';
    this.formAdminClaimValue = '';
    this.formTrustEmail = false;
    this.formAutoProvision = true;
    this.formActive = true;
  }

  openEdit(provider: SsoProvider): void {
    this.formOpen = true;
    this.editingId = provider.id;
    this.editingSecretSet = provider.secretSet;
    this.formSlug = provider.slug;
    this.formDisplayName = provider.displayName;
    this.formProtocol = provider.protocol;
    this.formIssuerUri = provider.issuerUri;
    this.formClientId = provider.clientId;
    // Never prefilled: the API does not return it, and a placeholder would be saved back verbatim.
    this.formClientSecret = '';
    this.formScopes = provider.scopes;
    this.formEmailClaim = provider.emailClaim;
    this.formNameClaim = provider.nameClaim;
    this.formAdminClaim = provider.adminClaim ?? '';
    this.formAdminClaimValue = provider.adminClaimValue ?? '';
    this.formTrustEmail = provider.trustEmailForLinking;
    this.formAutoProvision = provider.autoProvision;
    this.formActive = provider.active;
  }

  closeForm(): void {
    this.formOpen = false;
    this.editingId = null;
    this.formClientSecret = '';
  }

  save(): void {
    if (!this.canSave || this.saving) {
      return;
    }
    this.saving = true;

    const request: SaveSsoProviderRequest = {
      slug: this.formSlug.trim(),
      displayName: this.formDisplayName.trim(),
      protocol: this.formProtocol,
      issuerUri: this.formIssuerUri.trim(),
      clientId: this.formClientId.trim(),
      scopes: this.formScopes.trim(),
      emailClaim: this.formEmailClaim.trim(),
      nameClaim: this.formNameClaim.trim(),
      // GitHub has no claims to match, and the backend rejects an admin claim on it outright.
      adminClaim: this.isGitHub ? undefined : this.formAdminClaim.trim() || undefined,
      adminClaimValue: this.isGitHub ? undefined : this.formAdminClaimValue.trim() || undefined,
      trustEmailForLinking: this.formTrustEmail,
      autoProvision: this.formAutoProvision,
      active: this.formActive,
    };
    if (this.formClientSecret.trim()) {
      request.clientSecret = this.formClientSecret.trim();
    }

    const call = this.editingId
      ? this.api.updateProvider(this.editingId, request)
      : this.api.createProvider(request);

    call.pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving = false;
        this.closeForm();
        this.notify('sso.saved');
        this.load();
      },
      error: () => {
        this.saving = false;
        this.cdr.markForCheck();
      },
    });
  }

  test(provider: SsoProvider): void {
    this.testingId = provider.id;
    this.api.testProvider(provider.id).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.testingId = null;
        this.notify('sso.testSucceeded');
        this.load();
      },
      error: () => {
        this.testingId = null;
        // The interceptor surfaces the message; reload to pick up the recorded lastError.
        this.load();
      },
    });
  }

  remove(provider: SsoProvider): void {
    const data: ConfirmDialogData = {
      titleKey: 'sso.deleteTitle',
      messageKey: 'sso.deleteMessage',
      messageParams: { name: provider.displayName },
      secondaryMessageKey: 'sso.deleteLosesAccess',
      danger: true,
    };
    this.dialog.open(ConfirmDialogComponent, { data })
      .afterClosed()
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.api.deleteProvider(provider.id)
          .pipe(take(1), takeUntilDestroyed(this.destroyRef))
          .subscribe(() => {
            this.notify('sso.deleted');
            this.load();
          });
      });
  }

  toggleLocalLogin(enabled: boolean): void {
    this.api.updateSettings(enabled).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (settings) => {
        this.localLoginEnabled = settings.localLoginEnabled;
        this.notify(enabled ? 'sso.localLoginEnabled' : 'sso.localLoginDisabled');
        this.cdr.markForCheck();
      },
      error: () => {
        // Rejected server-side (e.g. no active provider); put the toggle back.
        this.localLoginEnabled = !enabled;
        this.cdr.markForCheck();
      },
    });
  }

  /** The URL an admin has to register at their IdP. Shown so it cannot be mistyped. */
  callbackUrl(slug: string): string {
    return `${window.location.origin}/login/oauth2/code/${slug || '<slug>'}`;
  }

  private notify(key: string): void {
    this.snackBar.open(this.translate.instant(key), this.translate.instant('common.close'), {
      duration: 4000,
    });
  }
}
