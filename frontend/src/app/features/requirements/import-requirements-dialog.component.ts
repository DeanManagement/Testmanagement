import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { take } from 'rxjs/operators';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { RequirementApiService } from '../../core/services/requirement-api.service';
import { ImportResult } from '../../shared/models/test-case.model';

/**
 * TM19: requirements from CSV, upserted by externalId. "Preview" is a dry run that reports the
 * same created / updated / skipped rows a real import would; closes with the real import's result.
 */
@Component({
  selector: 'app-import-requirements-dialog',
  standalone: true,
  imports: [MatButtonModule, MatDialogModule, MatProgressSpinnerModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'requirement.import.title' | translate }}</h2>
    <mat-dialog-content>
      <p class="hint">{{ 'requirement.import.hint' | translate }}</p>
      <code class="columns">externalId,title,description,parentExternalId,status,priority,cf:&lt;field&gt;</code>
      <input type="file" accept=".csv" (change)="onFileSelected($event)" data-test-id="requirement-import-file" />
      @if (loading()) {
        <mat-spinner diameter="32" />
      }
      @if (result(); as preview) {
        <p class="summary" data-test-id="requirement-import-summary">
          {{ 'import.created' | translate }}: {{ preview.imported }} ·
          {{ 'import.updated' | translate }}: {{ preview.updated }} ·
          {{ 'import.skipped' | translate }}: {{ preview.skipped }}
          @if (preview.dryRun) { · {{ 'import.dryRunBadge' | translate }} }
        </p>
        @if (preview.errors.length) {
          <ul class="errors" data-test-id="requirement-import-errors">
            @for (error of preview.errors; track $index) {
              <li>{{ 'import.row' | translate }} {{ error.row }}: {{ error.message }}</li>
            }
          </ul>
        }
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'common.cancel' | translate }}</button>
      <button mat-stroked-button [disabled]="!file || loading()" (click)="run(true)"
              data-test-id="requirement-import-preview">{{ 'import.preview' | translate }}</button>
      <button mat-flat-button [disabled]="!file || loading()" (click)="run(false)"
              data-test-id="requirement-import-confirm">{{ 'import.confirm' | translate }}</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .hint { margin-top: 0; }
    .columns { display: block; margin-bottom: 12px; font-size: 12px; overflow-wrap: anywhere; }
    .summary { font-weight: 500; }
    .errors { max-height: 240px; overflow: auto; font-size: 13px; color: var(--tm-tint-danger-fg); }
  `],
})
export class ImportRequirementsDialogComponent {
  private readonly api = inject(RequirementApiService);
  private readonly dialogRef = inject(MatDialogRef<ImportRequirementsDialogComponent>);
  private readonly destroyRef = inject(DestroyRef);
  private readonly projectId: string = inject(MAT_DIALOG_DATA).projectId;

  file: File | null = null;
  readonly loading = signal(false);
  readonly result = signal<ImportResult | null>(null);

  onFileSelected(event: Event): void {
    this.file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.result.set(null);
  }

  run(dryRun: boolean): void {
    if (!this.file) return;
    this.loading.set(true);
    this.api.importCsv(this.projectId, this.file, dryRun).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.loading.set(false);
          if (dryRun) {
            this.result.set(result);
          } else {
            this.dialogRef.close(result);
          }
        },
        error: () => this.loading.set(false),
      });
  }
}
