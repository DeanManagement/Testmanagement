import { ChangeDetectorRef, Component, DestroyRef, ElementRef, inject, OnInit, ViewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe, LowerCasePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import { take } from 'rxjs/operators';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { TestCaseActions } from '../../../store/test-case/test-case.actions';
import { selectTestCaseById } from '../../../store/test-case/test-case.selectors';
import { TestCase } from '../../../shared/models/test-case.model';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { CommentActions } from '../../../store/comment/comment.actions';
import { selectCommentsForEntity, selectCommentsLoading } from '../../../store/comment/comment.selectors';
import { selectAuthUser, selectIsSystemAdmin } from '../../../store/auth/auth.selectors';
import { Comment } from '../../../shared/models/comment.model';
import { CommentListComponent } from '../../../shared/components/comment-list/comment-list.component';
import { CommentFormComponent } from '../../../shared/components/comment-form/comment-form.component';
import { StepSpecCardComponent } from '../../../shared/components/step-spec-card/step-spec-card.component';
import { EntityHistoryComponent } from '../../../shared/components/entity-history/entity-history.component';
import { TestCaseVersionsComponent } from '../test-case-versions/test-case-versions.component';
import { TestCaseParametersComponent } from '../test-case-parameters/test-case-parameters.component';

import { EnvironmentResultsComponent } from '../environment-results/environment-results.component';

import { TestCaseReviewComponent } from '../review/test-case-review.component';
import { statusLabelKey } from '../review/review-status';
import { ProjectApiService } from '../../../core/services/project-api.service';
import { CustomFieldsDisplayComponent } from '../../../shared/components/custom-fields/custom-fields-display.component';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { millisToMinutes } from '../../../shared/pipes/duration';

@Component({
  selector: 'app-test-case-detail',
  standalone: true,
  imports: [
    DurationPipe,
    CustomFieldsDisplayComponent,
    TestCaseReviewComponent,
    EnvironmentResultsComponent,
    AsyncPipe,
    LowerCasePipe,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    TranslateModule,
    CommentListComponent,
    CommentFormComponent,
    StepSpecCardComponent,
    EntityHistoryComponent,
    TestCaseVersionsComponent,
    TestCaseParametersComponent,
  ],
  templateUrl: './test-case-detail.component.html',
  styleUrl: './test-case-detail.component.scss',
})
export class TestCaseDetailComponent implements OnInit {
  readonly millisToMinutes = millisToMinutes;

  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly testCaseApi = inject(TestCaseApiService);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);
  private readonly projectApi = inject(ProjectApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  projectId = '';
  testCaseId = '';
  testCase$: Observable<TestCase | undefined> = of(undefined);
  /** PRD-033: whether ACTIVE means approved in this project. */
  reviewRequired = false;
  readonly statusLabelKey = statusLabelKey;
  @ViewChild('versions') private versions?: TestCaseVersionsComponent;
  @ViewChild('versionsSection') private versionsSection?: ElementRef<HTMLElement>;
  getStepImageUrl(imageId: string): string {
    return this.testCaseApi.getStepImageUrl(imageId);
  }

  comments$: Observable<Comment[]> = of([]);
  commentsLoading$ = this.store.select(selectCommentsLoading);
  authUser$ = this.store.select(selectAuthUser);
  isAdmin$ = this.store.select(selectIsSystemAdmin);

  editingComment: Comment | null = null;

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.testCaseId = this.route.snapshot.paramMap.get('tcId') ?? '';
    if (this.projectId && this.testCaseId) {
      this.store.dispatch(TestCaseActions.loadTestCase({ projectId: this.projectId, id: this.testCaseId }));
      this.testCase$ = this.store.select(selectTestCaseById(this.testCaseId));
      this.projectApi.getById(this.projectId).pipe(take(1), takeUntilDestroyed(this.destroyRef))
        .subscribe((project) => {
          this.reviewRequired = project.reviewRequired;
          this.cdr.detectChanges();
        });
      this.comments$ = this.store.select(selectCommentsForEntity('TEST_CASE', this.testCaseId));
      this.store.dispatch(CommentActions.loadComments({
        projectId: this.projectId,
        entityType: 'TEST_CASE',
        entityId: this.testCaseId,
      }));
    }
  }

  /** Review actions change status and may add a comment (request changes). */
  onReviewChanged(): void {
    this.store.dispatch(TestCaseActions.loadTestCase({ projectId: this.projectId, id: this.testCaseId }));
    this.store.dispatch(CommentActions.loadComments({
      projectId: this.projectId,
      entityType: 'TEST_CASE',
      entityId: this.testCaseId,
    }));
  }

  compareWithApproved(approvedVersion: number, testCase: TestCase): void {
    this.versions?.compareVersions(approvedVersion, testCase.currentVersion);
    this.versionsSection?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  deleteTestCase(testCase: TestCase): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: {
        titleKey: 'common.delete',
        messageKey: 'testCase.deleteConfirm',
        messageParams: { title: testCase.title },
        secondaryMessageKey: 'common.irreversibleWarning',
        danger: true,
      } as ConfirmDialogData,
    });
    dialogRef.afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe(confirmed => {
      if (confirmed) {
        this.store.dispatch(TestCaseActions.deleteTestCase({ projectId: this.projectId, id: testCase.id }));
      }
    });
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
