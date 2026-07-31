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
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { take } from 'rxjs/operators';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';
import {
  TestCaseVersion,
  TestCaseVersionStep,
  TestCaseVersionSummary,
} from '../../../shared/models/test-case-version.model';

/** One row of the comparison: a field or step, with both sides and whether they differ. */
interface DiffRow {
  labelKey: string;
  /** Step rows carry their position; field rows do not. */
  position?: number;
  left: string;
  right: string;
  changed: boolean;
  addedOrRemoved: boolean;
}

/**
 * Version history for a test case (PRD-011 §3.4): pick two versions, see what changed.
 *
 * <p>Comparison is per field and per step rather than a character-level text diff. For an audit
 * question — "what did this test say when that run executed it?" — knowing precisely which step
 * changed is the useful answer, and a word-level diff of a step's prose adds noise without
 * changing what a reviewer concludes.
 */
@Component({
  selector: 'app-test-case-versions',
  standalone: true,
  imports: [
    FormsModule,
    LocalizedDatePipe,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './test-case-versions.component.html',
  styleUrl: './test-case-versions.component.scss',
})
export class TestCaseVersionsComponent implements OnChanges {
  private readonly http = inject(HttpClient);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) projectId!: string;
  @Input({ required: true }) testCaseId!: string;

  versions: TestCaseVersionSummary[] = [];
  loading = false;
  comparing = false;

  leftVersion: number | null = null;
  rightVersion: number | null = null;
  left: TestCaseVersion | null = null;
  right: TestCaseVersion | null = null;
  rows: DiffRow[] = [];

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['testCaseId'] || changes['projectId']) && this.projectId && this.testCaseId) {
      this.load();
    }
  }

  private baseUrl(): string {
    return `/api/projects/${this.projectId}/test-cases/${this.testCaseId}/versions`;
  }

  private load(): void {
    this.loading = true;
    this.http.get<TestCaseVersionSummary[]>(this.baseUrl())
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (versions) => {
          this.versions = versions;
          this.loading = false;
          // Default to the most recent change, which is what someone opening History wants.
          if (versions.length >= 2) {
            this.rightVersion = versions[0].versionNumber;
            this.leftVersion = versions[1].versionNumber;
            this.compare();
          }
          this.cdr.markForCheck();
        },
        error: () => {
          this.versions = [];
          this.loading = false;
          this.cdr.markForCheck();
        },
      });
  }

  compare(): void {
    if (this.leftVersion === null || this.rightVersion === null || this.comparing) {
      return;
    }
    this.comparing = true;
    forkJoin({
      left: this.http.get<TestCaseVersion>(`${this.baseUrl()}/${this.leftVersion}`),
      right: this.http.get<TestCaseVersion>(`${this.baseUrl()}/${this.rightVersion}`),
    })
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ left, right }) => {
          this.left = left;
          this.right = right;
          this.rows = this.buildRows(left, right);
          this.comparing = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.comparing = false;
          this.cdr.markForCheck();
        },
      });
  }

  get changedCount(): number {
    return this.rows.filter((r) => r.changed).length;
  }

  private buildRows(left: TestCaseVersion, right: TestCaseVersion): DiffRow[] {
    const rows: DiffRow[] = [
      this.fieldRow('testCase.form.title', left.title, right.title),
      this.fieldRow('testCase.form.description', left.description, right.description),
      this.fieldRow('testCase.form.preconditions', left.preconditions, right.preconditions),
      this.fieldRow('testCase.form.priority', left.priority, right.priority),
      this.fieldRow('testCase.form.status', left.status, right.status),
      this.fieldRow('testCase.form.labels', left.labels.join(', '), right.labels.join(', ')),
    ];

    // Steps are compared by position: a step inserted in the middle shifts everything after it,
    // which is itself a change worth showing rather than hiding behind a smarter alignment.
    const stepCount = Math.max(left.steps.length, right.steps.length);
    for (let i = 0; i < stepCount; i++) {
      const l = left.steps[i];
      const r = right.steps[i];
      rows.push({
        labelKey: 'version.step',
        position: i + 1,
        left: this.stepText(l),
        right: this.stepText(r),
        changed: this.stepText(l) !== this.stepText(r),
        addedOrRemoved: !l || !r,
      });
    }
    return rows;
  }

  private fieldRow(labelKey: string, left: string | null, right: string | null): DiffRow {
    const l = left ?? '';
    const r = right ?? '';
    return { labelKey, left: l, right: r, changed: l !== r, addedOrRemoved: false };
  }

  private stepText(step: TestCaseVersionStep | undefined): string {
    if (!step) {
      return '';
    }
    const parts = [step.action];
    if (step.expectedResult) {
      parts.push(`→ ${step.expectedResult}`);
    }
    if (step.testData) {
      parts.push(`(${step.testData})`);
    }
    return parts.join(' ');
  }
}
