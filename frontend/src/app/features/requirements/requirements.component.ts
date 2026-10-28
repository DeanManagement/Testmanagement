import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { forkJoin } from 'rxjs';
import { take } from 'rxjs/operators';
import { LowerCasePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { RequirementApiService } from '../../core/services/requirement-api.service';
import { TestCaseApiService } from '../../core/services/test-case-api.service';
import { MarkdownPipe } from '../../shared/pipes/markdown.pipe';
import { TestPlanApiService } from '../../core/services/test-plan-api.service';
import { TestPlan } from '../../shared/models/test-plan.model';
import { saveFile } from '../../shared/utils/save-file';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../shared/components/confirm-dialog/confirm-dialog.component';
import {
  CoverageStatus,
  CoverageSummary,
  Requirement,
  SaveRequirementRequest,
  TraceabilityRow,
} from '../../shared/models/requirement.model';
import { TestCase } from '../../shared/models/test-case.model';
import { RequirementSortColumn, viewRequirements } from './requirement-view';

/**
 * Requirements, the traceability matrix and the coverage summary on one screen (PRD-014 §3.4).
 *
 * <p>Kept together deliberately: the three answer one question — "is everything we promised
 * actually tested?" — and splitting them across routes would make the user assemble the answer.
 */
@Component({
  selector: 'app-requirements',
  standalone: true,
  imports: [
    RouterLink,
    FormsModule,
    LowerCasePipe,
    MarkdownPipe,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatMenuModule,
    TranslateModule,
  ],
  templateUrl: './requirements.component.html',
  styleUrl: './requirements.component.scss',
})
export class RequirementsComponent implements OnInit {
  private readonly api = inject(RequirementApiService);
  private readonly testCaseApi = inject(TestCaseApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  private readonly testPlanApi = inject(TestPlanApiService);

  projectId = '';
  requirements: Requirement[] = [];
  matrix: TraceabilityRow[] = [];
  private rowsById = new Map<string, TraceabilityRow>();
  coverage: CoverageSummary | null = null;
  testCases: TestCase[] = [];
  loading = true;
  saving = false;

  formOpen = false;
  editingId: string | null = null;
  formExternalId = '';
  formTitle = '';
  formDescription = '';

  /** Which requirement's link picker is open, and what is selected in it. */
  linkingId: string | null = null;
  linkSelection = '';

  readonly coverageStatuses: CoverageStatus[] = ['PASSED', 'FAILED', 'BLOCKED', 'SKIPPED', 'UNTESTED', 'UNCOVERED'];
  searchQuery = '';
  coverageFilter: CoverageStatus | '' = '';
  sortColumn: RequirementSortColumn = 'externalId';
  sortDirection: 'asc' | 'desc' = 'asc';

  /** Offered in the export menu; loaded when the menu is first opened. */
  testPlans: TestPlan[] | null = null;

  get visibleRequirements(): Requirement[] {
    return viewRequirements(this.requirements, this.rowsById, {
      query: this.searchQuery,
      coverage: this.coverageFilter,
      sort: { active: this.sortColumn, direction: this.sortDirection },
    });
  }

  toggleSortDirection(): void {
    this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
  }

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id')
      ?? this.route.snapshot.paramMap.get('id')
      ?? '';
    if (this.projectId) {
      this.load();
    } else {
      this.loading = false;
    }
  }

  private load(): void {
    this.loading = true;
    forkJoin({
      requirements: this.api.getAll(this.projectId, 0, 200),
      matrix: this.api.getMatrix(this.projectId),
      coverage: this.api.getCoverage(this.projectId),
    })
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ requirements, matrix, coverage }) => {
          this.requirements = requirements.content;
          this.matrix = matrix;
          this.rowsById = new Map(matrix.map((row) => [row.requirementId, row]));
          this.coverage = coverage;
          this.loading = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.loading = false;
          this.cdr.markForCheck();
        },
      });
  }

  private loadTestCases(): void {
    if (this.testCases.length > 0) {
      return;
    }
    this.testCaseApi.getAll(this.projectId, { page: 0, size: 200 })
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.testCases = page.content;
          this.cdr.markForCheck();
        },
        error: () => undefined,
      });
  }

  get canSave(): boolean {
    return this.formExternalId.trim().length > 0 && this.formTitle.trim().length > 0;
  }

  openCreate(): void {
    this.formOpen = true;
    this.editingId = null;
    this.formExternalId = '';
    this.formTitle = '';
    this.formDescription = '';
  }

  openEdit(requirement: Requirement): void {
    this.formOpen = true;
    this.editingId = requirement.id;
    this.formExternalId = requirement.externalId;
    this.formTitle = requirement.title;
    this.formDescription = requirement.description ?? '';
  }

  closeForm(): void {
    this.formOpen = false;
    this.editingId = null;
  }

  save(): void {
    if (!this.canSave || this.saving) {
      return;
    }
    this.saving = true;
    const request: SaveRequirementRequest = {
      externalId: this.formExternalId.trim(),
      title: this.formTitle.trim(),
      description: this.formDescription.trim() || undefined,
    };
    const call = this.editingId
      ? this.api.update(this.projectId, this.editingId, request)
      : this.api.create(this.projectId, request);

    call.pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving = false;
        this.closeForm();
        this.notify('requirement.saved');
        this.load();
      },
      error: () => {
        this.saving = false;
        this.cdr.markForCheck();
      },
    });
  }

  remove(requirement: Requirement): void {
    const data: ConfirmDialogData = {
      titleKey: 'requirement.deleteTitle',
      messageKey: 'requirement.deleteMessage',
      messageParams: { id: requirement.externalId },
      danger: true,
    };
    this.dialog.open(ConfirmDialogComponent, { data })
      .afterClosed()
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.api.delete(this.projectId, requirement.id)
          .pipe(take(1), takeUntilDestroyed(this.destroyRef))
          .subscribe(() => {
            this.notify('requirement.deleted');
            this.load();
          });
      });
  }

  openLink(requirement: Requirement): void {
    this.linkingId = requirement.id;
    this.linkSelection = '';
    this.loadTestCases();
  }

  closeLink(): void {
    this.linkingId = null;
    this.linkSelection = '';
  }

  confirmLink(): void {
    if (!this.linkingId || !this.linkSelection) {
      return;
    }
    this.api.linkTestCase(this.projectId, this.linkingId, this.linkSelection)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.closeLink();
          this.load();
        },
        error: () => this.closeLink(),
      });
  }

  unlink(requirement: Requirement, testCaseId: string): void {
    this.api.unlinkTestCase(this.projectId, requirement.id, testCaseId)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load());
  }

  /** Cases not already linked to this requirement, so the picker cannot offer a duplicate. */
  availableTestCases(requirement: Requirement): TestCase[] {
    const linked = new Set(requirement.testCases.map((tc) => tc.id));
    return this.testCases.filter((tc) => !linked.has(tc.id));
  }

  rowFor(requirementId: string): TraceabilityRow | undefined {
    return this.rowsById.get(requirementId);
  }

  loadTestPlans(): void {
    if (this.testPlans !== null) {
      return;
    }
    this.testPlanApi.getAll(this.projectId).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (plans) => {
          this.testPlans = plans;
          this.cdr.markForCheck();
        },
        error: () => undefined,
      });
  }

  /** TM20: requirement, linked cases and latest result as CSV, optionally from one plan's runs. */
  exportMatrix(testPlan?: TestPlan): void {
    this.api.exportMatrixCsv(this.projectId, testPlan?.id)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((blob) => saveFile(blob, testPlan ? `traceability-${slug(testPlan.name)}.csv` : 'traceability.csv'));
  }

  private notify(key: string): void {
    this.snackBar.open(this.translate.instant(key), this.translate.instant('common.close'), {
      duration: 4000,
    });
  }
}

/** A plan name made safe for a file name. */
function slug(name: string): string {
  return name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'plan';
}
