import { Component, Input } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { StepResult, TestResult } from '../../../shared/models/test-run.model';

/**
 * What went wrong on a finished run, above everything else.
 *
 * <p>A completed run is opened to answer one question — what broke? Before this the results were
 * listed in execution order as collapsed panels, so the failures had to be hunted for among the
 * passes, and the executor's comment, which carries the actual finding and is what CI ingestion and
 * the MCP tools populate, was rendered nowhere at all.
 *
 * <p>Its own component rather than more markup in the run detail: that template is already the
 * largest in the app and its stylesheet was within 60 bytes of the build budget. This also makes
 * the block available to the run report, where the same question is asked.
 */
@Component({
  selector: 'app-run-failure-summary',
  standalone: true,
  imports: [LowerCasePipe, RouterLink, MatIconModule, TranslateModule],
  template: `
    @if (failures.length) {
      <div class="failure-summary" data-test-id="test-run-failure-summary">
        <div class="failure-summary-header">
          <mat-icon>error</mat-icon>
          <h3>{{ 'testRun.failures.title' | translate: { count: failures.length } }}</h3>
          <span>{{ 'testRun.failures.outOf' | translate: { total: totalResults } }}</span>
        </div>
        <ul class="failure-list">
          @for (result of failures; track result.id) {
            <li class="failure-item" [attr.data-test-id]="'failure-item-' + result.id">
              <span class="badge badge--{{ result.status | lowercase }} badge--small">
                {{ 'resultStatus.' + result.status | translate }}
              </span>
              <a [routerLink]="['/projects', projectId, 'test-cases', result.testCaseId]" class="item-link">
                {{ result.testCaseTitle }}
              </a>
              @if (result.comment) {
                <p [attr.data-test-id]="'failure-comment-' + result.id">{{ result.comment }}</p>
              }
              @if (firstFailedStep(result); as step) {
                <p>
                  <strong>{{ 'stepResult.step' | translate }} {{ step.orderIndex + 1 }}:</strong>
                  {{ step.actualResult || step.action }}
                </p>
              }
              @if (result.defectLink) {
                <a [href]="result.defectLink" target="_blank" rel="noopener noreferrer"
                   class="defect-link" [attr.data-test-id]="'failure-defect-' + result.id">
                  <mat-icon>bug_report</mat-icon>
                  {{ 'testRun.failures.defect' | translate }}
                </a>
              }
            </li>
          }
        </ul>
      </div>
    }
  `,
  styleUrl: './run-failure-summary.component.scss',
})
export class RunFailureSummaryComponent {
  @Input({ required: true }) failures: TestResult[] = [];
  @Input({ required: true }) totalResults = 0;
  @Input({ required: true }) projectId = '';

  /**
   * The first step that failed, which is usually where the story starts — a later failure is often
   * just fallout from this one.
   */
  firstFailedStep(result: TestResult): StepResult | undefined {
    return [...result.stepResults]
      .sort((a, b) => a.orderIndex - b.orderIndex)
      .find((step) => step.status === 'FAILED' || step.status === 'BLOCKED');
  }
}
