import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe, DatePipe, LowerCasePipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { WatcherActions } from '../../store/watcher/watcher.actions';
import { selectWatchedItems, selectWatchedItemsLoading } from '../../store/watcher/watcher.selectors';
import { WatchedItem } from '../../shared/models/watcher.model';

@Component({
  selector: 'app-my-watched',
  standalone: true,
  imports: [
    AsyncPipe,
    DatePipe,
    LowerCasePipe,
    RouterLink,
    MatTableModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './my-watched.component.html',
  styleUrl: './my-watched.component.scss',
})
export class MyWatchedComponent implements OnInit {
  private readonly store = inject(Store);

  watchedItems$ = this.store.select(selectWatchedItems);
  loading$ = this.store.select(selectWatchedItemsLoading);
  displayedColumns = ['entityType', 'name', 'status', 'watchedAt'];

  ngOnInit(): void {
    this.store.dispatch(WatcherActions.loadWatchedItems());
  }

  getRouterLink(item: WatchedItem): string[] {
    switch (item.entityType) {
      case 'TEST_PLAN':
        return ['/projects', item.projectId, 'test-plans', item.entityId];
      case 'TEST_RUN':
        return ['/projects', item.projectId, 'test-runs', item.entityId];
      case 'BUG_REPORT':
        return ['/projects', item.projectId, 'bug-reports', item.entityId];
    }
  }

  getEntityIcon(entityType: string): string {
    switch (entityType) {
      case 'TEST_PLAN': return 'assignment';
      case 'TEST_RUN': return 'play_circle';
      case 'BUG_REPORT': return 'bug_report';
      default: return 'bookmark';
    }
  }
}
