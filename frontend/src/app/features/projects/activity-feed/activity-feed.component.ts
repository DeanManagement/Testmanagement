import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { take } from 'rxjs/operators';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { ActivityApiService } from '../../../core/services/activity-api.service';
import { ProjectMemberApiService } from '../../../core/services/project-member-api.service';
import {
  ActivityQuery,
  ALL_AUDIT_ACTIONS,
  AuditAction,
  AuditEntityType,
  AuditEntry,
  FILTERABLE_ENTITY_TYPES,
} from '../../../shared/models/activity.model';
import { ProjectMember } from '../../../shared/models/project-member.model';
import { ActivityEntryComponent } from '../../../shared/components/activity-entry/activity-entry.component';

const PAGE_SIZE = 20;

/**
 * The project's activity (PRD-046): filtered by date, user, object type and action, newest or
 * oldest first, and exported as CSV. Every filter lives in the URL, so a view can be shared.
 */
@Component({
  selector: 'app-activity-feed',
  standalone: true,
  imports: [
    ActivityEntryComponent,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    TranslateModule,
  ],
  templateUrl: './activity-feed.component.html',
  styleUrl: './activity-feed.component.scss',
})
export class ActivityFeedComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly activityApi = inject(ActivityApiService);
  private readonly memberApi = inject(ProjectMemberApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly entityTypes = FILTERABLE_ENTITY_TYPES;
  readonly actions = ALL_AUDIT_ACTIONS;

  projectId = '';
  entries: AuditEntry[] = [];
  members: ProjectMember[] = [];
  loading = false;
  hasMore = true;

  /** Dates as yyyy-mm-dd, the way the date inputs and the URL hold them. */
  fromDate = '';
  toDate = '';
  userFilter: string[] = [];
  typeFilter: AuditEntityType[] = [];
  actionFilter: AuditAction[] = [];
  sort: 'asc' | 'desc' = 'desc';

  private page = 0;

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('id') ?? '';
    if (!this.projectId) return;
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => this.readQuery(params));
    this.memberApi.getByProject(this.projectId).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((members) => {
        this.members = members;
        this.cdr.detectChanges();
      });
  }

  private readQuery(params: ParamMap): void {
    this.fromDate = params.get('from') ?? '';
    this.toDate = params.get('to') ?? '';
    this.userFilter = params.getAll('user');
    this.typeFilter = params.getAll('type') as AuditEntityType[];
    this.actionFilter = params.getAll('action') as AuditAction[];
    this.sort = params.get('sort') === 'asc' ? 'asc' : 'desc';
    this.entries = [];
    this.page = 0;
    this.hasMore = true;
    this.loadMore();
  }

  get hasFilters(): boolean {
    return !!this.fromDate || !!this.toDate || this.userFilter.length > 0 || this.typeFilter.length > 0
      || this.actionFilter.length > 0;
  }

  applyFilters(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        from: this.fromDate || null,
        to: this.toDate || null,
        user: this.userFilter.length ? this.userFilter : null,
        type: this.typeFilter.length ? this.typeFilter : null,
        action: this.actionFilter.length ? this.actionFilter : null,
        sort: this.sort === 'asc' ? 'asc' : null,
      },
      replaceUrl: true,
    });
  }

  clearFilters(): void {
    this.fromDate = '';
    this.toDate = '';
    this.userFilter = [];
    this.typeFilter = [];
    this.actionFilter = [];
    this.applyFilters();
  }

  loadMore(): void {
    if (this.loading || !this.hasMore) return;
    this.loading = true;
    this.activityApi.getActivity(this.projectId, { ...this.query(), page: this.page, size: PAGE_SIZE })
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.entries = [...this.entries, ...response.content];
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

  exportCsv(): void {
    this.activityApi.exportCsv(this.projectId, this.query()).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'activity.csv';
        a.click();
        setTimeout(() => URL.revokeObjectURL(url));
      });
  }

  /** The filters as the API takes them. Dates cover whole days in the viewer's time zone. */
  query(): ActivityQuery {
    return {
      from: this.fromDate ? startOfDay(this.fromDate).toISOString() : undefined,
      to: this.toDate ? startOfNextDay(this.toDate).toISOString() : undefined,
      userId: this.userFilter,
      entityType: this.typeFilter,
      action: this.actionFilter,
      sort: this.sort,
    };
  }
}

function startOfDay(date: string): Date {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(year, month - 1, day);
}

/** "To" includes its day, so the range ends where the next day starts. */
function startOfNextDay(date: string): Date {
  const next = startOfDay(date);
  next.setDate(next.getDate() + 1);
  return next;
}
