import {
  ChangeDetectorRef,
  Component,
  DestroyRef,
  inject,
  Input,
  OnChanges,
  SimpleChanges,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { take } from 'rxjs/operators';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { ParameterSetApiService } from '../../../core/services/parameter-set-api.service';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { ParameterSet } from '../../../shared/models/parameter-set.model';
import { TestStep } from '../../../shared/models/test-case.model';
import { substitute, unresolvedIn } from '../../../shared/utils/parameter-substitutor';

interface KeyValueRow {
  key: string;
  value: string;
}

/** A step as it will read when executed with the previewed set. */
interface PreviewStep {
  action: string;
  expectedResult: string;
  testData: string;
  unresolved: string[];
}

/**
 * Parameter sets on a test case, with a live preview of the substituted steps (PRD-015 §3.3).
 *
 * <p>The preview matters more than it looks: placeholders are easy to mistype, and without seeing
 * the result an author only finds out during a run, when the step reads {@code enter {usernme}}.
 */
@Component({
  selector: 'app-test-case-parameters',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    TranslateModule,
  ],
  templateUrl: './test-case-parameters.component.html',
  styleUrl: './test-case-parameters.component.scss',
})
export class TestCaseParametersComponent implements OnChanges {
  private readonly api = inject(ParameterSetApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) projectId!: string;
  @Input({ required: true }) testCaseId!: string;
  @Input() steps: TestStep[] = [];
  @Input() canEdit = false;

  sets: ParameterSet[] = [];
  loading = false;
  saving = false;

  formOpen = false;
  editingId: string | null = null;
  formName = '';
  rows: KeyValueRow[] = [{ key: '', value: '' }];

  /** Which set the preview is showing; defaults to the first once loaded. */
  previewSetId: string | null = null;

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['testCaseId'] || changes['projectId']) && this.projectId && this.testCaseId) {
      this.load();
    }
  }

  private load(): void {
    this.loading = true;
    this.api.getAll(this.projectId, this.testCaseId)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (sets) => {
          this.sets = sets;
          this.previewSetId = sets.length > 0 ? sets[0].id : null;
          this.loading = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.sets = [];
          this.loading = false;
          this.cdr.markForCheck();
        },
      });
  }

  get previewSet(): ParameterSet | null {
    return this.sets.find((s) => s.id === this.previewSetId) ?? null;
  }

  /** The steps as they will read when executed with the previewed set. */
  get previewSteps(): PreviewStep[] {
    const values = this.previewSet?.values ?? {};
    return this.steps.map((step) => ({
      action: substitute(step.action, values),
      expectedResult: substitute(step.expectedResult, values),
      testData: substitute(step.testData, values),
      unresolved: [
        ...unresolvedIn(step.action, values),
        ...unresolvedIn(step.expectedResult, values),
        ...unresolvedIn(step.testData, values),
      ],
    }));
  }

  get canSave(): boolean {
    return this.formName.trim().length > 0
      && this.rows.some((r) => r.key.trim().length > 0);
  }

  openCreate(): void {
    this.formOpen = true;
    this.editingId = null;
    this.formName = '';
    this.rows = this.suggestedRows();
  }

  openEdit(set: ParameterSet): void {
    this.formOpen = true;
    this.editingId = set.id;
    this.formName = set.name;
    this.rows = Object.entries(set.values).map(([key, value]) => ({ key, value }));
    if (this.rows.length === 0) {
      this.rows = [{ key: '', value: '' }];
    }
  }

  /**
   * Pre-fills the key column from the placeholders already in the steps, so a new set starts from
   * what the test actually needs rather than an empty grid.
   */
  private suggestedRows(): KeyValueRow[] {
    const keys = new Set<string>();
    for (const step of this.steps) {
      for (const key of [
        ...unresolvedIn(step.action, {}),
        ...unresolvedIn(step.expectedResult, {}),
        ...unresolvedIn(step.testData, {}),
      ]) {
        keys.add(key);
      }
    }
    const rows = [...keys].map((key) => ({ key, value: '' }));
    return rows.length > 0 ? rows : [{ key: '', value: '' }];
  }

  closeForm(): void {
    this.formOpen = false;
    this.editingId = null;
  }

  addRow(): void {
    this.rows = [...this.rows, { key: '', value: '' }];
  }

  removeRow(index: number): void {
    this.rows = this.rows.filter((_, i) => i !== index);
    if (this.rows.length === 0) {
      this.rows = [{ key: '', value: '' }];
    }
  }

  save(): void {
    if (!this.canSave || this.saving) {
      return;
    }
    this.saving = true;

    const values: Record<string, string> = {};
    for (const row of this.rows) {
      const key = row.key.trim();
      if (key) {
        values[key] = row.value;
      }
    }
    const request = { name: this.formName.trim(), values };
    const call = this.editingId
      ? this.api.update(this.projectId, this.testCaseId, this.editingId, request)
      : this.api.create(this.projectId, this.testCaseId, request);

    call.pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving = false;
        this.closeForm();
        this.load();
      },
      error: () => {
        this.saving = false;
        this.cdr.markForCheck();
      },
    });
  }

  remove(set: ParameterSet): void {
    const data: ConfirmDialogData = {
      titleKey: 'parameter.deleteTitle',
      messageKey: 'parameter.deleteMessage',
      messageParams: { name: set.name },
      secondaryMessageKey: 'parameter.deleteKeepsResults',
      danger: true,
    };
    this.dialog.open(ConfirmDialogComponent, { data })
      .afterClosed()
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.api.delete(this.projectId, this.testCaseId, set.id)
          .pipe(take(1), takeUntilDestroyed(this.destroyRef))
          .subscribe(() => this.load());
      });
  }

  valueEntries(set: ParameterSet): KeyValueRow[] {
    return Object.entries(set.values).map(([key, value]) => ({ key, value }));
  }

  trackByIndex(index: number): number {
    return index;
  }
}
