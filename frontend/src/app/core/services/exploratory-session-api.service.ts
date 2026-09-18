import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CreateSessionRequest,
  ExploratorySession,
  SessionNote,
  SessionNoteType,
  UpdateSessionRequest,
} from '../../shared/models/exploratory-session.model';
import { Page } from '../../shared/models/page.model';
import { TestRunStatus } from '../../shared/models/test-run.model';

/** Exploratory sessions (PRD-034). */
@Injectable({ providedIn: 'root' })
export class ExploratorySessionApiService {
  private readonly http = inject(HttpClient);

  private baseUrl(projectId: string): string {
    return `/api/projects/${projectId}/exploratory-sessions`;
  }

  list(projectId: string, status?: TestRunStatus, page = 0, size = 50): Observable<Page<ExploratorySession>> {
    let params = new HttpParams().set('page', String(page)).set('size', String(size)).set('sort', 'createdAt,desc');
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<Page<ExploratorySession>>(this.baseUrl(projectId), { params });
  }

  assignedToMe(): Observable<ExploratorySession[]> {
    return this.http.get<ExploratorySession[]>('/api/exploratory-sessions/assigned-to-me');
  }

  get(projectId: string, id: string): Observable<ExploratorySession> {
    return this.http.get<ExploratorySession>(`${this.baseUrl(projectId)}/${id}`);
  }

  create(projectId: string, request: CreateSessionRequest): Observable<ExploratorySession> {
    return this.http.post<ExploratorySession>(this.baseUrl(projectId), request);
  }

  update(projectId: string, id: string, request: UpdateSessionRequest): Observable<ExploratorySession> {
    return this.http.put<ExploratorySession>(`${this.baseUrl(projectId)}/${id}`, request);
  }

  start(projectId: string, id: string): Observable<ExploratorySession> {
    return this.http.post<ExploratorySession>(`${this.baseUrl(projectId)}/${id}/start`, {});
  }

  complete(projectId: string, id: string, summary?: string): Observable<ExploratorySession> {
    return this.http.post<ExploratorySession>(`${this.baseUrl(projectId)}/${id}/complete`, { summary });
  }

  abort(projectId: string, id: string, summary?: string): Observable<ExploratorySession> {
    return this.http.post<ExploratorySession>(`${this.baseUrl(projectId)}/${id}/abort`, { summary });
  }

  delete(projectId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl(projectId)}/${id}`);
  }

  addNote(projectId: string, id: string, type: SessionNoteType, body: string): Observable<SessionNote> {
    return this.http.post<SessionNote>(`${this.baseUrl(projectId)}/${id}/notes`, { type, body });
  }

  deleteNote(projectId: string, id: string, noteId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl(projectId)}/${id}/notes/${noteId}`);
  }

  uploadImage(projectId: string, id: string, noteId: string, file: File): Observable<void> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<void>(`${this.baseUrl(projectId)}/${id}/notes/${noteId}/image`, form);
  }

  /** For the authImage pipe, which fetches with the session's credentials. */
  imageUrl(projectId: string, id: string, noteId: string): string {
    return `${this.baseUrl(projectId)}/${id}/notes/${noteId}/image`;
  }
}
