import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CreateSavedFilterRequest,
  SavedFilter,
  SavedFilterView,
  UpdateSavedFilterRequest,
} from '../../shared/models/saved-filter.model';

@Injectable({ providedIn: 'root' })
export class SavedFilterApiService {
  private readonly http = inject(HttpClient);

  private baseUrl(projectId: string): string {
    return `/api/projects/${projectId}/saved-filters`;
  }

  /** The caller's own filters and the project's shared ones. */
  getVisible(projectId: string, view: SavedFilterView): Observable<SavedFilter[]> {
    return this.http.get<SavedFilter[]>(this.baseUrl(projectId), { params: { view } });
  }

  create(projectId: string, request: CreateSavedFilterRequest): Observable<SavedFilter> {
    return this.http.post<SavedFilter>(this.baseUrl(projectId), request);
  }

  update(projectId: string, id: string, request: UpdateSavedFilterRequest): Observable<SavedFilter> {
    return this.http.put<SavedFilter>(`${this.baseUrl(projectId)}/${id}`, request);
  }

  delete(projectId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl(projectId)}/${id}`);
  }
}
