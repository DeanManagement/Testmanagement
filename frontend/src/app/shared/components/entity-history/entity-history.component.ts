import { ChangeDetectorRef, Component, inject, Input, OnChanges } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { ActivityApiService } from '../../../core/services/activity-api.service';
import { AuditEntry } from '../../models/activity.model';

@Component({
  selector: 'app-entity-history',
  standalone: true,
  imports: [
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './entity-history.component.html',
  styleUrl: './entity-history.component.scss',
})
export class EntityHistoryComponent implements OnChanges {
  private readonly activityApi = inject(ActivityApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  @Input({ required: true }) projectId!: string;
  @Input({ required: true }) entityId!: string;

  entries: AuditEntry[] = [];
  loading = false;
  hasMore = true;
  private page = 0;
  private readonly pageSize = 10;

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
    this.activityApi.getEntityHistory(this.projectId, this.entityId, this.page, this.pageSize).subscribe({
      next: (response) => {
        this.entries = [...this.entries, ...response.content];
        this.hasMore = !response.last;
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

  actionIcon(action: string): string {
    switch (action) {
      case 'CREATED': return 'add_circle';
      case 'UPDATED': return 'edit';
      case 'DELETED': return 'delete';
      case 'STATUS_CHANGED': return 'swap_horiz';
      case 'COMPLETED': return 'check_circle';
      case 'REOPENED': return 'replay';
      case 'CLONED': return 'content_copy';
      default: return 'info';
    }
  }
}
