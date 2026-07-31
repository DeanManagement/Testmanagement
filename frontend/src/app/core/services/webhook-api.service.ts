import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CreateWebhookRequest,
  UpdateWebhookRequest,
  Webhook,
  WebhookDelivery,
} from '../../shared/models/webhook.model';
import { Page } from '../../shared/models/page.model';

@Injectable({ providedIn: 'root' })
export class WebhookApiService {
  private readonly http = inject(HttpClient);

  private baseUrl(projectId: string): string {
    return `/api/projects/${projectId}/webhooks`;
  }

  getAll(projectId: string): Observable<Webhook[]> {
    return this.http.get<Webhook[]>(this.baseUrl(projectId));
  }

  create(projectId: string, request: CreateWebhookRequest): Observable<Webhook> {
    return this.http.post<Webhook>(this.baseUrl(projectId), request);
  }

  update(projectId: string, webhookId: string, request: UpdateWebhookRequest): Observable<Webhook> {
    return this.http.put<Webhook>(`${this.baseUrl(projectId)}/${webhookId}`, request);
  }

  delete(projectId: string, webhookId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl(projectId)}/${webhookId}`);
  }

  test(projectId: string, webhookId: string): Observable<WebhookDelivery> {
    return this.http.post<WebhookDelivery>(`${this.baseUrl(projectId)}/${webhookId}/test`, {});
  }

  getDeliveries(projectId: string, webhookId: string, page = 0, size = 20): Observable<Page<WebhookDelivery>> {
    const params = new HttpParams().set('page', String(page)).set('size', String(size));
    return this.http.get<Page<WebhookDelivery>>(`${this.baseUrl(projectId)}/${webhookId}/deliveries`, { params });
  }
}
