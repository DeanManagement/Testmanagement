import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ActivityFeedComponent } from './activity-feed.component';
import { ActivityApiService } from '../../../core/services/activity-api.service';
import { ProjectMemberApiService } from '../../../core/services/project-member-api.service';

/** PRD-046: the Activity page reads its filters from the URL and asks for whole days. */
describe('ActivityFeedComponent', () => {
  let queryParams: BehaviorSubject<ParamMap>;
  let getActivity: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    TestBed.resetTestingModule();
    queryParams = new BehaviorSubject(convertToParamMap({}));
    getActivity = vi.fn(() => of({ content: [], page: { number: 0, size: 20, totalElements: 0, totalPages: 0 } }));
    navigate = vi.fn();
    TestBed.configureTestingModule({
      imports: [ActivityFeedComponent, TranslateModule.forRoot()],
      providers: [
        provideNoopAnimations(),
        { provide: Router, useValue: { navigate } },
        { provide: ActivityApiService, useValue: { getActivity, exportCsv: vi.fn() } },
        { provide: ProjectMemberApiService, useValue: { getByProject: () => of([]) } },
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: queryParams, snapshot: { paramMap: convertToParamMap({ id: 'p1' }) } },
        },
      ],
    });
  });

  function create(): ActivityFeedComponent {
    const fixture = TestBed.createComponent(ActivityFeedComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('asks for the filters in the URL, with "to" covering its whole day', () => {
    queryParams.next(convertToParamMap({ from: '2026-09-01', to: '2026-09-02', type: ['BUG_REPORT'], action: 'UPDATED', sort: 'asc' }));

    create();

    const query = getActivity.mock.calls.at(-1)![1];
    expect(query).toMatchObject({ entityType: ['BUG_REPORT'], action: ['UPDATED'], sort: 'asc', page: 0 });
    expect(new Date(query.from).getTime()).toBe(new Date(2026, 8, 1).getTime());
    expect(new Date(query.to).getTime()).toBe(new Date(2026, 8, 3).getTime());
  });

  it('writes the filters to the URL', () => {
    const feed = create();
    feed.userFilter = ['u1'];
    feed.sort = 'asc';

    feed.applyFilters();

    expect(navigate).toHaveBeenCalledWith([], expect.objectContaining({
      queryParams: { from: null, to: null, user: ['u1'], type: null, action: null, sort: 'asc' },
    }));
  });

  it('stops offering more after the last page', () => {
    getActivity.mockReturnValue(of({ content: [{ id: 'e1', action: 'CREATED', entityType: 'TEST_CASE', changes: [], link: null }], page: { number: 0, size: 20, totalElements: 1, totalPages: 1 } }));

    const feed = create();

    expect(feed.hasMore).toBe(false);
  });
});
