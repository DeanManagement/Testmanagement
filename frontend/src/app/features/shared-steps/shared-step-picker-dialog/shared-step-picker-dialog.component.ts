import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { debounceTime, distinctUntilChanged, startWith, switchMap, take } from 'rxjs';
import { SharedStepApiService } from '../../../core/services/shared-step-api.service';
import { SharedStepSummary } from '../../../shared/models/shared-step.model';

const SEARCH_DEBOUNCE_MS = 250;

/** Searches the project's shared steps; closes with the full shared step (its steps included) picked. */
@Component({
  selector: 'app-shared-step-picker-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './shared-step-picker-dialog.component.html',
  styleUrl: './shared-step-picker-dialog.component.scss',
})
export class SharedStepPickerDialogComponent implements OnInit {
  private readonly api = inject(SharedStepApiService);
  private readonly dialogRef = inject(MatDialogRef<SharedStepPickerDialogComponent>);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly data = inject<{ projectId: string }>(MAT_DIALOG_DATA);

  readonly query = new FormControl('', { nonNullable: true });
  results: SharedStepSummary[] = [];
  loading = true;

  ngOnInit(): void {
    this.query.valueChanges.pipe(
      startWith(''),
      debounceTime(SEARCH_DEBOUNCE_MS),
      distinctUntilChanged(),
      switchMap((q) => this.api.list(this.data.projectId, q)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((page) => {
      this.results = page.content;
      this.loading = false;
      this.cdr.detectChanges();
    });
  }

  pick(summary: SharedStepSummary): void {
    this.api.get(this.data.projectId, summary.id).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((sharedStep) => this.dialogRef.close(sharedStep));
  }
}
