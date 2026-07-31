import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MyQueueResponse } from '../../shared/models/my-queue.model';

/**
 * Lightweight client for `GET /api/me/queue`. No NgRx slice: this is a
 * single-shot read fetched on dashboard load, and any state would be stale
 * by the time the user returns to the page anyway.
 */
@Injectable({ providedIn: 'root' })
export class MyQueueApiService {
  private readonly http = inject(HttpClient);

  get(): Observable<MyQueueResponse> {
    return this.http.get<MyQueueResponse>('/api/me/queue');
  }
}
