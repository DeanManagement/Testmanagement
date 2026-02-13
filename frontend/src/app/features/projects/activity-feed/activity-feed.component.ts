import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { ActivityApiService } from '../../../core/services/activity-api.service';
import { AuditEntry } from '../../../shared/models/activity.model';

@Component({
  selector: 'app-activity-feed',
  standalone: true,
  imports: [
    DatePipe,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './activity-feed.component.html',
  styleUrl: './activity-feed.component.scss',
})
export class ActivityFeedComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly activityApi = inject(ActivityApiService);

  projectId = '';
  entries: AuditEntry[] = [];
  loading = false;
  hasMore = true;
  private page = 0;
  private readonly pageSize = 20;

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('id') ?? '';
    if (this.projectId) {
      this.loadMore();
    }
  }

  loadMore(): void {
    if (this.loading || !this.hasMore) return;
    this.loading = true;
    this.activityApi.getActivity(this.projectId, this.page, this.pageSize).subscribe({
      next: (response) => {
        this.entries = [...this.entries, ...response.content];
        this.hasMore = !response.last;
        this.page++;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
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
