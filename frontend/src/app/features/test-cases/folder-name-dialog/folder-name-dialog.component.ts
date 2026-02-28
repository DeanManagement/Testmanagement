import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';

export interface FolderNameDialogData {
  title: string;
  name: string;
}

@Component({
  selector: 'app-folder-name-dialog',
  standalone: true,
  imports: [
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    TranslateModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.title | translate }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'folder.nameLabel' | translate }}</mat-label>
        <input matInput [(ngModel)]="name" (keydown.enter)="submit()" cdkFocusInitial />
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'common.cancel' | translate }}</button>
      <button mat-flat-button [disabled]="!name.trim()" (click)="submit()">
        {{ 'common.save' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width { width: 100%; }
  `],
})
export class FolderNameDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<FolderNameDialogComponent>);
  readonly data: FolderNameDialogData = inject(MAT_DIALOG_DATA);

  name = this.data.name || '';

  submit(): void {
    if (this.name.trim()) {
      this.dialogRef.close(this.name.trim());
    }
  }
}
