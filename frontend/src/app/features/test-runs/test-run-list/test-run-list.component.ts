import { MatMenuModule } from '@angular/material/menu';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';
import { OPTIONAL_RUN_COLUMNS, OptionalRunColumn, readRunColumns, resultSegments, runColumns, writeRunColumns } from './run-list-view';
import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AsyncPipe, LowerCasePipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { Subject } from 'rxjs';
import { debounceTime, take } from 'rxjs/operators';
import { TranslateModule } from '@ngx-translate/core';
import { TestRunActions } from '../../../store/test-run/test-run.actions';
import { selectAllTestRuns, selectTestRunsLoading, selectTestRunsError, selectTestRunPage } from '../../../store/test-run/test-run.selectors';
import { TestRun, TestRunQuery, TestRunStatus } from '../../../shared/models/test-run.model';
import { CloneTestRunDialogComponent, CloneTestRunDialogResult } from '../clone-test-run-dialog/clone-test-run-dialog.component';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { AutomationPanelComponent } from '../../automation/automation-panel.component';

import { EnvironmentApiService } from '../../../core/services/environment-api.service';
import { ProjectEnvironment } from '../../../shared/models/environment.model';
import { CustomFieldFiltersComponent } from '../../../shared/components/custom-fields/custom-field-filters.component';
import { readCustomFieldParams } from '../../../shared/components/custom-fields/custom-field-filter-params';
import { MatTooltipModule } from '@angular/material/tooltip';

@Component({
  selector: 'app-test-run-list',
  standalone: true,
  imports: [
    MatMenuModule,
    MatCheckboxModule,
    LocalizedDatePipe,
    MatTooltipModule,
    CustomFieldFiltersComponent,
    AsyncPipe,
    LowerCasePipe,
    RouterLink,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatPaginatorModule,
    MatSortModule,
    TranslateModule,
    AutomationPanelComponent,
  ],
  templateUrl: './test-run-list.component.html',
  styleUrl: './test-run-list.component.scss',
})
export class TestRunListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  private readonly environmentApi = inject(EnvironmentApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  projectId = '';
  testRuns$ = this.store.select(selectAllTestRuns);
  loading$ = this.store.select(selectTestRunsLoading);
  error$ = this.store.select(selectTestRunsError);
  page$ = this.store.select(selectTestRunPage);
  readonly optionalColumns = OPTIONAL_RUN_COLUMNS;
  /** PRD-049: switched on by the user, remembered in this browser. */
  shownColumns: Set<OptionalRunColumn> = readRunColumns(globalThis.localStorage);
  displayedColumns = runColumns(this.shownColumns);
  readonly resultSegments = resultSegments;
  searchTerm = '';
  statusFilter: TestRunStatus | '' = '';
  environmentFilter = '';
  /** Archived ones included: they still label historical runs. */
  environments: ProjectEnvironment[] = [];
  sortActive = 'updatedAt';
  sortDirection: 'asc' | 'desc' = 'desc';
  allStatuses: TestRunStatus[] = ['PLANNED', 'IN_PROGRESS', 'COMPLETED', 'ABORTED'];

  private currentQuery: TestRunQuery = {};
  private readonly searchChange$ = new Subject<void>();

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    if (!this.projectId) {
      return;
    }

    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.searchTerm = params.get('q') ?? '';
      this.statusFilter = (params.get('status') as TestRunStatus) ?? '';
      this.environmentFilter = params.get('environmentId') ?? '';
      this.sortActive = params.get('sort')?.split(',')[0] ?? 'updatedAt';
      this.sortDirection = (params.get('sort')?.split(',')[1] as 'asc' | 'desc') ?? 'desc';

      this.currentQuery = {
        q: this.searchTerm || undefined,
        status: this.statusFilter ? [this.statusFilter] : undefined,
        environmentId: this.environmentFilter || undefined,
        customFieldParams: readCustomFieldParams(params),
        page: params.get('page') ? Number(params.get('page')) : 0,
        size: params.get('size') ? Number(params.get('size')) : 50,
        sort: params.get('sort') ?? 'updatedAt,desc',
      };
      this.store.dispatch(TestRunActions.loadTestRuns({ projectId: this.projectId, query: this.currentQuery }));
    });

    this.environmentApi.getAll(this.projectId).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((environments) => {
        this.environments = environments;
        this.cdr.detectChanges();
      });

    this.searchChange$
      .pipe(debounceTime(300), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.applyFilters());
  }

  toggleColumn(column: OptionalRunColumn): void {
    const next = new Set(this.shownColumns);
    if (!next.delete(column)) {
      next.add(column);
    }
    this.shownColumns = next;
    this.displayedColumns = runColumns(next);
    writeRunColumns(globalThis.localStorage, next);
  }

  applyFilters(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        q: this.searchTerm || null,
        status: this.statusFilter || null,
        environmentId: this.environmentFilter || null,
        page: null,
      },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  onSearchInput(): void {
    this.searchChange$.next();
  }

  onPage(event: PageEvent): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: event.pageIndex, size: event.pageSize },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  onSortChange(sort: Sort): void {
    const sortParam = sort.direction ? `${sort.active},${sort.direction}` : null;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { sort: sortParam, page: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  retry(): void {
    this.store.dispatch(TestRunActions.loadTestRuns({ projectId: this.projectId, query: this.currentQuery }));
  }

  cloneTestRun(run: TestRun): void {
    const dialogRef = this.dialog.open(CloneTestRunDialogComponent, {
      data: { projectId: this.projectId, name: run.name, environment: run.environment ?? '' },
    });
    dialogRef.afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe((result: CloneTestRunDialogResult | undefined) => {
      if (result) {
        this.store.dispatch(
          TestRunActions.cloneTestRun({
            projectId: this.projectId,
            runId: run.id,
            request: result,
          })
        );
      }
    });
  }

  deleteTestRun(run: TestRun): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: {
        titleKey: 'common.delete',
        messageKey: 'testRun.deleteConfirm',
        messageParams: { name: run.name },
        secondaryMessageKey: 'common.irreversibleWarning',
        danger: true,
      } as ConfirmDialogData,
    });
    dialogRef.afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe(confirmed => {
      if (confirmed) {
        this.store.dispatch(TestRunActions.deleteTestRun({ projectId: this.projectId, id: run.id }));
      }
    });
  }
}
