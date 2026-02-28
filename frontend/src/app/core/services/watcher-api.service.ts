import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { WatchableEntityType, WatchedItem } from '../../shared/models/watcher.model';

@Injectable({ providedIn: 'root' })
export class WatcherApiService {
  private readonly http = inject(HttpClient);

  watch(entityType: WatchableEntityType, entityId: string): Observable<void> {
    return this.http.post<void>('/api/watchers', { entityType, entityId });
  }

  unwatch(entityType: WatchableEntityType, entityId: string): Observable<void> {
    return this.http.delete<void>('/api/watchers', {
      params: { entityType, entityId },
    });
  }

  isWatching(entityType: WatchableEntityType, entityId: string): Observable<{ watching: boolean }> {
    return this.http.get<{ watching: boolean }>('/api/watchers/check', {
      params: { entityType, entityId },
    });
  }

  getMyWatchedItems(): Observable<WatchedItem[]> {
    return this.http.get<WatchedItem[]>('/api/watchers/my-items');
  }
}
