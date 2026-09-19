import { ChangeDetectorRef, Component, DestroyRef, inject, Input, OnChanges } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { take } from 'rxjs/operators';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { ActivityApiService } from '../../../core/services/activity-api.service';
import { AuditEntry } from '../../models/activity.model';
import { ActivityEntryComponent } from '../activity-entry/activity-entry.component';

const PAGE_SIZE = 10;

/** One object's history, newest first, including comments on it (PRD-046). */
@Component({
  selector: 'app-entity-history',
  standalone: true,
  imports: [ActivityEntryComponent, MatButtonModule, MatProgressSpinnerModule, TranslateModule],
  templateUrl: './entity-history.component.html',
  styleUrl: './entity-history.component.scss',
})
export class EntityHistoryComponent implements OnChanges {
  private readonly activityApi = inject(ActivityApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) projectId!: string;
  @Input({ required: true }) entityId!: string;
  /** When the object last changed: a new value reloads the history, e.g. after a status change. */
  @Input() changedAt: string | null = null;

  entries: AuditEntry[] = [];
  loading = false;
  hasMore = true;
  private page = 0;

  ngOnChanges(): void {
    this.entries = [];
    this.page = 0;
    this.hasMore = true;
    if (this.projectId && this.entityId) {
      this.loadMore();
    }
  }

  loadMore(): void {
    if (this.loading || !this.hasMore) return;
    this.loading = true;
    this.activityApi.getActivity(this.projectId, { entityId: this.entityId, page: this.page, size: PAGE_SIZE })
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.entries = [...this.entries, ...response.content];
          // The page metadata, not a top-level "last": the API sends the PagedModel shape.
          this.hasMore = response.page.number + 1 < response.page.totalPages;
          this.page++;
          this.loading = false;
          this.cdr.detectChanges();
        },
        error: () => {
          this.loading = false;
          this.cdr.detectChanges();
        },
      });
  }
}
