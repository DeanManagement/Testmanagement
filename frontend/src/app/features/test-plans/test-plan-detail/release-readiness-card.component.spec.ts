import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { Readiness, ReadinessCriterion } from '../../../shared/models/readiness.model';
import { ReleaseReadinessCardComponent } from './release-readiness-card.component';

const COUNTS = { considered: 4, passed: 3, failed: 1, blocked: 0, skipped: 0, pending: 0 };

function readiness(verdict: Readiness['verdict'], criteria: ReadinessCriterion[]): Readiness {
  return { planId: 'p1', planName: 'Release', verdict, evaluatedAt: '2026-09-19T10:00:00Z', criteria, counts: COUNTS };
}

function render(value: Readiness): HTMLElement {
  TestBed.configureTestingModule({
    imports: [ReleaseReadinessCardComponent, TranslateModule.forRoot()],
    providers: [provideRouter([])],
  });
  const translate = TestBed.inject(TranslateService);
  // Keys, not wording: the catalogue owns the prose.
  translate.setTranslation('en', {
    readiness: {
      verdict: { GO: 'GO', NO_GO: 'NO GO', NO_CRITERIA: 'NO CRITERIA' },
      outcome: { PASS: 'pass', FAIL: 'fail', NOT_APPLICABLE: 'n/a' },
      name: { PASS_RATE: 'pass rate', BLOCKER_BUGS: 'blockers', COVERAGE: 'coverage', FLAKY_TESTS: 'flaky' },
      noCriteria: 'no criteria set',
      counts: '{{passed}} of {{considered}}',
    },
  });
  translate.use('en');
  const fixture = TestBed.createComponent(ReleaseReadinessCardComponent);
  fixture.componentRef.setInput('readiness', value);
  fixture.componentRef.setInput('projectId', 'proj');
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

/** A criterion row as its cells, each with whitespace collapsed. */
const cells = (root: HTMLElement, name: string) =>
  Array.from(root.querySelectorAll(`[data-test-id="readiness-row-${name}"] > *`))
    .map((cell) => cell.textContent?.replace(/\s+/g, ' ').trim());

const text = (root: HTMLElement, testId: string) =>
  root.querySelector(`[data-test-id="${testId}"]`)?.textContent?.replace(/\s+/g, ' ').trim() ?? null;

describe('ReleaseReadinessCardComponent', () => {
  beforeEach(() => TestBed.resetTestingModule());

  describe('when the plan is ready', () => {
    it('should show GO with each criterion passing', () => {
      const root = render(readiness('GO', [{ name: 'PASS_RATE', actual: 98.5, threshold: 98, outcome: 'PASS' }]));

      expect(text(root, 'readiness-verdict')).toBe('GO');
      expect(cells(root, 'PASS_RATE')).toEqual(['pass rate', '98.5 %', '≥ 98 %', 'check_circle pass']);
      expect(text(root, 'readiness-counts')).toBe('3 of 4');
    });
  });

  describe('when a criterion fails', () => {
    it('should show NO GO and which criterion failed', () => {
      const root = render(readiness('NO_GO', [
        { name: 'PASS_RATE', actual: 75, threshold: 98, outcome: 'FAIL' },
        { name: 'BLOCKER_BUGS', actual: 0, threshold: 0, outcome: 'PASS' },
      ]));

      expect(text(root, 'readiness-verdict')).toBe('NO GO');
      expect(cells(root, 'PASS_RATE')).toEqual(['pass rate', '75 %', '≥ 98 %', 'cancel fail']);
      expect(cells(root, 'BLOCKER_BUGS')).toEqual(['blockers', '0', '≤ 0', 'check_circle pass']);
    });
  });

  describe('when a criterion does not apply', () => {
    it('should show no value rather than zero', () => {
      const root = render(readiness('GO', [{ name: 'COVERAGE', threshold: 80, outcome: 'NOT_APPLICABLE' }]));

      expect(cells(root, 'COVERAGE')).toEqual(['coverage', '—', '≥ 80 %', 'remove_circle_outline n/a']);
    });
  });

  describe('when the plan has no gate', () => {
    it('should say so instead of listing criteria', () => {
      const root = render(readiness('NO_CRITERIA', []));

      expect(text(root, 'readiness-verdict')).toBe('NO CRITERIA');
      expect(text(root, 'readiness-no-criteria')).toBe('no criteria set');
      expect(root.querySelector('table')).toBeNull();
    });
  });
});
