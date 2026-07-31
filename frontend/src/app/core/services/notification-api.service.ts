import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AppNotification, NotificationPreference } from '../../shared/models/notification.model';
import { Page } from '../../shared/models/page.model';

@Injectable({ providedIn: 'root' })
export class NotificationApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/me';

  list(unread = false, page = 0, size = 20): Observable<Page<AppNotification>> {
    const params = new HttpParams()
      .set('unread', String(unread))
      .set('page', String(page))
      .set('size', String(size));
    return this.http.get<Page<AppNotification>>(`${this.baseUrl}/notifications`, { params });
  }

  unreadCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.baseUrl}/notifications/unread-count`);
  }

  markRead(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/notifications/${id}/read`, {});
  }

  markAllRead(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/notifications/read-all`, {});
  }

  getPreferences(): Observable<NotificationPreference[]> {
    return this.http.get<NotificationPreference[]>(`${this.baseUrl}/notification-preferences`);
  }

  updatePreferences(prefs: NotificationPreference[]): Observable<NotificationPreference[]> {
    return this.http.put<NotificationPreference[]>(`${this.baseUrl}/notification-preferences`, prefs);
  }
}
