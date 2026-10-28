import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CreateTestPlanRequest,
  TestPlan,
  TestPlanScope,
  TestPlanSummary,
  UpdateTestPlanRequest,
} from '../../shared/models/test-plan.model';
import { retryWithBackoff } from '../utils/retry-strategy';
import { BurnDown } from '../../shared/models/effort.model';
import { Readiness } from '../../shared/models/readiness.model';

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

  /** GO / NO_GO against the plan's release gate (PRD-037). */
  getReadiness(projectId: string, id: string): Observable<Readiness> {
    return this.http.get<Readiness>(`${this.baseUrl(projectId)}/${id}/readiness`).pipe(retryWithBackoff());
  }

  /** Remaining estimated effort per day, computed on demand (PRD-036). */
  getBurnDown(projectId: string, id: string): Observable<BurnDown> {
    return this.http.get<BurnDown>(`${this.baseUrl(projectId)}/${id}/burn-down`).pipe(retryWithBackoff());
  }

  getSummary(projectId: string, id: string): Observable<TestPlanSummary> {
    return this.http.get<TestPlanSummary>(`${this.baseUrl(projectId)}/${id}/summary`).pipe(retryWithBackoff());
  }

  /** PRD-053 §8: the cases the plan intends to execute, with their latest result. */
  getScope(projectId: string, id: string): Observable<TestPlanScope> {
    return this.http.get<TestPlanScope>(`${this.baseUrl(projectId)}/${id}/scope`).pipe(retryWithBackoff());
  }

  /** At most 100 ids per call (the server's bulk limit). */
  addToScope(projectId: string, id: string, testCaseIds: string[]): Observable<TestPlanScope> {
    return this.http.post<TestPlanScope>(`${this.baseUrl(projectId)}/${id}/scope/bulk-add`, { testCaseIds });
  }

  removeFromScope(projectId: string, id: string, testCaseIds: string[]): Observable<TestPlanScope> {
    return this.http.post<TestPlanScope>(`${this.baseUrl(projectId)}/${id}/scope/bulk-remove`, { testCaseIds });
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
