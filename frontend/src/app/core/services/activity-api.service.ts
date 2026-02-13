import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuditEntry, PagedResponse } from '../../shared/models/activity.model';

@Injectable({ providedIn: 'root' })
export class ActivityApiService {
  private readonly http = inject(HttpClient);

  getActivity(projectId: string, page: number, size: number): Observable<PagedResponse<AuditEntry>> {
    return this.http.get<PagedResponse<AuditEntry>>(
      `/api/projects/${projectId}/activity`,
      { params: { page: page.toString(), size: size.toString() } }
    );
  }
}
