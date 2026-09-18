import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map, shareReplay, tap } from 'rxjs';
import {
  CreateCustomFieldRequest,
  CustomField,
  CustomFieldEntityType,
  UpdateCustomFieldRequest,
} from '../../shared/models/custom-field.model';

/**
 * Custom field definitions (PRD-035). The full list is cached per project because every form,
 * detail page and list filter asks for it; any change made through this service drops the cache.
 */
@Injectable({ providedIn: 'root' })
export class CustomFieldApiService {
  private readonly http = inject(HttpClient);
  private readonly cache = new Map<string, Observable<CustomField[]>>();

  private baseUrl(projectId: string): string {
    return `/api/projects/${projectId}/custom-fields`;
  }

  /** Every definition of the project, archived included, in display order. Cached. */
  getAll(projectId: string): Observable<CustomField[]> {
    let cached = this.cache.get(projectId);
    if (!cached) {
      cached = this.http.get<CustomField[]>(this.baseUrl(projectId)).pipe(shareReplay(1));
      this.cache.set(projectId, cached);
    }
    return cached;
  }

  /** The fields forms and filters offer: not archived, of one entity type. */
  getActive(projectId: string, entityType: CustomFieldEntityType): Observable<CustomField[]> {
    return this.getAll(projectId).pipe(
      map((fields) => fields.filter((field) => field.entityType === entityType && !field.archived)),
    );
  }

  create(projectId: string, request: CreateCustomFieldRequest): Observable<CustomField> {
    return this.http.post<CustomField>(this.baseUrl(projectId), request).pipe(tap(() => this.invalidate(projectId)));
  }

  update(projectId: string, id: string, request: UpdateCustomFieldRequest): Observable<CustomField> {
    return this.http.put<CustomField>(`${this.baseUrl(projectId)}/${id}`, request)
      .pipe(tap(() => this.invalidate(projectId)));
  }

  archive(projectId: string, id: string): Observable<CustomField> {
    return this.http.post<CustomField>(`${this.baseUrl(projectId)}/${id}/archive`, {})
      .pipe(tap(() => this.invalidate(projectId)));
  }

  unarchive(projectId: string, id: string): Observable<CustomField> {
    return this.http.post<CustomField>(`${this.baseUrl(projectId)}/${id}/unarchive`, {})
      .pipe(tap(() => this.invalidate(projectId)));
  }

  /** Refused with 409 while the field holds values. */
  delete(projectId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl(projectId)}/${id}`).pipe(tap(() => this.invalidate(projectId)));
  }

  /** Deletes the field and every value it holds. */
  forceDelete(projectId: string, id: string): Observable<void> {
    const params = new HttpParams().set('force', 'true');
    return this.http.delete<void>(`${this.baseUrl(projectId)}/${id}`, { params })
      .pipe(tap(() => this.invalidate(projectId)));
  }

  private invalidate(projectId: string): void {
    this.cache.delete(projectId);
  }
}
