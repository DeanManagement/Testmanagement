import { ChangeDetectorRef, Component, DestroyRef, inject, Input, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { take } from 'rxjs/operators';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { LocalizedDatePipe } from '../../shared/pipes/localized-date.pipe';
import { RequirementApiService } from '../../core/services/requirement-api.service';
import {
  RequirementSyncConfig,
  RequirementSyncResult,
  SaveRequirementSyncConfigRequest,
} from '../../shared/models/requirement.model';

/** What the server falls back to for blank state lists; shown as placeholders. */
export const DEFAULT_APPROVED_STATES = 'Approved, Committed, Active, Resolved, Closed, Done';
export const DEFAULT_OBSOLETE_STATES = 'Removed';
const EXAMPLE_QUERY = "SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject] = @project "
  + "AND [System.WorkItemType] IN ('Epic', 'Feature', 'User Story')";

/**
 * TM19: which Azure DevOps work items become requirements, and how their states map. Shown on the
 * issue-tracker settings page when the tracker is Azure DevOps; admin-only, like that page.
 */
@Component({
  selector: 'app-requirement-sync-settings',
  standalone: true,
  imports: [
    FormsModule,
    LocalizedDatePipe,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSlideToggleModule,
    TranslateModule,
  ],
  template: `
    <div class="tm-card form-card" data-test-id="requirement-sync-settings">
      <h2>{{ 'requirementSync.title' | translate }}</h2>
      <p class="setting-description">{{ 'requirementSync.description' | translate }}</p>

      @if (config?.lastError) {
        <p class="sync-error" data-test-id="requirement-sync-error">
          <mat-icon>error_outline</mat-icon> {{ config?.lastError }}
        </p>
      } @else if (config?.lastSyncedAt) {
        <p class="setting-description" data-test-id="requirement-sync-last">
          {{ 'requirementSync.lastSynced' | translate: { date: (config?.lastSyncedAt | localizedDate: 'medium') } }}
        </p>
      }

      <mat-slide-toggle [(ngModel)]="enabled" data-test-id="requirement-sync-enabled">
        {{ 'requirementSync.enabled' | translate }}
      </mat-slide-toggle>

      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'requirementSync.query' | translate }}</mat-label>
        <textarea matInput rows="4" [(ngModel)]="query" [placeholder]="exampleQuery"
                  data-test-id="requirement-sync-query"></textarea>
        <mat-hint>{{ 'requirementSync.queryHint' | translate }}</mat-hint>
      </mat-form-field>

      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'requirementSync.prefix' | translate }}</mat-label>
        <input matInput [(ngModel)]="externalIdPrefix" placeholder="AB#" maxlength="20"
               data-test-id="requirement-sync-prefix">
        <mat-hint>{{ 'requirementSync.prefixHint' | translate }}</mat-hint>
      </mat-form-field>

      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'requirementSync.approvedStates' | translate }}</mat-label>
        <input matInput [(ngModel)]="approvedStates" [placeholder]="defaultApproved"
               data-test-id="requirement-sync-approved">
        <mat-hint>{{ 'requirementSync.statesHint' | translate }}</mat-hint>
      </mat-form-field>

      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'requirementSync.obsoleteStates' | translate }}</mat-label>
        <input matInput [(ngModel)]="obsoleteStates" [placeholder]="defaultObsolete"
               data-test-id="requirement-sync-obsolete">
        <mat-hint>{{ 'requirementSync.mappingHint' | translate }}</mat-hint>
      </mat-form-field>

      <mat-slide-toggle [(ngModel)]="scheduled" data-test-id="requirement-sync-scheduled">
        {{ 'requirementSync.scheduled' | translate }}
      </mat-slide-toggle>
      <p class="setting-description">{{ 'requirementSync.scheduledHint' | translate }}</p>

      @if (preview) {
        <p data-test-id="requirement-sync-preview">{{ 'requirementSync.preview' | translate: preview }}</p>
      }

      <div class="form-actions">
        @if (config) {
          <button mat-stroked-button [disabled]="busy" (click)="dryRun()" data-test-id="requirement-sync-dry-run">
            <mat-icon>preview</mat-icon>
            {{ 'requirementSync.dryRun' | translate }}
          </button>
        }
        <button mat-flat-button [disabled]="!query.trim() || busy" (click)="save()" data-test-id="requirement-sync-save">
          {{ 'common.save' | translate }}
        </button>
      </div>
    </div>
  `,
  styles: [`
    .form-card { padding: 24px; margin-top: 24px; display: flex; flex-direction: column; }
    h2 { margin: 0 0 8px; }
    .full-width { width: 100%; }
    .setting-description { color: var(--tm-text-secondary); font-size: 13px; margin: 0 0 16px; }
    .sync-error { display: flex; align-items: center; gap: 8px; color: var(--tm-tint-danger-fg); word-break: break-word; }
    .form-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
    mat-slide-toggle { display: block; margin: 8px 0 16px; }
  `],
})
export class RequirementSyncSettingsComponent implements OnInit {
  private readonly api = inject(RequirementApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) projectId = '';

  readonly exampleQuery = EXAMPLE_QUERY;
  readonly defaultApproved = DEFAULT_APPROVED_STATES;
  readonly defaultObsolete = DEFAULT_OBSOLETE_STATES;

  config: RequirementSyncConfig | null = null;
  enabled = false;
  scheduled = false;
  query = '';
  externalIdPrefix = '';
  approvedStates = '';
  obsoleteStates = '';
  preview: RequirementSyncResult | null = null;
  busy = false;

  ngOnInit(): void {
    this.api.getSyncConfig(this.projectId)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (config) => this.apply(config ?? null),
        error: () => undefined,
      });
  }

  save(): void {
    const request: SaveRequirementSyncConfigRequest = {
      enabled: this.enabled,
      scheduled: this.scheduled,
      query: this.query.trim(),
      externalIdPrefix: this.externalIdPrefix.trim() || null,
      approvedStates: this.approvedStates.trim(),
      obsoleteStates: this.obsoleteStates.trim(),
    };
    this.busy = true;
    this.api.saveSyncConfig(this.projectId, request)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (config) => {
          this.busy = false;
          this.apply(config);
          this.snackBar.open(this.translate.instant('requirementSync.saved'),
            this.translate.instant('common.close'), { duration: 4000 });
        },
        error: () => {
          this.busy = false;
          this.cdr.markForCheck();
        },
      });
  }

  /** Counts what a sync with the saved settings would change; writes nothing. */
  dryRun(): void {
    this.busy = true;
    this.api.runSync(this.projectId, true)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.busy = false;
          this.preview = result;
          this.cdr.markForCheck();
        },
        error: () => {
          this.busy = false;
          this.cdr.markForCheck();
        },
      });
  }

  private apply(config: RequirementSyncConfig | null): void {
    this.config = config;
    if (config) {
      this.enabled = config.enabled;
      this.scheduled = config.scheduled;
      this.query = config.query;
      this.externalIdPrefix = config.externalIdPrefix ?? '';
      this.approvedStates = config.approvedStates;
      this.obsoleteStates = config.obsoleteStates;
    }
    this.cdr.markForCheck();
  }
}
