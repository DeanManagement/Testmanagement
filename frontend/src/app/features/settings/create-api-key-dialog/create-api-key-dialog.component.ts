import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { Clipboard } from '@angular/cdk/clipboard';
import { ApiKeyCreated } from '../../../shared/models/api-key.model';

@Component({
  selector: 'app-create-api-key-dialog',
  standalone: true,
  imports: [
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    TranslateModule,
  ],
  template: `
    @if (!createdKey) {
      <h2 mat-dialog-title>{{ 'settings.apiKeys.createTitle' | translate }}</h2>
      <mat-dialog-content>
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>{{ 'settings.apiKeys.name' | translate }}</mat-label>
          <input matInput [(ngModel)]="name" required data-test-id="api-key-name-input" />
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button (click)="onCancel()">{{ 'common.cancel' | translate }}</button>
        <button mat-flat-button color="primary" [disabled]="!name.trim()" (click)="onCreate()"
                data-test-id="api-key-create-confirm">
          {{ 'settings.apiKeys.create' | translate }}
        </button>
      </mat-dialog-actions>
    } @else {
      <h2 mat-dialog-title>{{ 'settings.apiKeys.createdTitle' | translate }}</h2>
      <mat-dialog-content>
        <p class="warning-text">{{ 'settings.apiKeys.createdWarning' | translate }}</p>
        <div class="key-display">
          <code data-test-id="api-key-raw-value">{{ createdKey.rawKey }}</code>
          <button mat-icon-button (click)="copyKey()" data-test-id="api-key-copy-btn">
            <mat-icon>{{ copied ? 'check' : 'content_copy' }}</mat-icon>
          </button>
        </div>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-flat-button color="primary" (click)="onClose()" data-test-id="api-key-done-btn">
          {{ 'settings.apiKeys.done' | translate }}
        </button>
      </mat-dialog-actions>
    }
  `,
  styles: [`
    .full-width { width: 100%; }
    mat-dialog-content { display: flex; flex-direction: column; gap: 8px; min-width: 450px; }
    .warning-text { color: var(--tm-danger, #d32f2f); font-size: 14px; margin-bottom: 8px; }
    .key-display {
      display: flex;
      align-items: center;
      gap: 8px;
      background: #f5f5f5;
      border-radius: 4px;
      padding: 8px 12px;
    }
    .key-display code {
      flex: 1;
      word-break: break-all;
      font-family: 'JetBrains Mono', 'Fira Code', monospace;
      font-size: 13px;
    }
  `],
})
export class CreateApiKeyDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<CreateApiKeyDialogComponent>);
  private readonly clipboard = inject(Clipboard);

  name = '';
  createdKey: ApiKeyCreated | null = null;
  copied = false;

  onCancel(): void {
    this.dialogRef.close();
  }

  onCreate(): void {
    this.dialogRef.close({ action: 'create', name: this.name.trim() });
  }

  setCreatedKey(key: ApiKeyCreated): void {
    this.createdKey = key;
  }

  copyKey(): void {
    if (this.createdKey) {
      this.clipboard.copy(this.createdKey.rawKey);
      this.copied = true;
      setTimeout(() => (this.copied = false), 2000);
    }
  }

  onClose(): void {
    this.dialogRef.close({ action: 'done' });
  }
}
