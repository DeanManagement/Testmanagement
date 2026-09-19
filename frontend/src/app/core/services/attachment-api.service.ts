import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpEvent } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Attachment } from '../../shared/models/test-case.model';

/**
 * Files on any owner (PRD-044 test cases, PRD-051 bug reports). Every call takes the owner's
 * attachment collection URL, which the owner's own API service builds.
 */
@Injectable({ providedIn: 'root' })
export class AttachmentApiService {
  private readonly http = inject(HttpClient);

  list(url: string): Observable<Attachment[]> {
    return this.http.get<Attachment[]>(url);
  }

  /** Emits upload progress events, then the stored attachment. */
  upload(url: string, file: File): Observable<HttpEvent<Attachment>> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<Attachment>(url, formData, { reportProgress: true, observe: 'events' });
  }

  /** Just the stored attachment, for uploads nobody watches the progress of. */
  uploadQuietly(url: string, file: File): Observable<Attachment> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<Attachment>(url, formData);
  }

  download(url: string, id: string): Observable<Blob> {
    return this.http.get(`${url}/${id}`, { responseType: 'blob' });
  }

  delete(url: string, id: string): Observable<void> {
    return this.http.delete<void>(`${url}/${id}`);
  }
}
