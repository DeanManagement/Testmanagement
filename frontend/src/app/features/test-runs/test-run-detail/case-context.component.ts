import { Component, DestroyRef, inject, Input, OnChanges, signal, SimpleChanges } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { take } from 'rxjs/operators';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { preconditionLinks, TestCaseLink } from '../../../shared/models/test-case-link.model';
import { TestResult, TestResultStatus } from '../../../shared/models/test-run.model';
import { MarkdownPipe } from '../../../shared/pipes/markdown.pipe';

/**
 * The case's preconditions and description while executing it (PRD-048). Shown from the live case,
 * like the steps below it; when the case changed since the result was recorded, it says so and
 * links to the case's version history rather than showing text that disagrees with those steps.
 */
@Component({
  selector: 'app-case-context',
  standalone: true,
  imports: [RouterLink, MatIconModule, TranslateModule, MarkdownPipe],
  template: `
    @if (preconditions().length) {
      <div class="run-first" data-test-id="execution-run-first">
        <strong>{{ 'testCase.links.runFirst' | translate }}:</strong>
        @for (link of preconditions(); track link.id) {
          <span class="run-first-case" [attr.data-test-id]="'execution-run-first-' + link.testCaseKey">
            <a [routerLink]="['/projects', projectId, 'test-cases', link.testCaseId]">{{ link.testCaseKey }} {{ link.testCaseTitle }}</a>
            @if (statusInRun(link.testCaseId); as status) {
              <span class="badge badge--{{ status.toLowerCase() }} badge--small">{{ 'resultStatus.' + status | translate }}</span>
            } @else {
              <span class="not-in-run">{{ 'testCase.links.notInRun' | translate }}</span>
            }
          </span>
        }
      </div>
    }
    @if (result.testCasePreconditions || result.testCaseDescription) {
      <details class="case-context" [open]="!!result.testCasePreconditions" data-test-id="execution-case-context">
        <summary>{{ 'testRun.execution.caseContext' | translate }}</summary>
        @if (result.testCasePreconditions) {
          <strong>{{ 'testCase.form.preconditions' | translate }}:</strong>
          <div class="tm-markdown" [innerHTML]="result.testCasePreconditions | markdown" data-test-id="execution-case-preconditions"></div>
        }
        @if (result.testCaseDescription) {
          <div class="tm-markdown" [innerHTML]="result.testCaseDescription | markdown" data-test-id="execution-case-description"></div>
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
    .run-first { display: flex; flex-wrap: wrap; align-items: center; gap: 4px 12px; margin: 0 0 12px; font-size: 14px; }
    .run-first-case { display: inline-flex; align-items: center; gap: 6px; }
    .badge--small { font-size: 10px; padding: 1px 6px; }
    .not-in-run { color: var(--tm-text-secondary); font-size: 12px; }
    .case-context { margin: 0 0 12px; font-size: 14px; }
    .case-context summary { cursor: pointer; font-weight: 500; }
    .case-context strong { display: block; margin-top: 6px; }
    .case-context .tm-markdown { margin-top: 6px; }
    .case-context strong + .tm-markdown { margin-top: 2px; }
    .case-changed { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--tm-text-secondary); }
    .case-changed mat-icon { font-size: 16px; width: 16px; height: 16px; }
  `],
})
export class CaseContextComponent implements OnChanges {
  private readonly testCaseApi = inject(TestCaseApiService);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) result!: TestResult;
  @Input({ required: true }) projectId!: string;
  /** TM07: the run's results, to show how this case's precondition cases fared in the same run. */
  @Input() runResults: TestResult[] = [];

  readonly preconditions = signal<TestCaseLink[]>([]);

  ngOnChanges(changes: SimpleChanges): void {
    const previous = changes['result']?.previousValue as TestResult | undefined;
    if (changes['result'] && previous?.testCaseId !== this.result.testCaseId) {
      this.preconditions.set([]);
      this.testCaseApi.getLinks(this.projectId, this.result.testCaseId)
        .pipe(take(1), takeUntilDestroyed(this.destroyRef))
        .subscribe((links) => this.preconditions.set(preconditionLinks(links)));
    }
  }

  /** The case's latest recorded status in this run; PENDING when not yet executed, null when not in it. */
  statusInRun(testCaseId: string): TestResultStatus | null {
    const results = this.runResults.filter((r) => r.testCaseId === testCaseId);
    if (!results.length) return null;
    const executed = results.filter((r) => r.executedAt).sort((a, b) => b.executedAt!.localeCompare(a.executedAt!));
    return (executed[0] ?? results[0]).status;
  }

  get changedSinceExecution(): boolean {
    const { executedVersion, testCaseVersion } = this.result;
    return executedVersion != null && testCaseVersion != null && executedVersion !== testCaseVersion;
  }
}
