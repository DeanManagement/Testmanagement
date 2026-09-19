import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CreateTestPlanRequest,
  TestPlan,
  TestPlanSummary,
  UpdateTestPlanRequest,
} from '../../shared/models/test-plan.model';
import { retryWithBackoff } from '../utils/retry-strategy';
import { BurnDown } from '../../shared/models/effort.model';

@Injectable({ providedIn: 'root' })
export class TestPlanApiService {
  private readonly http = inject(HttpClient);

  private baseUrl(projectId: string): string {
    return `/api/projects/${projectId}/test-plans`;
  }

  getAll(projectId: string): Observable<TestPlan[]> {
    return this.http.get<TestPlan[]>(this.baseUrl(projectId)).pipe(retryWithBackoff());
  }

  getById(projectId: string, id: string): Observable<TestPlan> {
    return this.http.get<TestPlan>(`${this.baseUrl(projectId)}/${id}`).pipe(retryWithBackoff());
  }

  /** Remaining estimated effort per day, computed on demand (PRD-036). */
  getBurnDown(projectId: string, id: string): Observable<BurnDown> {
    return this.http.get<BurnDown>(`${this.baseUrl(projectId)}/${id}/burn-down`).pipe(retryWithBackoff());
  }

  getSummary(projectId: string, id: string): Observable<TestPlanSummary> {
    return this.http.get<TestPlanSummary>(`${this.baseUrl(projectId)}/${id}/summary`).pipe(retryWithBackoff());
  }

  create(projectId: string, request: CreateTestPlanRequest): Observable<TestPlan> {
    return this.http.post<TestPlan>(this.baseUrl(projectId), request);
  }

  update(projectId: string, id: string, request: UpdateTestPlanRequest): Observable<TestPlan> {
    return this.http.put<TestPlan>(`${this.baseUrl(projectId)}/${id}`, request);
  }

  delete(projectId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl(projectId)}/${id}`);
  }
}
