import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { debounceTime, map, switchMap } from 'rxjs/operators';
import { BugReportApiService } from '../../../core/services/bug-report-api.service';
import {
  ALL_BUG_RESOLUTIONS,
  BugReport,
  BugReportStatus,
  BugResolution,
  ChangeBugStatusRequest,
  takesResolution,
} from '../../../shared/models/bug-report.model';

const SEARCH_DEBOUNCE_MS = 250;
const DUPLICATE_SUGGESTIONS = 10;

export interface ChangeBugStatusDialogData {
  projectId: string;
  newStatus: BugReportStatus;
  /** Absent for a bulk change, where the bugs may start from different statuses. */
  currentStatus?: BugReportStatus;
  /** The bugs being changed, so none is offered as its own duplicate. */
  bugIds: string[];
}

/**
 * The one place a status changes (PRD-045 §1): a reason always, a resolution when closing (optional
 * when resolving), and the original bug for DUPLICATE. Closes with the request, or nothing.
 */
@Component({
  selector: 'app-change-bug-status-dialog',
  standalone: true,
  imports: [
    FormsModule,
    MatAutocompleteModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    TranslateModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ 'bugReport.statusChange.title' | translate }}</h2>
    <mat-dialog-content>
      <p class="status-transition" data-test-id="status-transition">
        @if (data.currentStatus) {
          {{ 'bugReport.status.' + data.currentStatus | translate }} &rarr;
        }
        {{ 'bugReport.status.' + data.newStatus | translate }}
      </p>
      @if (showsResolution) {
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>{{ 'bugReport.resolution.label' | translate }}</mat-label>
          <mat-select [(ngModel)]="resolution" data-test-id="status-resolution">
            @if (!requiresResolution) {
              <mat-option [value]="null">{{ 'bugReport.resolution.none' | translate }}</mat-option>
            }
            @for (r of resolutions; track r) {
              <mat-option [value]="r">{{ 'bugReport.resolution.' + r | translate }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      }
      @if (resolution === 'DUPLICATE') {
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>{{ 'bugReport.resolution.duplicateOf' | translate }}</mat-label>
          <input matInput [ngModel]="duplicateSearch" (ngModelChange)="onDuplicateSearch($event)"
                 [matAutocomplete]="duplicates" data-test-id="status-duplicate-of" />
          <mat-autocomplete #duplicates="matAutocomplete" (optionSelected)="duplicateOf = $event.option.value"
                            [displayWith]="bugLabel">
            @for (bug of suggestions(); track bug.id) {
              <mat-option [value]="bug">{{ bugLabel(bug) }}</mat-option>
            }
          </mat-autocomplete>
        </mat-form-field>
      }
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'bugReport.statusChange.reason' | translate }}</mat-label>
        <textarea matInput [(ngModel)]="reason" required rows="3" data-test-id="status-reason"></textarea>
        @if (!reason.trim()) {
          <mat-hint>{{ 'bugReport.statusChange.reasonRequired' | translate }}</mat-hint>
        }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="onCancel()">{{ 'bugReport.statusChange.cancel' | translate }}</button>
      <button mat-flat-button color="primary" [disabled]="!canConfirm" (click)="onConfirm()" data-test-id="status-confirm">
        {{ 'bugReport.statusChange.confirm' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width { width: 100%; }
    mat-dialog-content { display: flex; flex-direction: column; gap: 8px; min-width: min(90vw, 400px); }
    .status-transition { font-weight: 500; margin: 0 0 8px; }
  `],
})
export class ChangeBugStatusDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<ChangeBugStatusDialogComponent>);
  private readonly api = inject(BugReportApiService);
  readonly data: ChangeBugStatusDialogData = inject(MAT_DIALOG_DATA);

  readonly resolutions = ALL_BUG_RESOLUTIONS;
  readonly showsResolution = takesResolution(this.data.newStatus);
  readonly requiresResolution = this.data.newStatus === 'CLOSED';

  reason = '';
  resolution: BugResolution | null = null;
  duplicateSearch = '';
  duplicateOf: BugReport | null = null;
  readonly suggestions = signal<BugReport[]>([]);

  private readonly searchTerms = new Subject<string>();

  constructor() {
    this.searchTerms.pipe(
      debounceTime(SEARCH_DEBOUNCE_MS),
      switchMap((q) => this.api.getAll(this.data.projectId, { q, size: DUPLICATE_SUGGESTIONS })),
      map((page) => page.content.filter((bug) => !this.data.bugIds.includes(bug.id))),
      takeUntilDestroyed(inject(DestroyRef)),
    ).subscribe((bugs) => this.suggestions.set(bugs));
  }

  get canConfirm(): boolean {
    if (!this.reason.trim()) return false;
    if (this.requiresResolution && !this.resolution) return false;
    return this.resolution !== 'DUPLICATE' || !!this.duplicateOf;
  }

  bugLabel(bug: BugReport | string | null): string {
    if (!bug || typeof bug === 'string') return bug ?? '';
    return `${bug.key} ${bug.title}`;
  }

  onDuplicateSearch(term: string | BugReport): void {
    if (typeof term !== 'string') return;
    this.duplicateSearch = term;
    this.duplicateOf = null;
    this.searchTerms.next(term.trim());
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onConfirm(): void {
    const request: ChangeBugStatusRequest = {
      status: this.data.newStatus,
      reason: this.reason.trim(),
      resolution: this.showsResolution ? this.resolution : null,
      duplicateOfId: this.resolution === 'DUPLICATE' ? this.duplicateOf?.id ?? null : null,
    };
    this.dialogRef.close(request);
  }
}
