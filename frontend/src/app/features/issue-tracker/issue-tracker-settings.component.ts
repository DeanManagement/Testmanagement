import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { take } from 'rxjs/operators';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { LocalizedDatePipe } from '../../shared/pipes/localized-date.pipe';
import { IssueTrackerApiService } from '../../core/services/issue-tracker-api.service';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../shared/components/confirm-dialog/confirm-dialog.component';
import {
  IssueTrackerConfig,
  IssueTrackerProviderType,
  PROJECT_REF_HINT,
  SaveIssueTrackerConfigRequest,
} from '../../shared/models/issue-tracker.model';

/**
 * Project-level issue-tracker configuration (PRD-010 §3.5). Admin-only; the route is reachable from
 * the project settings card.
 *
 * <p>The stored API token is never returned by the API, so the field always starts empty and an
 * empty value on save means "keep the existing token" rather than "clear it".
 */
@Component({
  selector: 'app-issue-tracker-settings',
  standalone: true,
  imports: [
    RouterLink,
    FormsModule,
    LocalizedDatePipe,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatSlideToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './issue-tracker-settings.component.html',
  styleUrl: './issue-tracker-settings.component.scss',
})
export class IssueTrackerSettingsComponent implements OnInit {
  private readonly api = inject(IssueTrackerApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  projectId = '';
  config: IssueTrackerConfig | null = null;
  providers: IssueTrackerProviderType[] = [];
  loading = true;
  saving = false;
  testing = false;

  formProvider: IssueTrackerProviderType = 'GITLAB';
  formBaseUrl = '';
  formProjectRef = '';
  formToken = '';
  formActive = true;

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('id') ?? '';
    if (!this.projectId) {
      this.loading = false;
      return;
    }

    this.api.getSupportedProviders(this.projectId)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (providers) => {
          this.providers = providers;
          if (providers.length > 0 && !providers.includes(this.formProvider)) {
            this.formProvider = providers[0];
          }
          this.cdr.markForCheck();
        },
        error: () => undefined,
      });

    this.load();
  }

  private load(): void {
    this.loading = true;
    this.api.getConfig(this.projectId)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (config) => {
          this.config = config ?? null;
          if (this.config) {
            this.formProvider = this.config.provider;
            this.formBaseUrl = this.config.baseUrl;
            this.formProjectRef = this.config.projectRef;
            this.formActive = this.config.active;
          }
          // Never prefill: the API does not return the token, and a placeholder here would be
          // saved back verbatim.
          this.formToken = '';
          this.loading = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.loading = false;
          this.cdr.markForCheck();
        },
      });
  }

  get projectRefHint(): string {
    return PROJECT_REF_HINT[this.formProvider];
  }

  get canSave(): boolean {
    if (!this.formBaseUrl.trim() || !this.formProjectRef.trim()) {
      return false;
    }
    // A token is only mandatory the first time; later saves may keep the stored one.
    return this.config !== null || this.formToken.trim().length > 0;
  }

  save(): void {
    if (!this.canSave || this.saving) {
      return;
    }
    this.saving = true;

    const request: SaveIssueTrackerConfigRequest = {
      provider: this.formProvider,
      baseUrl: this.formBaseUrl.trim(),
      projectRef: this.formProjectRef.trim(),
      active: this.formActive,
    };
    if (this.formToken.trim()) {
      request.apiToken = this.formToken.trim();
    }

    this.api.saveConfig(this.projectId, request)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (config) => {
          this.config = config;
          this.formToken = '';
          this.saving = false;
          this.notify('issueTracker.saved');
          this.cdr.markForCheck();
        },
        error: () => {
          this.saving = false;
          this.cdr.markForCheck();
        },
      });
  }

  testConnection(): void {
    if (this.testing) {
      return;
    }
    this.testing = true;
    this.api.testConnection(this.projectId)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.testing = false;
          this.notify('issueTracker.testSucceeded');
          // The check clears or sets lastError server-side, so re-read to show it.
          this.load();
        },
        error: () => {
          this.testing = false;
          // The interceptor surfaces the upstream message; refresh to pick up lastError.
          this.load();
        },
      });
  }

  remove(): void {
    const data: ConfirmDialogData = {
      titleKey: 'issueTracker.deleteTitle',
      messageKey: 'issueTracker.deleteMessage',
      // Existing links survive deletion, which is worth stating before they click.
      secondaryMessageKey: 'issueTracker.deleteKeepsLinks',
      danger: true,
    };
    this.dialog.open(ConfirmDialogComponent, { data })
      .afterClosed()
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.api.deleteConfig(this.projectId)
          .pipe(take(1), takeUntilDestroyed(this.destroyRef))
          .subscribe(() => {
            this.config = null;
            this.formToken = '';
            this.notify('issueTracker.deleted');
            this.cdr.markForCheck();
          });
      });
  }

  private notify(key: string): void {
    this.snackBar.open(this.translate.instant(key), this.translate.instant('common.close'), {
      duration: 4000,
    });
  }
}
