import { Component, DestroyRef, computed, inject, OnInit, signal } from '@angular/core';
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
 *
 * State is held in signals rather than plain fields. The app runs zoneless, so an HTTP callback
 * writing a plain field notifies nothing and the view is never re-checked — the spinner then sits
 * there after the response has already arrived, until some unrelated interaction happens to
 * trigger change detection. Signal writes schedule that themselves.
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

  readonly loading = signal(true);
  readonly queue = signal<MyQueueResponse | null>(null);

  readonly isEmpty = computed(() => {
    const queue = this.queue();
    if (!queue) {
      return false;
    }
    return (
      queue.dueTestPlans.length === 0 &&
      queue.inProgressRuns.length === 0 &&
      queue.staleBugReports.length === 0 &&
      queue.oldDraftTestCases.length === 0
    );
  });

  ngOnInit(): void {
    this.api.get().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (queue) => {
        this.queue.set(queue);
        this.loading.set(false);
      },
      error: () => {
        // Fail silently — the widget is a nice-to-have. The dashboard still
        // works without it, and a noisy error toast on every dashboard visit
        // would be worse than a missing widget.
        this.loading.set(false);
      },
    });
  }
}
