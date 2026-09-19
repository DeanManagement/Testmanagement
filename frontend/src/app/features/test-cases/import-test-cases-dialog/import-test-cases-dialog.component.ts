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
import { TestCaseFolder } from '../../../shared/models/test-case-folder.model';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';

export interface FolderOption {
  id: string;
  name: string;
  depth: number;
}

/** Depth-first, so the select lists each folder under its parent. */
export function flattenFolders(folders: TestCaseFolder[], depth = 0): FolderOption[] {
  return folders.flatMap((f) => [{ id: f.id, name: f.name, depth }, ...flattenFolders(f.children ?? [], depth + 1)]);
}

@Component({
  selector: 'app-import-test-cases-dialog',
  standalone: true,
  imports: [
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatFormFieldModule,
    MatSelectModule,
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
  /** The folder tree to offer as the target (PRD-040); set by the opener. */
  folders: TestCaseFolder[] = [];
  /** Null means the project root. The opener passes the folder selected in the list. */
  folderId: string | null = null;
  file: File | null = null;
  loading = false;
  preview: ImportResult | null = null;
  errorColumns = ['row', 'message'];

  get folderOptions(): FolderOption[] {
    return flattenFolders(this.folders);
  }

  /** Created / updated / unchanged only mean something for a Gherkin upload. */
  get isGherkin(): boolean {
    return !!this.file && /\.(feature|zip)$/i.test(this.file.name);
  }

  onFolderChange(folderId: string | null): void {
    this.folderId = folderId;
    this.preview = null;
  }

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
    this.api.import(this.projectId, this.file, true, this.folderId).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
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
    this.api.import(this.projectId, this.file, false, this.folderId).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
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
