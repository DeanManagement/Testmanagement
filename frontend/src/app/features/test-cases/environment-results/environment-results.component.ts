import { ChangeDetectorRef, Component, DestroyRef, OnChanges, inject, input } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LowerCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { take } from 'rxjs/operators';
import { EnvironmentApiService } from '../../../core/services/environment-api.service';
import { EnvironmentResult } from '../../../shared/models/environment.model';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';

/** "Has it passed on X?": a test case's latest executed result in each environment (PRD-032). */
@Component({
  selector: 'app-environment-results',
  standalone: true,
  imports: [LowerCasePipe, RouterLink, TranslateModule, LocalizedDatePipe],
  template: `
    @if (loaded) {
      @if (rows.length === 0) {
        <p class="empty">{{ 'environment.results.empty' | translate }}</p>
      } @else {
        <div class="table-scroll">
          <table class="results-table" data-test-id="environment-results-table">
            <thead>
              <tr>
                <th>{{ 'environment.label' | translate }}</th>
                <th>{{ 'environment.results.status' | translate }}</th>
                <th>{{ 'environment.results.run' | translate }}</th>
                <th>{{ 'environment.results.date' | translate }}</th>
              </tr>
            </thead>
            <tbody>
              @for (row of rows; track row.environmentId ?? 'unspecified') {
                <tr>
                  <td>{{ row.environmentName ?? ('environment.unspecified' | translate) }}</td>
                  <td>
                    <span class="badge badge--{{ row.status | lowercase }}">{{ 'resultStatus.' + row.status | translate }}</span>
                  </td>
                  <td><a [routerLink]="['/projects', projectId(), 'test-runs', row.runId]">{{ row.runKey }}</a></td>
                  <td>{{ row.executedAt | localizedDate: 'short' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    }
  `,
  styles: [`
    .table-scroll { overflow-x: auto; }
    .results-table { width: 100%; border-collapse: collapse; }
    .results-table th, .results-table td { text-align: left; padding: 0.5rem 0.75rem; border-bottom: 1px solid var(--tm-border); }
    .results-table th { font-weight: 600; }
    .empty { margin: 0; opacity: 0.7; }
  `],
})
export class EnvironmentResultsComponent implements OnChanges {
  private readonly api = inject(EnvironmentApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly projectId = input.required<string>();
  readonly testCaseId = input.required<string>();

  rows: EnvironmentResult[] = [];
  loaded = false;

  ngOnChanges(): void {
    this.api.latestResultsByEnvironment(this.projectId(), this.testCaseId())
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((rows) => {
        this.rows = rows;
        this.loaded = true;
        this.cdr.detectChanges();
      });
  }
}
