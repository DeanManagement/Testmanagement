import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { TranslateModule } from '@ngx-translate/core';
import { User } from '../../../shared/models/user.model';

@Component({
  selector: 'app-edit-user-dialog',
  standalone: true,
  imports: [
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    TranslateModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ 'settings.users.editTitle' | translate }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'settings.users.displayName' | translate }}</mat-label>
        <input matInput [(ngModel)]="displayName" required data-test-id="user-edit-name-input" />
      </mat-form-field>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'settings.users.newPassword' | translate }}</mat-label>
        <input matInput type="password" [(ngModel)]="password" data-test-id="user-edit-password-input" />
      </mat-form-field>
      <mat-checkbox [(ngModel)]="systemAdmin" data-test-id="user-edit-admin-checkbox">
        {{ 'settings.users.systemAdminLabel' | translate }}
      </mat-checkbox>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="onCancel()">{{ 'common.cancel' | translate }}</button>
      <button mat-flat-button color="primary"
              [disabled]="!displayName.trim()"
              (click)="onSave()" data-test-id="user-edit-confirm">
        {{ 'common.save' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width { width: 100%; }
    mat-dialog-content { display: flex; flex-direction: column; gap: 8px; min-width: min(90vw, 450px); }
  `],
})
export class EditUserDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<EditUserDialogComponent>);
  private readonly data: User = inject(MAT_DIALOG_DATA);

  displayName = this.data.displayName;
  systemAdmin = this.data.systemAdmin;
  password = '';

  onCancel(): void {
    this.dialogRef.close();
  }

  onSave(): void {
    const result: { displayName: string; systemAdmin: boolean; password?: string } = {
      displayName: this.displayName.trim(),
      systemAdmin: this.systemAdmin,
    };
    if (this.password.trim()) {
      result.password = this.password;
    }
    this.dialogRef.close(result);
  }
}
