import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { provideMockStore } from '@ngrx/store/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RunCasesDialogComponent } from './run-cases-dialog.component';
import { TestRunApiService } from '../../../core/services/test-run-api.service';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { TestResult, TestResultStatus, TestRun } from '../../../shared/models/test-run.model';

function result(id: string, testCaseId: string, status: TestResultStatus): TestResult {
  return { id, testCaseId, testCaseKey: 'K-' + testCaseId, testCaseTitle: 'T', status, stepResults: [] } as unknown as TestResult;
}

/** TM21/TM22: the cases of an open run, reordered, removed and added from one dialog. */
describe('RunCasesDialogComponent', () => {
  const run = { id: 'run1', results: [result('r1', 'c1', 'PENDING'), result('r2', 'c2', 'PASSED')] } as TestRun;
  let api: Record<string, ReturnType<typeof vi.fn>>;
  let confirmed: boolean;

  function create(): RunCasesDialogComponent {
    TestBed.configureTestingModule({
      imports: [RunCasesDialogComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideMockStore({}),
        { provide: MAT_DIALOG_DATA, useValue: { projectId: 'p1', run } },
        { provide: MatDialogRef, useValue: { close: vi.fn() } },
        { provide: TestRunApiService, useValue: api },
        { provide: TestCaseApiService, useValue: { getAll: () => of({ content: [], page: { totalElements: 0 } }), getLabels: () => of([]) } },
      ],
    });
    // Overridden rather than provided: the component's MatDialogModule import would shadow a plain provider.
    TestBed.overrideProvider(MatDialog, { useValue: { open: () => ({ afterClosed: () => of(confirmed) }) } });
    return TestBed.createComponent(RunCasesDialogComponent).componentInstance;
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    confirmed = false;
    api = {
      reorderResults: vi.fn(() => of(run)),
      removeTestCases: vi.fn(() => of(run)),
      addTestCases: vi.fn(() => of(run)),
    };
  });

  it('saves the dropped order as the full list of result ids', () => {
    const component = create();

    component.drop({ previousIndex: 1, currentIndex: 0 } as CdkDragDrop<unknown>);

    expect(api['reorderResults']).toHaveBeenCalledWith('p1', 'run1', ['r2', 'r1']);
  });

  it('assigns one result to the chosen member', () => {
    api['assignResults'] = vi.fn(() => of(run));
    const component = create();

    component.assign(run.results![0], 'u1');

    expect(api['assignResults']).toHaveBeenCalledWith('p1', 'run1', ['r1'], 'u1');
  });

  it('removes a pending case without asking', () => {
    const component = create();

    component.remove(run.results![0]);

    expect(api['removeTestCases']).toHaveBeenCalledWith('p1', 'run1', ['c1'], false);
  });

  it('keeps a recorded result when the tester does not confirm', () => {
    const component = create();

    component.remove(run.results![1]);

    expect(api['removeTestCases']).not.toHaveBeenCalled();
  });

  it('discards a recorded result once the tester confirms', () => {
    confirmed = true;
    const component = create();

    component.remove(run.results![1]);

    expect(api['removeTestCases']).toHaveBeenCalledWith('p1', 'run1', ['c2'], true);
  });

  it('adds the picked cases and clears the selection', () => {
    const component = create();
    component.toAdd.set([{ id: 'c3', key: 'K-3', title: 'New' } as never]);

    component.add();

    expect(api['addTestCases']).toHaveBeenCalledWith('p1', 'run1', ['c3']);
    expect(component.toAdd()).toEqual([]);
  });
});
