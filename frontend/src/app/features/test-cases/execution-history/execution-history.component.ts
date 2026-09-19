import { Component, DestroyRef, inject, Input, OnChanges, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LowerCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { take } from 'rxjs/operators';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { TestCaseExecution } from '../../../shared/models/test-case.model';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';

const PAGE_SIZE = 20;

/**
 * Everywhere a case ran or is scheduled to run (PRD-050), newest run first; each row opens that
 * result in its run. A version older than the case's current one is marked, since the wording it ran
 * is no longer what the page shows.
 */
@Component({
  selector: 'app-execution-history',
  standalone: true,
  imports: [LowerCasePipe, RouterLink, MatPaginatorModule, MatTableModule, MatTooltipModule, TranslateModule,
    LocalizedDatePipe],
  template: `
    @if (failed()) {
      <p class="hint" role="status">{{ 'testCase.executions.unavailable' | translate }}</p>
    } @else if (total() === 0) {
      <p class="hint" data-test-id="executions-empty">{{ 'testCase.executions.empty' | translate }}</p>
    } @else if (total(); as count) {
      <div class="table-scroll">
        <table mat-table [dataSource]="rows()" class="data-table" data-test-id="executions-table">
          <ng-container matColumnDef="run">
            <th mat-header-cell *matHeaderCellDef>{{ 'testCase.executions.run' | translate }}</th>
            <td mat-cell *matCellDef="let row">
              <a [routerLink]="['/projects', projectId, 'test-runs', row.runId]" [queryParams]="{ result: row.resultId }"
                 class="item-link"><span class="case-key">{{ row.runKey }}</span> {{ row.runName }}</a>
              @if (row.parameterSetName) {
                <span class="secondary">· {{ row.parameterSetName }}</span>
              }
            </td>
          </ng-container>
          <ng-container matColumnDef="environment">
            <th mat-header-cell *matHeaderCellDef>{{ 'testRun.form.environment' | translate }}</th>
            <td mat-cell *matCellDef="let row">{{ row.environment || '–' }}</td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>{{ 'testCase.form.status' | translate }}</th>
            <td mat-cell *matCellDef="let row">
              <span class="badge badge--{{ row.status | lowercase }}">{{ 'resultStatus.' + row.status | translate }}</span>
            </td>
          </ng-container>
          <ng-container matColumnDef="executedAt">
            <th mat-header-cell *matHeaderCellDef>{{ 'report.executedAt' | translate }}</th>
            <td mat-cell *matCellDef="let row">{{ row.executedAt ? (row.executedAt | localizedDate:'short') : '–' }}</td>
          </ng-container>
          <ng-container matColumnDef="executor">
            <th mat-header-cell *matHeaderCellDef>{{ 'report.executedBy' | translate }}</th>
            <td mat-cell *matCellDef="let row">{{ row.executorName || '–' }}</td>
          </ng-container>
          <ng-container matColumnDef="version">
            <th mat-header-cell *matHeaderCellDef>{{ 'report.version' | translate }}</th>
            <td mat-cell *matCellDef="let row">
              @if (row.executedVersion) {
                v{{ row.executedVersion }}
                @if (isOlderWording(row)) {
                  <span class="older" [matTooltip]="'testCase.executions.olderWordingHint' | translate"
                        data-test-id="executions-older-wording">{{ 'testCase.executions.olderWording' | translate }}</span>
                }
              } @else {
                –
              }
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
      </div>
      @if (count > pageSize) {
        <mat-paginator [length]="count" [pageSize]="pageSize" [pageIndex]="pageIndex()" [hidePageSize]="true"
                       (page)="onPage($event)" />
      }
    }
  `,
  styles: [`
    .hint { color: var(--tm-text-secondary); margin: 0; }
    .secondary { color: var(--tm-text-secondary); font-size: 12px; }
    .older { margin-left: 6px; font-size: 11px; color: #b45309; }
    .item-link { color: inherit; text-decoration: none; }
    .item-link:hover { text-decoration: underline; }
  `],
})
export class ExecutionHistoryComponent implements OnChanges {
  private readonly api = inject(TestCaseApiService);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) projectId!: string;
  @Input({ required: true }) testCaseId!: string;
  /** The case's current version, to mark results that ran older wording. */
  @Input() currentVersion: number | null = null;

  readonly columns = ['run', 'environment', 'status', 'executedAt', 'executor', 'version'];
  readonly pageSize = PAGE_SIZE;
  readonly rows = signal<TestCaseExecution[]>([]);
  readonly total = signal<number | null>(null);
  readonly pageIndex = signal(0);
  readonly failed = signal(false);

  ngOnChanges(): void {
    this.load(0);
  }

  onPage(event: PageEvent): void {
    this.load(event.pageIndex);
  }

  isOlderWording(row: TestCaseExecution): boolean {
    return row.executedVersion != null && this.currentVersion != null && row.executedVersion < this.currentVersion;
  }

  private load(page: number): void {
    this.api.getExecutions(this.projectId, this.testCaseId, page, PAGE_SIZE)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.rows.set(result.content);
          this.total.set(result.page.totalElements);
          this.pageIndex.set(result.page.number);
          this.failed.set(false);
        },
        error: () => this.failed.set(true),
      });
  }
}
