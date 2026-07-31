import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Comment, CreateCommentRequest, UpdateCommentRequest } from '../../shared/models/comment.model';
import { retryWithBackoff } from '../utils/retry-strategy';

@Injectable({ providedIn: 'root' })
export class CommentApiService {
  private readonly http = inject(HttpClient);

  getTestCaseComments(projectId: string, testCaseId: string): Observable<Comment[]> {
    return this.http.get<Comment[]>(`/api/projects/${projectId}/test-cases/${testCaseId}/comments`).pipe(retryWithBackoff());
  }

  createTestCaseComment(projectId: string, testCaseId: string, request: CreateCommentRequest): Observable<Comment> {
    return this.http.post<Comment>(`/api/projects/${projectId}/test-cases/${testCaseId}/comments`, request);
  }

  getTestResultComments(projectId: string, runId: string, resultId: string): Observable<Comment[]> {
    return this.http.get<Comment[]>(`/api/projects/${projectId}/test-runs/${runId}/results/${resultId}/comments`).pipe(retryWithBackoff());
  }

  createTestResultComment(projectId: string, runId: string, resultId: string, request: CreateCommentRequest): Observable<Comment> {
    return this.http.post<Comment>(`/api/projects/${projectId}/test-runs/${runId}/results/${resultId}/comments`, request);
  }

  update(projectId: string, commentId: string, request: UpdateCommentRequest): Observable<Comment> {
    return this.http.put<Comment>(`/api/projects/${projectId}/comments/${commentId}`, request);
  }

  delete(projectId: string, commentId: string): Observable<void> {
    return this.http.delete<void>(`/api/projects/${projectId}/comments/${commentId}`);
  }
}
