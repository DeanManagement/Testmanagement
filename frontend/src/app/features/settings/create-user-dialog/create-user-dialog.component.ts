import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-create-user-dialog',
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
    <h2 mat-dialog-title id="create-user-dialog-title">{{ 'settings.users.createTitle' | translate }}</h2>
    <mat-dialog-content role="dialog" aria-labelledby="create-user-dialog-title">
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'auth.email' | translate }}</mat-label>
        <input matInput type="email" [(ngModel)]="email" required data-test-id="user-email-input" aria-required="true" />
      </mat-form-field>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'settings.users.displayName' | translate }}</mat-label>
        <input matInput [(ngModel)]="displayName" required data-test-id="user-name-input" aria-required="true" />
      </mat-form-field>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'auth.password' | translate }}</mat-label>
        <input matInput type="password" [(ngModel)]="password" required data-test-id="user-password-input" aria-required="true" />
      </mat-form-field>
      <mat-checkbox [(ngModel)]="systemAdmin" data-test-id="user-admin-checkbox">
        {{ 'settings.users.systemAdminLabel' | translate }}
      </mat-checkbox>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="onCancel()">{{ 'common.cancel' | translate }}</button>
      <button mat-flat-button color="primary"
              [disabled]="!email.trim() || !displayName.trim() || !password.trim() || password.length < 8"
              (click)="onCreate()" data-test-id="user-create-confirm">
        {{ 'common.save' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width { width: 100%; }
    mat-dialog-content { display: flex; flex-direction: column; gap: 8px; min-width: min(90vw, 450px); }
  `],
})
export class CreateUserDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<CreateUserDialogComponent>);

  email = '';
  displayName = '';
  password = '';
  systemAdmin = false;

  onCancel(): void {
    this.dialogRef.close();
  }

  onCreate(): void {
    this.dialogRef.close({
      email: this.email.trim(),
      displayName: this.displayName.trim(),
      password: this.password,
      systemAdmin: this.systemAdmin,
    });
  }
}
