import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { take } from 'rxjs/operators';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { TestSuiteApiService } from '../../../core/services/test-suite-api.service';
import { TestSuite } from '../../../shared/models/test-suite.model';

@Component({
  selector: 'app-bulk-add-to-suite-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatSelectModule, MatFormFieldModule, FormsModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'bulk.addToSuite' | translate }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'bulk.selectSuite' | translate }}</mat-label>
        <mat-select [(ngModel)]="selectedSuiteId">
          @for (suite of suites; track suite.id) {
            <mat-option [value]="suite.id">{{ suite.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'common.cancel' | translate }}</button>
      <button mat-flat-button [disabled]="!selectedSuiteId" (click)="confirm()">{{ 'common.confirm' | translate }}</button>
    </mat-dialog-actions>
  `,
  styles: [`.full-width { width: 100%; } mat-dialog-content { min-width: min(90vw, 400px); }`],
})
export class BulkAddToSuiteDialogComponent implements OnInit {
  private readonly testSuiteApi = inject(TestSuiteApiService);
  private readonly dialogRef = inject(MatDialogRef<BulkAddToSuiteDialogComponent>);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  suites: TestSuite[] = [];
  selectedSuiteId: string | null = null;
  projectId = '';

  ngOnInit(): void {
    if (this.projectId) {
      this.testSuiteApi.getAll(this.projectId, { size: 200, sort: 'name,asc' }).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe(result => {
        this.suites = result.content;
        this.cdr.detectChanges();
      });
    }
  }

  confirm(): void {
    if (this.selectedSuiteId) {
      this.dialogRef.close(this.selectedSuiteId);
    }
  }
}
