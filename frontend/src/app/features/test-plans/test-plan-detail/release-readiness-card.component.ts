import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { CriterionName, Readiness, ReadinessCriterion } from '../../../shared/models/readiness.model';

/** Where a criterion's detail lives, relative to the project; null when it is on this page already. */
const CRITERION_LINKS: Record<CriterionName, string | null> = {
  PASS_RATE: null,
  BLOCKER_BUGS: 'bug-reports',
  COVERAGE: 'requirements',
  FLAKY_TESTS: 'dashboard',
};

/**
 * A test plan's verdict against its release gate (PRD-037 §3.4), one row per configured criterion.
 * The pass rate here is "effective": each case's latest result, which is why it can differ from the
 * plan's overall pass rate, which counts every result.
 */
@Component({
  selector: 'app-release-readiness-card',
  standalone: true,
  imports: [RouterLink, MatIconModule, MatTooltipModule, TranslateModule],
  template: `
    @let r = readiness();
    <section class="tm-card readiness-card" data-test-id="test-plan-readiness">
      <div class="readiness-header">
        <h3>{{ 'readiness.title' | translate }}</h3>
        <span class="verdict verdict--{{ r.verdict }}" data-test-id="readiness-verdict">
          {{ 'readiness.verdict.' + r.verdict | translate }}
        </span>
      </div>

      @if (r.verdict === 'NO_CRITERIA') {
        <p class="hint" data-test-id="readiness-no-criteria">{{ 'readiness.noCriteria' | translate }}</p>
      } @else {
        <table class="criteria">
          <thead>
            <tr>
              <th scope="col">{{ 'readiness.criterion' | translate }}</th>
              <th scope="col">{{ 'readiness.actual' | translate }}</th>
              <th scope="col">{{ 'readiness.threshold' | translate }}</th>
              <th scope="col"><span class="visually-hidden">{{ 'readiness.outcomeColumn' | translate }}</span></th>
            </tr>
          </thead>
          <tbody>
            @for (criterion of r.criteria; track criterion.name) {
              <tr [attr.data-test-id]="'readiness-row-' + criterion.name">
                <th scope="row">
                  @if (linkFor(criterion); as link) {
                    <a class="criterion-link" [routerLink]="['/projects', projectId(), link]">{{ 'readiness.name.' + criterion.name | translate }}</a>
                  } @else {
                    <span [matTooltip]="criterion.name === 'PASS_RATE' ? ('readiness.effectiveHint' | translate) : ''">
                      {{ 'readiness.name.' + criterion.name | translate }}
                    </span>
                  }
                </th>
                <td>{{ format(criterion, criterion.actual) }}</td>
                <td>{{ isPercent(criterion) ? '≥' : '≤' }} {{ format(criterion, criterion.threshold) }}</td>
                <td class="outcome outcome--{{ criterion.outcome }}">
                  <mat-icon aria-hidden="true">{{ icon(criterion) }}</mat-icon>
                  {{ 'readiness.outcome.' + criterion.outcome | translate }}
                </td>
              </tr>
            }
          </tbody>
        </table>
        <p class="hint" data-test-id="readiness-counts">
          {{ 'readiness.counts' | translate: r.counts }}
        </p>
      }
    </section>
  `,
  styles: [`
    .readiness-card { padding: 20px 24px; margin-bottom: 24px; }
    .readiness-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
    h3 { margin: 0; font-size: 16px; font-weight: 600; }
    .verdict { font-weight: 700; font-size: 0.85rem; padding: 4px 12px; border-radius: 999px; letter-spacing: 0.04em; }
    .verdict--GO { background: var(--tm-success); color: var(--tm-on-success); }
    .verdict--NO_GO { background: var(--tm-accent); color: var(--tm-on-accent); }
    .verdict--NO_CRITERIA { background: var(--tm-subtle-bg); color: var(--tm-text-secondary); }
    .criteria { width: 100%; border-collapse: collapse; margin-top: 12px; }
    .criteria th, .criteria td { text-align: left; padding: 8px 4px; border-bottom: 1px solid var(--tm-border); }
    .criteria thead th { font-size: 0.75rem; color: var(--tm-text-secondary); font-weight: 500; }
    .criteria tbody th { font-weight: 500; }
    .criterion-link { color: inherit; text-decoration: underline; text-underline-offset: 2px; }
    .criterion-link:hover { text-decoration-thickness: 2px; }
    .outcome { display: flex; align-items: center; gap: 4px; font-weight: 500; }
    .outcome mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .outcome--PASS { color: var(--tm-success); }
    .outcome--FAIL { color: var(--tm-accent); }
    .outcome--NOT_APPLICABLE { color: var(--tm-text-secondary); }
    .hint { margin: 12px 0 0; font-size: 0.85rem; color: var(--tm-text-secondary); }
    .visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
  `],
})
export class ReleaseReadinessCardComponent {
  readonly readiness = input.required<Readiness>();
  readonly projectId = input.required<string>();

  linkFor(criterion: ReadinessCriterion): string | null {
    return CRITERION_LINKS[criterion.name];
  }

  isPercent(criterion: ReadinessCriterion): boolean {
    return criterion.name === 'PASS_RATE' || criterion.name === 'COVERAGE';
  }

  /** Percentages with up to two decimals and a % sign; counts as whole numbers; no value as an em dash. */
  format(criterion: ReadinessCriterion, value: number | undefined): string {
    if (value === undefined || value === null) {
      return '—';
    }
    return this.isPercent(criterion) ? `${Number(value.toFixed(2))} %` : String(value);
  }

  icon(criterion: ReadinessCriterion): string {
    switch (criterion.outcome) {
      case 'PASS':
        return 'check_circle';
      case 'FAIL':
        return 'cancel';
      default:
        return 'remove_circle_outline';
    }
  }
}
