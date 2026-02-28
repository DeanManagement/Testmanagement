import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  TestCaseFolder,
  CreateTestCaseFolderRequest,
  UpdateTestCaseFolderRequest,
  ReorderFoldersRequest,
  MoveTestCasesRequest,
} from '../../shared/models/test-case-folder.model';

@Injectable({ providedIn: 'root' })
export class TestCaseFolderApiService {
  private readonly http = inject(HttpClient);

  private baseUrl(projectId: string): string {
    return `/api/projects/${projectId}/test-case-folders`;
  }

  getTree(projectId: string): Observable<TestCaseFolder[]> {
    return this.http.get<TestCaseFolder[]>(this.baseUrl(projectId));
  }

  create(projectId: string, request: CreateTestCaseFolderRequest): Observable<TestCaseFolder> {
    return this.http.post<TestCaseFolder>(this.baseUrl(projectId), request);
  }

  update(projectId: string, folderId: string, request: UpdateTestCaseFolderRequest): Observable<TestCaseFolder> {
    return this.http.put<TestCaseFolder>(`${this.baseUrl(projectId)}/${folderId}`, request);
  }

  delete(projectId: string, folderId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl(projectId)}/${folderId}`);
  }

  reorder(projectId: string, request: ReorderFoldersRequest): Observable<TestCaseFolder[]> {
    return this.http.put<TestCaseFolder[]>(`${this.baseUrl(projectId)}/reorder`, request);
  }

  moveTestCases(projectId: string, request: MoveTestCasesRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl(projectId)}/move-test-cases`, request);
  }
}
