import { Component, DestroyRef, inject, Input, OnChanges, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { take } from 'rxjs/operators';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { TestCase, TestCaseContext } from '../../../shared/models/test-case.model';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';

/**
 * The line under a case's title (PRD-050): its folder path, who created and last changed it, and the
 * suites that include it. Loads on its own; a failure leaves the rest of the page alone.
 */
@Component({
  selector: 'app-case-context',
  standalone: true,
  imports: [RouterLink, MatIconModule, TranslateModule, LocalizedDatePipe],
  template: `
    @if (context(); as ctx) {
      <div class="case-context" data-test-id="case-context">
        <span class="folder-path" data-test-id="case-folder-path">
          <mat-icon aria-hidden="true">folder</mat-icon>
          @for (folder of ctx.folderPath; track folder.id; let last = $last) {
            <a [routerLink]="['/projects', projectId, 'test-cases']" [queryParams]="{ folderId: folder.id }">{{ folder.name }}</a>
            @if (!last) { <span aria-hidden="true">/</span> }
          } @empty {
            {{ 'testCase.context.noFolder' | translate }}
          }
        </span>
        <span data-test-id="case-created">
          {{ 'testCase.context.created' | translate: { name: ctx.createdByName ?? ('activity.system' | translate), date: (testCase.createdAt | localizedDate:'medium') } }}
        </span>
        <span data-test-id="case-updated">
          {{ 'testCase.context.updated' | translate: { name: ctx.updatedByName ?? ('activity.system' | translate), date: (testCase.updatedAt | localizedDate:'medium') } }}
        </span>
      </div>
      <div class="case-suites" data-test-id="case-suites">
        <span class="label">{{ 'testCase.context.suites' | translate }}:</span>
        @for (suite of ctx.suites; track suite.id) {
          <a class="suite-chip" [routerLink]="['/projects', projectId, 'test-suites', suite.id]">{{ suite.name }}</a>
        } @empty {
          <span class="none">{{ 'testCase.context.noSuites' | translate }}</span>
        }
      </div>
    } @else if (failed()) {
      <p class="case-context" role="status">{{ 'testCase.context.unavailable' | translate }}</p>
    }
  `,
  styles: [`
    .case-context, .case-suites { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 16px;
      font-size: 13px; color: var(--tm-text-secondary); margin: 4px 0; }
    .folder-path { display: inline-flex; align-items: center; gap: 4px; }
    .folder-path mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .case-context a, .case-suites a { color: inherit; }
    .case-suites { gap: 6px; }
    .label { font-weight: 500; }
    .suite-chip { padding: 1px 8px; border: 1px solid var(--tm-border); border-radius: 12px; text-decoration: none; }
    .suite-chip:hover { border-color: var(--tm-primary); }
  `],
})
export class CaseContextComponent implements OnChanges {
  private readonly api = inject(TestCaseApiService);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) projectId!: string;
  @Input({ required: true }) testCase!: TestCase;

  readonly context = signal<TestCaseContext | null>(null);
  readonly failed = signal(false);

  /** Reloads when the case changes, e.g. after an edit moves it to another folder. */
  ngOnChanges(): void {
    this.api.getContext(this.projectId, this.testCase.id).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (context) => {
          this.context.set(context);
          this.failed.set(false);
        },
        error: () => this.failed.set(true),
      });
  }
}
