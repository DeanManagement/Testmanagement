import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe, LowerCasePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTableModule } from '@angular/material/table';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import { TestCaseActions } from '../../../store/test-case/test-case.actions';
import { selectTestCaseById } from '../../../store/test-case/test-case.selectors';
import { TestCase } from '../../../shared/models/test-case.model';
import { CommentActions } from '../../../store/comment/comment.actions';
import { selectAllComments, selectCommentsLoading } from '../../../store/comment/comment.selectors';
import { selectAuthUser, selectIsSystemAdmin } from '../../../store/auth/auth.selectors';
import { Comment } from '../../../shared/models/comment.model';
import { CommentListComponent } from '../../../shared/components/comment-list/comment-list.component';
import { CommentFormComponent } from '../../../shared/components/comment-form/comment-form.component';

@Component({
  selector: 'app-test-case-detail',
  standalone: true,
  imports: [
    AsyncPipe,
    LowerCasePipe,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatTableModule,
    TranslateModule,
    CommentListComponent,
    CommentFormComponent,
  ],
  templateUrl: './test-case-detail.component.html',
  styleUrl: './test-case-detail.component.scss',
})
export class TestCaseDetailComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);

  projectId = '';
  testCaseId = '';
  testCase$: Observable<TestCase | undefined> = of(undefined);
  stepColumns = ['index', 'action', 'expectedResult'];

  comments$ = this.store.select(selectAllComments);
  commentsLoading$ = this.store.select(selectCommentsLoading);
  authUser$ = this.store.select(selectAuthUser);
  isAdmin$ = this.store.select(selectIsSystemAdmin);

  editingComment: Comment | null = null;

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.testCaseId = this.route.snapshot.paramMap.get('tcId') ?? '';
    if (this.projectId && this.testCaseId) {
      this.store.dispatch(TestCaseActions.loadTestCases({ projectId: this.projectId }));
      this.testCase$ = this.store.select(selectTestCaseById(this.testCaseId));
      this.store.dispatch(CommentActions.loadComments({
        projectId: this.projectId,
        entityType: 'TEST_CASE',
        entityId: this.testCaseId,
      }));
    }
  }

  deleteTestCase(id: string): void {
    this.store.dispatch(TestCaseActions.deleteTestCase({ projectId: this.projectId, id }));
  }

  addComment(content: string): void {
    this.store.dispatch(CommentActions.createComment({
      projectId: this.projectId,
      entityType: 'TEST_CASE',
      entityId: this.testCaseId,
      request: { content },
    }));
  }

  startEditComment(comment: Comment): void {
    this.editingComment = comment;
  }

  saveEditComment(content: string): void {
    if (this.editingComment) {
      this.store.dispatch(CommentActions.updateComment({
        projectId: this.projectId,
        commentId: this.editingComment.id,
        request: { content },
      }));
      this.editingComment = null;
    }
  }

  cancelEditComment(): void {
    this.editingComment = null;
  }

  onDeleteComment(comment: Comment): void {
    this.store.dispatch(CommentActions.deleteComment({
      projectId: this.projectId,
      commentId: comment.id,
    }));
  }
}
