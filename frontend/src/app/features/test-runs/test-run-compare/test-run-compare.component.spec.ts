import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, provideRouter, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestRunApiService } from '../../../core/services/test-run-api.service';
import { RunComparison } from '../../../shared/models/run-comparison.model';
import { TestRunCompareComponent } from './test-run-compare.component';

const COMPARISON: RunComparison = {
  base: { id: 'run-41', key: 'P-Run-41', name: 'nightly', status: 'COMPLETED', happenedAt: '2026-09-18T10:00:00Z' },
  head: { id: 'run-42', key: 'P-Run-42', name: 'nightly', status: 'COMPLETED', happenedAt: '2026-09-19T10:00:00Z' },
  baseAutoSelected: true,
  counts: { newlyFailing: 1, fixed: 0, stillFailing: 0, added: 0, removed: 0, otherChange: 0, unchanged: 3 },
  rows: [{
    category: 'NEWLY_FAILING', testCaseId: 'c1', testCaseKey: 'P-17', title: 'Checkout', baseStatus: 'PASSED',
    headStatus: 'FAILED', baseResultId: 'r1', headResultId: 'r2', versionChanged: false, duplicates: 0,
  }],
};

describe('TestRunCompareComponent', () => {
  let queryParams: BehaviorSubject<ParamMap>;
  let compare: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    TestBed.resetTestingModule();
    queryParams = new BehaviorSubject(convertToParamMap({ head: 'run-42' }));
    compare = vi.fn(() => of(COMPARISON));
    TestBed.configureTestingModule({
      imports: [TestRunCompareComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: TestRunApiService, useValue: { compare, getAll: () => of({ content: [], page: {} }) } },
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: queryParams, parent: { snapshot: { paramMap: convertToParamMap({ id: 'proj' }) } } },
        },
      ],
    });
  });

  function render(): HTMLElement {
    const fixture = TestBed.createComponent(TestRunCompareComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('should ask for the head in the URL and let the server pick the base', () => {
    render();

    expect(compare).toHaveBeenCalledWith('proj', 'run-42', null, false);
  });

  it('should pass an explicit base and the unchanged switch through', () => {
    queryParams.next(convertToParamMap({ head: 'run-42', base: 'run-40', unchanged: 'true' }));

    render();

    expect(compare).toHaveBeenLastCalledWith('proj', 'run-42', 'run-40', true);
  });

  it('should show each non-empty category with its count and open newly failing', () => {
    const root = render();

    expect(root.querySelector('[data-test-id="compare-counts"]')?.textContent).toContain('1');
    expect(root.querySelector('[data-test-id="compare-group-NEWLY_FAILING"]')).not.toBeNull();
    expect(root.querySelector('[data-test-id="compare-row-P-17"]')?.textContent).toContain('Checkout');
    expect(root.querySelector('[data-test-id="compare-auto-base"]')).not.toBeNull();
  });

  it('should re-request when another base is chosen', () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(TestRunCompareComponent);
    fixture.detectChanges();

    fixture.componentInstance.chooseBase('run-39');

    expect(navigate).toHaveBeenCalledWith([], expect.objectContaining({
      queryParams: { base: 'run-39' }, queryParamsHandling: 'merge',
    }));
  });

  it('should swap base and head', () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(TestRunCompareComponent);
    fixture.detectChanges();

    fixture.componentInstance.swap();

    expect(navigate).toHaveBeenCalledWith([], expect.objectContaining({
      queryParams: { head: 'run-41', base: 'run-42' },
    }));
  });
});
