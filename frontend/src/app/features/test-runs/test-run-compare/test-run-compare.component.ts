import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { LowerCasePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { take } from 'rxjs/operators';
import { TestRunApiService } from '../../../core/services/test-run-api.service';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';
import { RunComparison } from '../../../shared/models/run-comparison.model';
import { TestRun } from '../../../shared/models/test-run.model';
import { CategoryGroup, groupByCategory, hasDifferences, isBaseNewer } from './comparison-view';

/** How many recent runs the base picker offers. */
const PICKER_RUNS = 100;

/**
 * Two runs side by side (PRD-038 §3.5). The URL is the state (head, base, unchanged), so a
 * comparison can be shared; without a base the server picks the previous run.
 */
@Component({
  selector: 'app-test-run-compare',
  standalone: true,
  imports: [
    LowerCasePipe,
    RouterLink,
    MatButtonModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatTooltipModule,
    TranslateModule,
    LocalizedDatePipe,
  ],
  templateUrl: './test-run-compare.component.html',
  styleUrl: './test-run-compare.component.scss',
})
export class TestRunCompareComponent implements OnInit {
  private readonly api = inject(TestRunApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  projectId = '';
  headId = '';
  baseId: string | null = null;
  includeUnchanged = false;

  comparison: RunComparison | null = null;
  groups: CategoryGroup[] = [];
  differences = false;
  baseNewer = false;
  loading = false;
  /** The server's reason, e.g. that no previous run could be found. */
  error: string | null = null;
  runs: TestRun[] = [];

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.api.getAll(this.projectId, { size: PICKER_RUNS, sort: 'updatedAt,desc' })
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((page) => {
        this.runs = page.content;
        this.cdr.detectChanges();
      });
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.headId = params.get('head') ?? '';
      this.baseId = params.get('base');
      this.includeUnchanged = params.get('unchanged') === 'true';
      this.load();
    });
  }

  /** Runs the base can be: any but the head. */
  baseCandidates(): TestRun[] {
    return this.runs.filter((run) => run.id !== this.headId);
  }

  chooseBase(baseId: string): void {
    this.navigate({ base: baseId });
  }

  swap(): void {
    if (this.comparison) {
      this.navigate({ head: this.comparison.base.id, base: this.comparison.head.id });
    }
  }

  setIncludeUnchanged(include: boolean): void {
    this.navigate({ unchanged: include ? 'true' : null });
  }

  private navigate(queryParams: Record<string, string | null>): void {
    this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge' });
  }

  private load(): void {
    if (!this.headId) {
      return;
    }
    this.loading = true;
    this.error = null;
    this.api.compare(this.projectId, this.headId, this.baseId, this.includeUnchanged)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (comparison) => {
          this.comparison = comparison;
          this.groups = groupByCategory(comparison);
          this.differences = hasDifferences(comparison.counts);
          this.baseNewer = isBaseNewer(comparison);
          this.loading = false;
          this.cdr.detectChanges();
        },
        error: (error: HttpErrorResponse) => {
          this.comparison = null;
          this.error = error.error?.message ?? error.message;
          this.loading = false;
          this.cdr.detectChanges();
        },
      });
  }
}
