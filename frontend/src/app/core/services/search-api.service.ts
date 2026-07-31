import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface SearchHit {
  type: 'testCase' | 'testRun' | 'bugReport' | 'project';
  id: string;
  key: string | null;
  title: string;
  projectId: string;
  snippet: string | null;
}

export interface SearchResponse {
  testCases: SearchHit[];
  testRuns: SearchHit[];
  bugReports: SearchHit[];
  projects: SearchHit[];
}

@Injectable({ providedIn: 'root' })
export class SearchApiService {
  private readonly http = inject(HttpClient);

  search(q: string, limit = 5): Observable<SearchResponse> {
    const params = new HttpParams().set('q', q).set('limit', String(limit));
    return this.http.get<SearchResponse>('/api/search', { params });
  }
}
