import { ChangeDetectorRef, Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { take } from 'rxjs/operators';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { TranslateModule } from '@ngx-translate/core';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { ImportResult } from '../../../shared/models/test-case.model';

@Component({
  selector: 'app-import-test-cases-dialog',
  standalone: true,
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    TranslateModule,
  ],
  templateUrl: './import-test-cases-dialog.component.html',
  styleUrl: './import-test-cases-dialog.component.scss',
})
export class ImportTestCasesDialogComponent {
  private readonly api = inject(TestCaseApiService);
  private readonly dialogRef = inject(MatDialogRef<ImportTestCasesDialogComponent>);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  projectId = '';
  file: File | null = null;
  loading = false;
  preview: ImportResult | null = null;
  errorColumns = ['row', 'message'];

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file = input.files && input.files.length > 0 ? input.files[0] : null;
    this.preview = null;
  }

  dryRun(): void {
    if (!this.file) {
      return;
    }
    this.loading = true;
    this.api.import(this.projectId, this.file, true).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (result) => {
        this.preview = result;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  confirmImport(): void {
    if (!this.file) {
      return;
    }
    this.loading = true;
    this.api.import(this.projectId, this.file, false).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (result) => {
        this.loading = false;
        this.dialogRef.close(result);
      },
      error: () => {
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
