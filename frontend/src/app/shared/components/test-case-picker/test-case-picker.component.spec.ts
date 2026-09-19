import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideMockStore } from '@ngrx/store/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestCasePickerComponent } from './test-case-picker.component';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { selectFolderTree } from '../../../store/test-case-folder/test-case-folder.selectors';
import { TestCase, TestCaseQuery } from '../../models/test-case.model';

/** PRD-052: one case picker for runs and suites, filtered on the server. */
describe('TestCasePickerComponent', () => {
  let fixture: ComponentFixture<TestCasePickerComponent>;
  let picker: TestCasePickerComponent;
  let getAll: ReturnType<typeof vi.fn>;
  let found: TestCase[];
  let total: number;

  const testCase = (id: string): TestCase => ({ id, key: `P-${id}`, title: `Case ${id}` }) as TestCase;

  beforeEach(() => {
    vi.useFakeTimers();
    found = [testCase('1'), testCase('2')];
    total = 2;
    getAll = vi.fn(() => of({ content: found, page: { size: 200, number: 0, totalElements: total, totalPages: 1 } }));
    TestBed.configureTestingModule({
      imports: [TestCasePickerComponent, TranslateModule.forRoot()],
      providers: [
        provideNoopAnimations(),
        provideMockStore({ selectors: [{ selector: selectFolderTree, value: [] }] }),
        { provide: TestCaseApiService, useValue: { getAll, getLabels: () => of(['smoke']) } },
      ],
    });
    fixture = TestBed.createComponent(TestCasePickerComponent);
    picker = fixture.componentInstance;
    fixture.componentRef.setInput('projectId', 'p1');
  });

  afterEach(() => vi.useRealTimers());

  /** Lets the debounced search run and renders the result. */
  async function settle(): Promise<void> {
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(400);
    fixture.detectChanges();
  }

  function byTestId(id: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-test-id="${id}"]`);
  }

  it('sends search, folder and labels to the server', async () => {
    await settle();
    picker.search.set('login');
    picker.folderId.set('f1');
    picker.labels.set(['smoke']);

    await settle();

    const query = getAll.mock.calls.at(-1)![1] as TestCaseQuery;
    expect(query).toEqual(expect.objectContaining({ q: 'login', folderId: 'f1', includeSubfolders: true, label: ['smoke'] }));
  });

  it('keeps a selected case the filters hide under "Selected"', async () => {
    fixture.componentRef.setInput('selected', [{ id: '9', key: 'P-9', title: 'Hidden' }]);

    await settle();

    expect(byTestId('tc-picker-hidden-selected')?.textContent).toContain('Hidden');
  });

  it('selects every case shown, once each', async () => {
    fixture.componentRef.setInput('selected', [{ id: '1', key: 'P-1', title: 'Case 1' }]);
    await settle();

    picker.selectAllVisible();

    expect(picker.selected().map((tc) => tc.id)).toEqual(['1', '2']);
  });

  it('toggles a case in and out of the selection', async () => {
    await settle();

    picker.toggle(found[0]);
    picker.toggle(found[1]);
    picker.toggle(found[0]);

    expect(picker.selected().map((tc) => tc.id)).toEqual(['2']);
  });

  it('says when not every match is shown', async () => {
    total = 340;

    await settle();

    expect(byTestId('tc-picker-truncated')).not.toBeNull();
  });

  it('tells "no match" from "no cases yet"', async () => {
    found = [];
    total = 0;
    await settle();
    expect(byTestId('tc-picker-empty')).not.toBeNull();

    picker.search.set('nothing like this');
    await settle();

    expect(byTestId('tc-picker-no-match')).not.toBeNull();
  });
});
