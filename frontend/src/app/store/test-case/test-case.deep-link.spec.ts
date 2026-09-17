import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';
import { provideEffects } from '@ngrx/effects';
import { provideStore, Store } from '@ngrx/store';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { TestCaseApiService } from '../../core/services/test-case-api.service';
import { TestCase } from '../../shared/models/test-case.model';
import { TestCaseActions } from './test-case.actions';
import { TestCaseEffects } from './test-case.effects';
import { testCaseReducer } from './test-case.reducer';

/** Same defect as the test run store: only the list told the store which project it was in. */
describe('test case store, when a test case is opened by deep link', () => {
  it('should return to the project\'s test case list after deleting it', () => {
    const router = { navigate: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideStore({ testCases: testCaseReducer }),
        provideEffects(TestCaseEffects),
        {
          provide: TestCaseApiService,
          useValue: {
            getById: vi.fn().mockReturnValue(of({ id: 'case-1' } as TestCase)),
            delete: vi.fn().mockReturnValue(of(undefined)),
          },
        },
        { provide: Router, useValue: router },
        { provide: MatSnackBar, useValue: { open: vi.fn() } },
        { provide: TranslateService, useValue: { instant: (key: string) => key } },
      ],
    });
    const store = TestBed.inject(Store);
    store.dispatch(TestCaseActions.loadTestCase({ projectId: 'project-1', id: 'case-1' }));

    store.dispatch(TestCaseActions.deleteTestCase({ projectId: 'project-1', id: 'case-1' }));

    expect(router.navigate).toHaveBeenCalledWith(['/projects', 'project-1', 'test-cases']);
  });
});
