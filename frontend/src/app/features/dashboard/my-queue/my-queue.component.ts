import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { take } from 'rxjs/operators';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';
import { MyQueueApiService } from '../../../core/services/my-queue-api.service';
import { MyQueueResponse } from '../../../shared/models/my-queue.model';

/**
 * Dashboard widget that aggregates a few high-signal "things I should do
 * right now" buckets for the calling user. Backed by `/api/me/queue`.
 *
 * Intentionally renders nothing while loading and shows a friendly empty
 * state when the queue is empty — most users will hit that case often, and
 * a permanently visible empty container would just be visual noise.
 */
@Component({
  selector: 'app-my-queue',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslateModule,
    LocalizedDatePipe,
  ],
  templateUrl: './my-queue.component.html',
  styleUrl: './my-queue.component.scss',
})
export class MyQueueComponent implements OnInit {
  private readonly api = inject(MyQueueApiService);
  private readonly destroyRef = inject(DestroyRef);

  loading = true;
  queue: MyQueueResponse | null = null;

  ngOnInit(): void {
    this.api.get().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (queue) => {
        this.queue = queue;
        this.loading = false;
      },
      error: () => {
        // Fail silently — the widget is a nice-to-have. The dashboard still
        // works without it, and a noisy error toast on every dashboard visit
        // would be worse than a missing widget.
        this.loading = false;
      },
    });
  }

  get isEmpty(): boolean {
    if (!this.queue) return false;
    return (
      this.queue.dueTestPlans.length === 0 &&
      this.queue.inProgressRuns.length === 0 &&
      this.queue.staleBugReports.length === 0 &&
      this.queue.oldDraftTestCases.length === 0
    );
  }
}
