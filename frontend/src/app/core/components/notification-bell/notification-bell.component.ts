import { ChangeDetectorRef, Component, DestroyRef, NgZone, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatBadgeModule } from '@angular/material/badge';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { interval } from 'rxjs';
import { take } from 'rxjs/operators';
import { NotificationApiService } from '../../services/notification-api.service';
import { AppNotification } from '../../../shared/models/notification.model';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatBadgeModule,
    MatMenuModule,
    MatTooltipModule,
    TranslateModule,
  ],
  templateUrl: './notification-bell.component.html',
  styleUrl: './notification-bell.component.scss',
})
export class NotificationBellComponent implements OnInit {
  private readonly api = inject(NotificationApiService);
  private readonly router = inject(Router);
  private readonly zone = inject(NgZone);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  unreadCount = 0;
  notifications: AppNotification[] = [];
  loading = false;

  ngOnInit(): void {
    this.refreshCount();
    // Poll unread count every 60s while the tab is visible.
    this.zone.runOutsideAngular(() => {
      interval(60000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
        if (!document.hidden) {
          this.zone.run(() => this.refreshCount());
        }
      });
    });
  }

  refreshCount(): void {
    this.api.unreadCount().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe((r) => {
      this.unreadCount = r.count;
      this.cdr.markForCheck();
    });
  }

  onMenuOpened(): void {
    this.loading = true;
    this.api.list(false, 0, 20).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (page) => {
        this.notifications = page.content;
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.loading = false;
        this.cdr.markForCheck();
      },
    });
  }

  open(notification: AppNotification): void {
    const navigate = () => this.router.navigate(this.entityLink(notification));
    if (!notification.read) {
      this.api.markRead(notification.id).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe(() => {
        notification.read = true;
        this.refreshCount();
        navigate();
      });
    } else {
      navigate();
    }
  }

  markAllRead(): void {
    this.api.markAllRead().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.notifications.forEach((n) => (n.read = true));
      this.unreadCount = 0;
      this.cdr.markForCheck();
    });
  }

  entityLink(n: AppNotification): unknown[] {
    const segment = {
      TEST_RUN: 'test-runs',
      TEST_PLAN: 'test-plans',
      BUG_REPORT: 'bug-reports',
    }[n.entityType];
    return ['/projects', n.projectId, segment, n.entityId];
  }
}
