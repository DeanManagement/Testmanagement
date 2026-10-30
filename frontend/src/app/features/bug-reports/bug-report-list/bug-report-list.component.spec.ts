import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { TranslateModule } from '@ngx-translate/core';
import { MatDialog } from '@angular/material/dialog';
import { BehaviorSubject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BugReportListComponent } from './bug-report-list.component';
import { BugReportActions } from '../../../store/bug-report/bug-report.actions';
import { BugReportApiService } from '../../../core/services/bug-report-api.service';
import { ProjectMemberApiService } from '../../../core/services/project-member-api.service';
import { EnvironmentApiService } from '../../../core/services/environment-api.service';
import { TestRunApiService } from '../../../core/services/test-run-api.service';
import { BugStatusApiService } from '../../../core/services/bug-status-api.service';
import { selectAuthUser } from '../../../store/auth/auth.selectors';
import { BugReport, BugStatus, ChangeBugStatusRequest } from '../../../shared/models/bug-report.model';
import { initialBugReportState } from '../../../store/bug-report/bug-report.state';

/** PRD-045: the list reads its filters from the URL, and bulk actions and drops go through the API. */
describe('BugReportListComponent', () => {
  let queryParams: BehaviorSubject<ParamMap>;
  let store: MockStore;
  let navigate: ReturnType<typeof vi.fn>;
  let bulkUpdate: ReturnType<typeof vi.fn>;
  let dialogResult: ChangeBugStatusRequest | undefined;

  const bug = { id: 'b1', key: 'P-BUG-1', status: 'NEW' } as BugReport;
  const STATUSES = [
    { id: 'new', name: 'New', color: '#000000', category: 'NEW', position: 0, archived: false, allowedTargetIds: [] },
    { id: 'triage', name: 'Triage', color: '#000000', category: 'NEW', position: 1, archived: false, allowedTargetIds: [] },
    { id: 'retest', name: 'Ready for retest', color: '#000000', category: 'RESOLVED', position: 0, archived: false,
      allowedTargetIds: [] },
  ] as BugStatus[];

  beforeEach(() => {
    TestBed.resetTestingModule();
    queryParams = new BehaviorSubject(convertToParamMap({}));
    navigate = vi.fn();
    bulkUpdate = vi.fn(() => of({ affected: 1, message: '' }));
    dialogResult = undefined;
    TestBed.configureTestingModule({
      imports: [BugReportListComponent, TranslateModule.forRoot()],
      providers: [
        provideNoopAnimations(),
        provideMockStore({
          initialState: { bugReports: initialBugReportState },
          selectors: [{ selector: selectAuthUser, value: { id: 'me', systemAdmin: false } }],
        }),
        { provide: Router, useValue: { navigate } },
        { provide: BugReportApiService, useValue: { bulkUpdate, bulkDelete: vi.fn(() => of({})), getLabels: () => of([]) } },
        { provide: EnvironmentApiService, useValue: { getAll: () => of([]) } },
        { provide: TestRunApiService, useValue: { getAll: () => of({ content: [] }) } },
        { provide: BugStatusApiService, useValue: { getAll: () => of(STATUSES) } },
        {
          provide: ProjectMemberApiService,
          useValue: { getByProject: () => of([{ userId: 'me', role: 'TESTER', displayName: 'Me' }]) },
        },
        { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(dialogResult) }) } },
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: queryParams, parent: { snapshot: { paramMap: convertToParamMap({ id: 'p1' }) } } },
        },
      ],
    });
    store = TestBed.inject(MockStore);
    vi.spyOn(store, 'dispatch');
  });

  function create(): BugReportListComponent {
    const fixture = TestBed.createComponent(BugReportListComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('loads the page the URL describes', () => {
    queryParams.next(convertToParamMap({ q: 'crash', status: ['NEW', 'OPEN'], assignee: 'none', page: '2', sort: 'key,asc' }));

    create();

    expect(store.dispatch).toHaveBeenCalledWith(BugReportActions.loadBugReports({
      projectId: 'p1',
      query: { q: 'crash', status: ['NEW', 'OPEN'], statusId: [], priority: [], assignee: ['none'], reporter: [], label: [],
        environmentId: undefined, testRunId: undefined, createdFrom: undefined, createdTo: undefined,
        page: 2, size: 50, sort: 'key,asc' },
    }));
  });

  it('reads the reporter, run, label and date filters from the URL', () => {
    queryParams.next(convertToParamMap({ reporter: 'u1', testRunId: 'r1', label: ['ui', 'api'],
      createdFrom: '2026-10-01', createdTo: '2026-10-31' }));

    create();

    expect(store.dispatch).toHaveBeenCalledWith(expect.objectContaining({ query: expect.objectContaining({
      reporter: ['u1'], testRunId: 'r1', label: ['ui', 'api'], createdFrom: '2026-10-01', createdTo: '2026-10-31',
    }) }));
  });

  it('shows a category link from a dashboard as all statuses of that category', () => {
    queryParams.next(convertToParamMap({ status: 'NEW' }));

    const list = create();

    expect(list.pickedStatusIds).toEqual(['new', 'triage']);
  });

  it('replaces a category link with the statuses picked in the filter', () => {
    queryParams.next(convertToParamMap({ status: 'NEW' }));
    const list = create();

    list.onStatusIdsPicked(['retest']);

    expect(navigate).toHaveBeenCalledWith([], expect.objectContaining({
      queryParams: expect.objectContaining({ status: null, statusId: ['retest'], page: null }),
    }));
  });

  it('loads the whole filter onto the board', () => {
    queryParams.next(convertToParamMap({ view: 'kanban', page: '3' }));

    create();

    expect(store.dispatch).toHaveBeenCalledWith(expect.objectContaining({ query: expect.objectContaining({ page: 0, size: 200 }) }));
  });

  it('writes the filters to the URL and starts again at the first page', () => {
    const list = create();
    list.statusFilter = ['CLOSED'];
    list.assigneeFilter = ['me'];
    list.labelFilter = ['ui'];
    list.createdFrom = '2026-10-01';

    list.applyFilters();

    expect(navigate).toHaveBeenCalledWith([], expect.objectContaining({
      queryParams: { q: null, status: ['CLOSED'], statusId: null, priority: null, assignee: ['me'], environmentId: null,
        reporter: null, testRunId: null, label: ['ui'], createdFrom: '2026-10-01', createdTo: null, page: null },
    }));
  });

  it('forgets the selection when the query changes', () => {
    const list = create();
    list.toggle('b1');

    queryParams.next(convertToParamMap({ q: 'other' }));

    expect(list.selected().size).toBe(0);
  });

  it('assigns every selected bug in one request', () => {
    const list = create();
    list.toggle('b1');
    list.toggle('b2');

    list.bulkAssign('u7');

    expect(bulkUpdate).toHaveBeenCalledWith('p1', { ids: ['b1', 'b2'], assigneeId: 'u7' });
  });

  it('changes nothing when the status dialog after a drop is cancelled', () => {
    const list = create();
    vi.mocked(store.dispatch).mockClear();

    list.onStatusDrop({ bug, newStatus: 'CLOSED' });

    expect(store.dispatch).not.toHaveBeenCalled();
  });

  it('changes the status with the dialog answer after a drop', () => {
    const list = create();
    dialogResult = { status: 'CLOSED', reason: 'fixed in 2.1', resolution: 'FIXED', duplicateOfId: null };

    list.onStatusDrop({ bug, newStatus: 'CLOSED' });

    expect(store.dispatch).toHaveBeenCalledWith(
      BugReportActions.changeBugReportStatus({ projectId: 'p1', id: 'b1', request: dialogResult }));
  });
});
