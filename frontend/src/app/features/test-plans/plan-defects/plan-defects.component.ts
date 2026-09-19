import { Component, DestroyRef, inject, Input, OnChanges, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LowerCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { take } from 'rxjs/operators';
import { BugReportApiService } from '../../../core/services/bug-report-api.service';
import { BugReport } from '../../../shared/models/bug-report.model';

/**
 * The plan's defects (PRD-047): bugs found in, or linked to, its runs. Renders nothing when there
 * are none, or when the project has bug reports switched off (the API refuses then).
 */
@Component({
  selector: 'app-plan-defects',
  standalone: true,
  imports: [LowerCasePipe, RouterLink, MatIconModule, TranslateModule],
  template: `
    @if (bugs().length) {
      <div class="runs-section tm-card" data-test-id="test-plan-defects">
        <div class="section-header">
          <mat-icon>pest_control</mat-icon>
          <h3>{{ 'testPlan.defects.title' | translate }} ({{ bugs().length }})</h3>
        </div>
        <ul class="defects">
          @for (bug of bugs(); track bug.id) {
            <li [attr.data-test-id]="'plan-defect-' + bug.key">
              <a [routerLink]="['/projects', projectId, 'bug-reports', bug.id]" class="item-link">{{ bug.key }} {{ bug.title }}</a>
              <span class="badge badge--{{ bug.priority | lowercase }} badge--small">{{ 'priority.' + bug.priority | translate }}</span>
              <span class="badge badge--{{ bug.status | lowercase }} badge--small">{{ 'bugReport.status.' + bug.status | translate }}</span>
              @if (bug.testRunId) {
                <span class="found-in">{{ 'testPlan.defects.foundIn' | translate: { run: bug.testRunName } }}</span>
              }
            </li>
          }
        </ul>
      </div>
    }
  `,
  styles: [`
    .defects { list-style: none; margin: 0; padding: 0; }
    .defects li { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 6px 0;
      border-bottom: 1px solid var(--tm-border); }
    .defects li:last-child { border-bottom: none; }
    .item-link { color: inherit; font-weight: 500; text-decoration: none; }
    .item-link:hover { text-decoration: underline; }
    .found-in { font-size: 12px; color: var(--tm-text-secondary); margin-left: auto; }
    .badge--small { font-size: 10px; padding: 1px 6px; }
  `],
})
export class PlanDefectsComponent implements OnChanges {
  private readonly api = inject(BugReportApiService);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) projectId!: string;
  @Input({ required: true }) planId!: string;

  readonly bugs = signal<BugReport[]>([]);

  ngOnChanges(): void {
    this.api.getByTestPlan(this.projectId, this.planId).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (bugs) => this.bugs.set(bugs), error: () => this.bugs.set([]) });
  }
}
