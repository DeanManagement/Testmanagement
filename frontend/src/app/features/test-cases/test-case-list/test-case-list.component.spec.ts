import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestCaseListComponent } from './test-case-list.component';
import { TestCaseActions } from '../../../store/test-case/test-case.actions';
import {
  selectAllTestCases, selectHasSelection, selectSelectedTestCaseIds, selectTestCasePage, selectTestCasesLoading,
} from '../../../store/test-case/test-case.selectors';
import { selectFolderTree } from '../../../store/test-case-folder/test-case-folder.selectors';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { ProjectApiService } from '../../../core/services/project-api.service';
import { TestSuiteApiService } from '../../../core/services/test-suite-api.service';
import { CustomFieldApiService } from '../../../core/services/custom-field-api.service';
import { TestCase } from '../../../shared/models/test-case.model';

/** PRD-052: the label filter lives in the URL, and an empty list says why it is empty. */
describe('TestCaseListComponent', () => {
  let queryParams: BehaviorSubject<ParamMap>;
  let store: MockStore;
  let navigate: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    TestBed.resetTestingModule();
    queryParams = new BehaviorSubject(convertToParamMap({}));
    navigate = vi.fn();
    TestBed.configureTestingModule({
      imports: [TestCaseListComponent, TranslateModule.forRoot()],
      providers: [
        provideNoopAnimations(),
        provideMockStore({
          selectors: [
            { selector: selectAllTestCases, value: [] },
            { selector: selectTestCasesLoading, value: false },
            { selector: selectSelectedTestCaseIds, value: [] },
            { selector: selectHasSelection, value: false },
            { selector: selectFolderTree, value: [] },
            { selector: selectTestCasePage, value: null },
          ],
        }),
        { provide: Router, useValue: { navigate } },
        { provide: TestCaseApiService, useValue: { getLabels: () => of(['negativ', 'smoke']) } },
        { provide: ProjectApiService, useValue: { getById: () => of({ reviewRequired: false }) } },
        { provide: TestSuiteApiService, useValue: {} },
        { provide: CustomFieldApiService, useValue: { getActive: () => of([]) } },
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: queryParams,
            get snapshot() {
              return { queryParamMap: queryParams.value };
            },
            parent: { snapshot: { paramMap: convertToParamMap({ id: 'p1' }) } },
          },
        },
      ],
    });
    store = TestBed.inject(MockStore);
    vi.spyOn(store, 'dispatch');
  });

  function create() {
    const fixture = TestBed.createComponent(TestCaseListComponent);
    fixture.detectChanges();
    return { component: fixture.componentInstance, element: fixture.nativeElement as HTMLElement };
  }

  function lastNavigation(): Record<string, unknown> {
    return navigate.mock.calls.at(-1)![1].queryParams;
  }

  it('loads the labels named in the URL', () => {
    queryParams.next(convertToParamMap({ label: ['smoke', 'negativ'] }));

    create();

    expect(store.dispatch).toHaveBeenCalledWith(TestCaseActions.loadTestCases({
      projectId: 'p1',
      query: expect.objectContaining({ label: ['smoke', 'negativ'] }),
    }));
  });

  it('adds a clicked label chip to the filter in the URL', () => {
    queryParams.next(convertToParamMap({ label: 'smoke' }));
    const { component } = create();

    component.addLabelFilter('negativ');

    expect(lastNavigation()['label']).toEqual(['smoke', 'negativ']);
  });

  it('does not add a label twice', () => {
    queryParams.next(convertToParamMap({ label: 'smoke' }));
    const { component } = create();

    component.addLabelFilter('smoke');

    expect(navigate).not.toHaveBeenCalled();
  });

  it('says nothing matches when a filter is set, and offers to reset it', () => {
    queryParams.next(convertToParamMap({ q: 'login', label: 'smoke', 'cf.Team': 'Web' }));
    const { component, element } = create();

    expect(element.querySelector('[data-test-id="test-case-list-no-match"]')).not.toBeNull();
    component.resetFilters();
    expect(lastNavigation()).toEqual(expect.objectContaining({ q: null, label: null, 'cf.Team': null }));
  });

  it('says a folder is empty when only a folder is selected', () => {
    queryParams.next(convertToParamMap({ folderId: 'f1' }));

    const { element } = create();

    expect(element.querySelector('[data-test-id="test-case-list-empty"]')?.textContent).toContain('testCase.list.folderEmpty');
  });

  it('invites to create the first case in an empty project', () => {
    const { element } = create();

    expect(element.querySelector('[data-test-id="test-case-list-empty"]')?.textContent).toContain('testCase.list.empty');
    expect(element.querySelector('[data-test-id="test-case-create-empty-btn"]')).not.toBeNull();
  });

  it('turns every label chip into a filter button', () => {
    store.overrideSelector(selectAllTestCases, [{ id: 'c1', key: 'P-1', title: 'Login', labels: ['smoke'] } as TestCase]);
    store.overrideSelector(selectTestCasePage, { size: 50, number: 0, totalElements: 1, totalPages: 1 });
    const { component, element } = create();
    const addLabel = vi.spyOn(component, 'addLabelFilter');

    (element.querySelector('[data-test-id="test-case-label"]') as HTMLButtonElement).click();

    expect(addLabel).toHaveBeenCalledWith('smoke');
  });
});
