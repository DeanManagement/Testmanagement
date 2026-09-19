import { CaseContextComponent } from '../case-context/case-context.component';
import { ExecutionHistoryComponent } from '../execution-history/execution-history.component';
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
import { combineLatest, Observable, of } from 'rxjs';
import { filter, switchMap, take } from 'rxjs/operators';
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
import { ExecutedStep, expandSteps, sharedStepHeadingAt } from '../../../shared/utils/shared-step-groups';
import { TestCaseParametersComponent } from '../test-case-parameters/test-case-parameters.component';

import { EnvironmentResultsComponent } from '../environment-results/environment-results.component';

import { TestCaseReviewComponent } from '../review/test-case-review.component';
import { statusLabelKey } from '../review/review-status';
import { ProjectApiService } from '../../../core/services/project-api.service';
import { ProjectMemberApiService } from '../../../core/services/project-member-api.service';
import { AttachmentsComponent } from '../../../shared/components/attachments/attachments.component';
import { CustomFieldsDisplayComponent } from '../../../shared/components/custom-fields/custom-fields-display.component';
import { DurationPipe } from '../../../shared/pipes/duration.pipe';
import { millisToMinutes } from '../../../shared/pipes/duration';

@Component({
  selector: 'app-test-case-detail',
  standalone: true,
  imports: [
    CaseContextComponent,
    ExecutionHistoryComponent,
    AttachmentsComponent,
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
  private readonly memberApi = inject(ProjectMemberApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  projectId = '';
  testCaseId = '';
  testCase$: Observable<TestCase | undefined> = of(undefined);
  /** PRD-033: whether ACTIVE means approved in this project. */
  reviewRequired = false;
  /** TESTER and up may upload and delete attachments; viewers see them read-only. */
  canWrite = false;
  readonly statusLabelKey = statusLabelKey;
  @ViewChild('versions') private versions?: TestCaseVersionsComponent;
  @ViewChild('versionsSection') private versionsSection?: ElementRef<HTMLElement>;
  get attachmentsUrl(): string {
    return this.testCaseApi.attachmentsUrl(this.projectId, this.testCaseId);
  }

  getStepImageUrl(imageId: string): string {
    return this.testCaseApi.getStepImageUrl(imageId);
  }

  readonly headingAt = sharedStepHeadingAt;
  private expandedFor: { testCase: TestCase; steps: ExecutedStep[] } | null = null;

  /** The steps as executed, shared steps in place (PRD-030); kept per case so inputs stay stable. */
  executedSteps(testCase: TestCase): ExecutedStep[] {
    if (this.expandedFor?.testCase !== testCase) {
      this.expandedFor = { testCase, steps: expandSteps(testCase.steps) };
    }
    return this.expandedFor.steps;
  }

  /** The server copies the shared step's steps and images into the case, and writes a version. */
  convertToLocal(referenceStepId: string): void {
    this.dialog.open(ConfirmDialogComponent, {
      data: { titleKey: 'sharedStep.convert', messageKey: 'sharedStep.convertConfirm' } as ConfirmDialogData,
    }).afterClosed().pipe(
      filter(Boolean),
      switchMap(() => this.testCaseApi.inlineSharedStep(this.projectId, this.testCaseId, referenceStepId)),
      take(1),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((testCase) => {
      this.store.dispatch(TestCaseActions.updateTestCaseSuccess({ testCase }));
      this.versions?.reload();
    });
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
      combineLatest([this.memberApi.getByProject(this.projectId), this.authUser$.pipe(take(1))])
        .pipe(take(1), takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: ([members, user]) => {
            const role = members.find((m) => m.userId === user?.id)?.role;
            this.canWrite = !!user && (user.systemAdmin || role === 'ADMIN' || role === 'TESTER');
            this.cdr.detectChanges();
          },
          error: () => undefined,
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
