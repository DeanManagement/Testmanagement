import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';

export interface SavedFilterDialogData {
  titleKey: string;
  name: string;
  shared: boolean;
}

export interface SavedFilterDialogResult {
  name: string;
  shared: boolean;
}

/** Name and sharing of a saved filter, for saving a new one or editing one (PRD-053 §3). */
@Component({
  selector: 'app-saved-filter-dialog',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatCheckboxModule, MatDialogModule, MatFormFieldModule, MatInputModule,
    TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ data.titleKey | translate }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'savedFilters.name' | translate }}</mat-label>
        <input matInput [(ngModel)]="name" maxlength="255" (keydown.enter)="submit()" cdkFocusInitial
               data-test-id="saved-filter-name" />
      </mat-form-field>
      <mat-checkbox [(ngModel)]="shared" data-test-id="saved-filter-shared">
        {{ 'savedFilters.shareWithProject' | translate }}
      </mat-checkbox>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'common.cancel' | translate }}</button>
      <button mat-flat-button [disabled]="!name.trim()" (click)="submit()" data-test-id="saved-filter-submit">
        {{ 'common.save' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width { width: 100%; }
    mat-dialog-content { min-width: min(90vw, 350px); }
  `],
})
export class SavedFilterDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<SavedFilterDialogComponent, SavedFilterDialogResult>);
  readonly data: SavedFilterDialogData = inject(MAT_DIALOG_DATA);

  name = this.data.name;
  shared = this.data.shared;

  submit(): void {
    if (this.name.trim()) {
      this.dialogRef.close({ name: this.name.trim(), shared: this.shared });
    }
  }
}
