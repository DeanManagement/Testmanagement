import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Store } from '@ngrx/store';
import { AsyncPipe, LowerCasePipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatSortModule, Sort } from '@angular/material/sort';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { combineLatest, Observable, Subject } from 'rxjs';
import { debounceTime, filter, switchMap, take } from 'rxjs/operators';
import { BugReportActions } from '../../../store/bug-report/bug-report.actions';
import {
  selectAllBugReports,
  selectBugReportPage,
  selectBugReportsError,
  selectBugReportsLoading,
} from '../../../store/bug-report/bug-report.selectors';
import { selectAuthUser } from '../../../store/auth/auth.selectors';
import {
  ALL_BUG_STATUSES,
  BugReport,
  BugReportQuery,
  BugReportStatus,
  BulkUpdateBugReportsRequest,
  ChangeBugStatusRequest,
  Priority,
} from '../../../shared/models/bug-report.model';
import { ProjectMember } from '../../../shared/models/project-member.model';
import { BugReportApiService } from '../../../core/services/bug-report-api.service';
import { ProjectMemberApiService } from '../../../core/services/project-member-api.service';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { ChangeBugStatusDialogComponent, ChangeBugStatusDialogData } from '../change-bug-status-dialog/change-bug-status-dialog.component';
import { BugReportKanbanComponent, BugStatusDrop } from '../bug-report-kanban/bug-report-kanban.component';

const DEFAULT_PAGE_SIZE = 50;
/** The board shows every bug of the filter; the API's largest page. */
const KANBAN_PAGE_SIZE = 200;
const DEFAULT_SORT = 'createdAt,desc';
const SEARCH_DEBOUNCE_MS = 300;
const SNACK_MS = 3000;

type ViewMode = 'list' | 'kanban';

/**
 * Triage (PRD-045): search, filter, sort and page on the server, with every setting in the URL so a
 * view can be shared; select bugs in the list or on the board and change them in one step.
 */
@Component({
  selector: 'app-bug-report-list',
  standalone: true,
  imports: [
    AsyncPipe,
    BugReportKanbanComponent,
    FormsModule,
    LocalizedDatePipe,
    LowerCasePipe,
    RouterLink,
    MatTableModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatCheckboxModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatMenuModule,
    MatSelectModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSortModule,
    TranslateModule,
  ],
  templateUrl: './bug-report-list.component.html',
  styleUrl: './bug-report-list.component.scss',
})
export class BugReportListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly api = inject(BugReportApiService);
  private readonly memberApi = inject(ProjectMemberApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);

  readonly allStatuses = ALL_BUG_STATUSES;
  readonly priorities: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  readonly displayedColumns = ['select', 'key', 'title', 'priority', 'status', 'assignee', 'createdAt'];

  projectId = '';
  bugReports$ = this.store.select(selectAllBugReports);
  loading$ = this.store.select(selectBugReportsLoading);
  error$ = this.store.select(selectBugReportsError);
  page$ = this.store.select(selectBugReportPage);

  viewMode: ViewMode = 'list';
  searchTerm = '';
  statusFilter: BugReportStatus[] = [];
  priorityFilter: Priority[] = [];
  assigneeFilter: string[] = [];
  sortActive = 'createdAt';
  sortDirection: 'asc' | 'desc' = 'desc';

  members: ProjectMember[] = [];
  canWrite = false;
  readonly selected = signal<ReadonlySet<string>>(new Set());

  private currentQuery: BugReportQuery = {};
  private readonly searchChange$ = new Subject<void>();

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    if (!this.projectId) {
      return;
    }
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => this.readQuery(params));
    this.searchChange$.pipe(debounceTime(SEARCH_DEBOUNCE_MS), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.applyFilters());
    this.loadMembersAndRole();
  }

  private readQuery(params: ParamMap): void {
    this.viewMode = params.get('view') === 'kanban' ? 'kanban' : 'list';
    this.searchTerm = params.get('q') ?? '';
    this.statusFilter = params.getAll('status') as BugReportStatus[];
    this.priorityFilter = params.getAll('priority') as Priority[];
    this.assigneeFilter = params.getAll('assignee');
    const sort = params.get('sort') ?? DEFAULT_SORT;
    this.sortActive = sort.split(',')[0];
    this.sortDirection = sort.split(',')[1] === 'asc' ? 'asc' : 'desc';
    const kanban = this.viewMode === 'kanban';
    this.currentQuery = {
      q: this.searchTerm || undefined,
      status: this.statusFilter,
      priority: this.priorityFilter,
      assignee: this.assigneeFilter,
      page: kanban ? 0 : Number(params.get('page') ?? 0),
      size: kanban ? KANBAN_PAGE_SIZE : Number(params.get('size') ?? DEFAULT_PAGE_SIZE),
      sort,
    };
    this.selected.set(new Set());
    this.reload();
  }

  private loadMembersAndRole(): void {
    combineLatest([this.memberApi.getByProject(this.projectId), this.store.select(selectAuthUser).pipe(take(1))])
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ([members, user]) => {
          this.members = members;
          const role = members.find((m) => m.userId === user?.id)?.role;
          this.canWrite = !!user && (user.systemAdmin || role === 'ADMIN' || role === 'TESTER');
          this.cdr.detectChanges();
        },
        error: () => undefined,
      });
  }

  reload(): void {
    this.store.dispatch(BugReportActions.loadBugReports({ projectId: this.projectId, query: this.currentQuery }));
  }

  get hasFilters(): boolean {
    return !!this.searchTerm || this.statusFilter.length > 0 || this.priorityFilter.length > 0
      || this.assigneeFilter.length > 0;
  }

  // ---- URL ------------------------------------------------------------------------------------

  private navigate(queryParams: Record<string, string | string[] | number | null>): void {
    this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge', replaceUrl: true });
  }

  applyFilters(): void {
    this.navigate({
      q: this.searchTerm || null,
      status: this.statusFilter.length ? this.statusFilter : null,
      priority: this.priorityFilter.length ? this.priorityFilter : null,
      assignee: this.assigneeFilter.length ? this.assigneeFilter : null,
      page: null,
    });
  }

  clearFilters(): void {
    this.navigate({ q: null, status: null, priority: null, assignee: null, page: null });
  }

  onSearchInput(): void {
    this.searchChange$.next();
  }

  onViewChange(view: ViewMode): void {
    this.navigate({ view: view === 'kanban' ? view : null, page: null });
  }

  onPage(event: PageEvent): void {
    this.navigate({ page: event.pageIndex, size: event.pageSize });
  }

  onSortChange(sort: Sort): void {
    this.navigate({ sort: sort.direction ? `${sort.active},${sort.direction}` : null, page: null });
  }

  // ---- selection ------------------------------------------------------------------------------

  toggle(id: string): void {
    const next = new Set(this.selected());
    if (!next.delete(id)) {
      next.add(id);
    }
    this.selected.set(next);
  }

  isAllSelected(reports: BugReport[]): boolean {
    return reports.length > 0 && reports.every((b) => this.selected().has(b.id));
  }

  toggleAll(reports: BugReport[]): void {
    this.selected.set(this.isAllSelected(reports) ? new Set() : new Set(reports.map((b) => b.id)));
  }

  clearSelection(): void {
    this.selected.set(new Set());
  }

  // ---- changes --------------------------------------------------------------------------------

  /** A drop only asks; cancelling leaves the card in its lane, since nothing moved it yet. */
  onStatusDrop({ bug, newStatus }: BugStatusDrop): void {
    this.askForStatus(newStatus, [bug.id], bug.status).subscribe((request) =>
      this.store.dispatch(BugReportActions.changeBugReportStatus({ projectId: this.projectId, id: bug.id, request })));
  }

  bulkStatus(status: BugReportStatus): void {
    const ids = [...this.selected()];
    this.askForStatus(status, ids).subscribe((request) =>
      this.runBulk({ ids, status, reason: request.reason, resolution: request.resolution,
        duplicateOfId: request.duplicateOfId }));
  }

  bulkAssign(assignee: string | null): void {
    const ids = [...this.selected()];
    this.runBulk(assignee ? { ids, assigneeId: assignee } : { ids, clearAssignee: true });
  }

  bulkAssignToMe(): void {
    this.store.select(selectAuthUser).pipe(take(1)).subscribe((user) => user && this.bulkAssign(user.id));
  }

  bulkPriority(priority: Priority): void {
    this.runBulk({ ids: [...this.selected()], priority });
  }

  bulkDelete(): void {
    const ids = [...this.selected()];
    this.dialog.open(ConfirmDialogComponent, {
      data: {
        titleKey: 'common.delete',
        messageKey: 'bugReport.bulk.deleteConfirm',
        messageParams: { count: ids.length },
        secondaryMessageKey: 'common.irreversibleWarning',
        danger: true,
      } as ConfirmDialogData,
    }).afterClosed().pipe(
      filter(Boolean),
      switchMap(() => this.api.bulkDelete(this.projectId, ids)),
      take(1),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({ next: () => this.afterBulk(), error: (err: HttpErrorResponse) => this.bulkFailed(err) });
  }

  private askForStatus(newStatus: BugReportStatus, bugIds: string[], currentStatus?: BugReportStatus)
    : Observable<ChangeBugStatusRequest> {
    return this.dialog.open(ChangeBugStatusDialogComponent, {
      data: { projectId: this.projectId, newStatus, currentStatus, bugIds } as ChangeBugStatusDialogData,
    }).afterClosed().pipe(
      filter((request): request is ChangeBugStatusRequest => !!request),
      take(1),
      takeUntilDestroyed(this.destroyRef),
    );
  }

  private runBulk(request: BulkUpdateBugReportsRequest): void {
    this.api.bulkUpdate(this.projectId, request).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: () => this.afterBulk(), error: (err: HttpErrorResponse) => this.bulkFailed(err) });
  }

  private afterBulk(): void {
    this.snackBar.open(this.translate.instant('bugReport.bulk.done'), this.translate.instant('common.close'),
      { duration: SNACK_MS });
    this.clearSelection();
    this.reload();
  }

  /** Bulk changes are all or nothing, so the server's reason names what to fix. */
  private bulkFailed(err: HttpErrorResponse): void {
    const message = err.error?.message ?? this.translate.instant('bugReport.bulk.failed');
    this.snackBar.open(message, this.translate.instant('common.close'), { duration: SNACK_MS });
  }
}
