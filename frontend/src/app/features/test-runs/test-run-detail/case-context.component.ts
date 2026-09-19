import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { TestResult } from '../../../shared/models/test-run.model';

/**
 * The case's preconditions and description while executing it (PRD-048). Shown from the live case,
 * like the steps below it; when the case changed since the result was recorded, it says so and
 * links to the case's version history rather than showing text that disagrees with those steps.
 */
@Component({
  selector: 'app-case-context',
  standalone: true,
  imports: [RouterLink, MatIconModule, TranslateModule],
  template: `
    @if (result.testCasePreconditions || result.testCaseDescription) {
      <details class="case-context" [open]="!!result.testCasePreconditions" data-test-id="execution-case-context">
        <summary>{{ 'testRun.execution.caseContext' | translate }}</summary>
        @if (result.testCasePreconditions) {
          <p><strong>{{ 'testCase.form.preconditions' | translate }}:</strong> {{ result.testCasePreconditions }}</p>
        }
        @if (result.testCaseDescription) {
          <p>{{ result.testCaseDescription }}</p>
        }
      </details>
    }
    @if (changedSinceExecution) {
      <p class="case-changed" data-test-id="execution-case-changed">
        <mat-icon aria-hidden="true">history</mat-icon>
        {{ 'testRun.execution.caseChanged' | translate: { executed: result.executedVersion, current: result.testCaseVersion } }}
        <a [routerLink]="['/projects', projectId, 'test-cases', result.testCaseId]">{{ 'version.title' | translate }}</a>
      </p>
    }
  `,
  styles: [`
    .case-context { margin: 0 0 12px; font-size: 14px; }
    .case-context summary { cursor: pointer; font-weight: 500; }
    .case-context p { margin: 6px 0 0; white-space: pre-wrap; }
    .case-changed { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--tm-text-secondary); }
    .case-changed mat-icon { font-size: 16px; width: 16px; height: 16px; }
  `],
})
export class CaseContextComponent {
  @Input({ required: true }) result!: TestResult;
  @Input({ required: true }) projectId!: string;

  get changedSinceExecution(): boolean {
    const { executedVersion, testCaseVersion } = this.result;
    return executedVersion != null && testCaseVersion != null && executedVersion !== testCaseVersion;
  }
}
