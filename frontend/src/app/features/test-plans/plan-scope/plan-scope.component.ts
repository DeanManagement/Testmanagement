import { Component, computed, DestroyRef, inject, Input, OnChanges, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LowerCasePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { concat, Observable } from 'rxjs';
import { last, take } from 'rxjs/operators';
import { TestPlanApiService } from '../../../core/services/test-plan-api.service';
import { TestRunApiService } from '../../../core/services/test-run-api.service';
import { TestCasePickerComponent } from '../../../shared/components/test-case-picker/test-case-picker.component';
import { TestPlanScope, TestPlanScopeItem } from '../../../shared/models/test-plan.model';
import { TestCaseSummary } from '../../../shared/models/test-suite.model';

/** The server's bulk limit per request. */
const BULK_LIMIT = 100;

/**
 * The plan's scope (PRD-053 §8): the cases it intends to execute, each with its latest result across
 * the plan's runs. Cases no run has executed yet are highlighted, and can be put into a new run.
 */
@Component({
  selector: 'app-plan-scope',
  standalone: true,
  imports: [LowerCasePipe, RouterLink, MatButtonModule, MatIconModule, MatTooltipModule, TranslateModule,
    TestCasePickerComponent],
  template: `
    <div class="runs-section tm-card" data-test-id="test-plan-scope">
      <div class="section-header">
        <mat-icon>checklist</mat-icon>
        <h3>{{ 'testPlan.scope.title' | translate }}</h3>
        @if (scope(); as s) {
          <span class="scope-totals" data-test-id="test-plan-scope-totals">
            {{ 'testPlan.scope.totals' | translate: s.summary }}
          </span>
        }
        <span class="spacer"></span>
        <button mat-stroked-button type="button" (click)="picking.set(!picking())" data-test-id="test-plan-scope-add-toggle">
          <mat-icon>add</mat-icon>{{ 'testPlan.scope.addCases' | translate }}
        </button>
        <button mat-flat-button type="button" [disabled]="unexecutedIds().length === 0 || busy()" (click)="createRun()"
                data-test-id="test-plan-scope-create-run">
          <mat-icon>play_circle</mat-icon>
          {{ 'testPlan.scope.createRun' | translate: { count: unexecutedIds().length } }}
        </button>
      </div>

      @if (picking()) {
        <div class="picker" data-test-id="test-plan-scope-picker">
          <app-test-case-picker [projectId]="projectId" [(selected)]="toAdd" />
          <button mat-stroked-button type="button" [disabled]="toAdd().length === 0 || busy()" (click)="add()"
                  data-test-id="test-plan-scope-add-btn">
            {{ 'testRun.cases.add' | translate: { count: toAdd().length } }}
          </button>
        </div>
      }

      @if (scope()?.cases?.length) {
        <ul class="scope-cases">
          @for (item of scope()!.cases; track item.testCaseId) {
            <li [class.not-executed]="!item.latestStatus" [attr.data-test-id]="'scope-case-' + item.key">
              <a [routerLink]="['/projects', projectId, 'test-cases', item.testCaseId]" class="item-link">
                <span class="case-key">{{ item.key }}</span> {{ item.title }}
              </a>
              @if (item.latestStatus) {
                <span class="badge badge--{{ item.latestStatus | lowercase }} badge--small">
                  {{ 'resultStatus.' + item.latestStatus | translate }}
                </span>
              } @else {
                <span class="badge badge--small not-executed-badge" data-test-id="scope-not-executed">
                  {{ 'testPlan.scope.notExecuted' | translate }}
                </span>
              }
              <button mat-icon-button type="button" (click)="remove(item)" [disabled]="busy()"
                      [matTooltip]="'testPlan.scope.remove' | translate"
                      [attr.data-test-id]="'scope-remove-' + item.key">
                <mat-icon>remove_circle_outline</mat-icon>
              </button>
            </li>
          }
        </ul>
      } @else if (scope()) {
        <p class="empty-hint" data-test-id="test-plan-scope-empty">{{ 'testPlan.scope.empty' | translate }}</p>
      }
    </div>
  `,
  styles: [`
    .section-header { flex-wrap: wrap; }
    .spacer { flex: 1; }
    .scope-totals { font-size: 13px; color: var(--tm-text-secondary); }
    .picker { margin: 8px 0 16px; }
    .scope-cases { list-style: none; margin: 0; padding: 0; }
    .scope-cases li { display: flex; align-items: center; gap: 8px; padding: 4px 8px;
      border-bottom: 1px solid var(--tm-border); }
    .scope-cases li:last-child { border-bottom: none; }
    .scope-cases li.not-executed { background: var(--tm-tint-warn-bg); border-left: 3px solid var(--tm-warn); }
    .item-link { flex: 1; min-width: 0; color: inherit; text-decoration: none; overflow: hidden;
      text-overflow: ellipsis; white-space: nowrap; }
    .item-link:hover { text-decoration: underline; }
    .case-key { font-weight: 600; }
    .badge--small { font-size: 10px; padding: 1px 6px; }
    .not-executed-badge { background: var(--tm-tint-warn-bg); color: var(--tm-tint-warn-fg); font-weight: 600; }
  `],
})
export class PlanScopeComponent implements OnChanges {
  private readonly api = inject(TestPlanApiService);
  private readonly runApi = inject(TestRunApiService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) projectId!: string;
  @Input({ required: true }) planId!: string;
  @Input({ required: true }) planName!: string;

  readonly scope = signal<TestPlanScope | null>(null);
  readonly picking = signal(false);
  readonly toAdd = signal<TestCaseSummary[]>([]);
  readonly busy = signal(false);
  readonly unexecutedIds = computed(() =>
    (this.scope()?.cases ?? []).filter((c) => !c.latestStatus).map((c) => c.testCaseId));

  ngOnChanges(): void {
    this.save(this.api.getScope(this.projectId, this.planId));
  }

  add(): void {
    const ids = this.toAdd().map((tc) => tc.id);
    const calls = chunk(ids, BULK_LIMIT).map((part) => this.api.addToScope(this.projectId, this.planId, part));
    this.save(concat(...calls).pipe(last()), () => {
      this.toAdd.set([]);
      this.picking.set(false);
    });
  }

  remove(item: TestPlanScopeItem): void {
    this.save(this.api.removeFromScope(this.projectId, this.planId, [item.testCaseId]));
  }

  /** Reuses ordinary run creation: a run in this plan seeded with every case not executed yet. */
  createRun(): void {
    this.busy.set(true);
    this.runApi.create(this.projectId, {
      name: this.translate.instant('testPlan.scope.runName', { plan: this.planName }),
      testCaseIds: this.unexecutedIds(),
      testPlanId: this.planId,
    }).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (run) => this.router.navigate(['/projects', this.projectId, 'test-runs', run.id]),
      error: () => this.busy.set(false),
    });
  }

  private save(call: Observable<TestPlanScope>, onSaved?: () => void): void {
    this.busy.set(true);
    call.pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (scope) => {
        this.scope.set(scope);
        this.busy.set(false);
        onSaved?.();
      },
      error: () => this.busy.set(false),
    });
  }
}

function chunk<T>(items: T[], size: number): T[][] {
  const parts: T[][] = [];
  for (let i = 0; i < items.length; i += size) {
    parts.push(items.slice(i, i + size));
  }
  return parts;
}
