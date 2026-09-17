import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';
import { provideEffects } from '@ngrx/effects';
import { provideStore, Store } from '@ngrx/store';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestRunApiService } from '../../core/services/test-run-api.service';
import { StepResult, TestRun } from '../../shared/models/test-run.model';
import { TestRunActions } from './test-run.actions';
import { TestRunEffects } from './test-run.effects';
import { testRunReducer } from './test-run.reducer';

/**
 * A run page opened directly — a bookmark, a reload, a link in a notification — never loads the
 * run list, and the list was the only thing that told the store which project it was in. Everything
 * that read the project back from the store then worked with `null`.
 */
describe('test run store, when a run is opened by deep link', () => {
  const PROJECT = 'project-1';
  const RUN = 'run-1';
  const run = { id: RUN, results: [] } as unknown as TestRun;

  let store: Store;
  let api: { getById: ReturnType<typeof vi.fn>; updateStepResult: ReturnType<typeof vi.fn>; delete: ReturnType<typeof vi.fn>; create: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    api = {
      getById: vi.fn().mockReturnValue(of(run)),
      updateStepResult: vi.fn().mockReturnValue(of({ id: 'step-1', status: 'PASSED' } as StepResult)),
      delete: vi.fn().mockReturnValue(of(undefined)),
      create: vi.fn().mockReturnValue(of(run)),
    };
    router = { navigate: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideStore({ testRuns: testRunReducer }),
        provideEffects(TestRunEffects),
        { provide: TestRunApiService, useValue: api },
        { provide: Router, useValue: router },
        { provide: MatSnackBar, useValue: { open: vi.fn() } },
        { provide: TranslateService, useValue: { instant: (key: string) => key } },
      ],
    });
    store = TestBed.inject(Store);

    // The deep link: only the single run is loaded, never the list.
    store.dispatch(TestRunActions.loadTestRun({ projectId: PROJECT, id: RUN }));
    api.getById.mockClear();
  });

  it('should reload the run from its own project after a step is recorded, so the case status derived on the server shows up', () => {
    store.dispatch(TestRunActions.updateStepResult({
      projectId: PROJECT, runId: RUN, resultId: 'result-1', stepResultId: 'step-1',
      request: { status: 'PASSED', actualResult: '' },
    }));

    expect(api.getById).toHaveBeenCalledTimes(1);
    expect(api.getById).toHaveBeenCalledWith(PROJECT, RUN);
  });

  it('should return to the project\'s run list after deleting the run', () => {
    store.dispatch(TestRunActions.deleteTestRun({ projectId: PROJECT, id: RUN }));

    expect(router.navigate).toHaveBeenCalledWith(['/projects', PROJECT, 'test-runs']);
  });
});

describe('test run store, when a run is created from a directly opened form', () => {
  it('should open the new run inside its project', () => {
    const created = { id: 'run-9', results: [] } as unknown as TestRun;
    const router = { navigate: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideStore({ testRuns: testRunReducer }),
        provideEffects(TestRunEffects),
        { provide: TestRunApiService, useValue: { create: vi.fn().mockReturnValue(of(created)) } },
        { provide: Router, useValue: router },
        { provide: MatSnackBar, useValue: { open: vi.fn() } },
        { provide: TranslateService, useValue: { instant: (key: string) => key } },
      ],
    });

    TestBed.inject(Store).dispatch(TestRunActions.createTestRun({
      projectId: 'project-1', request: { name: 'Smoke' } as never,
    }));

    expect(router.navigate).toHaveBeenCalledWith(['/projects', 'project-1', 'test-runs', 'run-9']);
  });
});
