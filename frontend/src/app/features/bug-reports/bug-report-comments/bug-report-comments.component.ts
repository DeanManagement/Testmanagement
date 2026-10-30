import { Component, inject, input, OnChanges, signal, SimpleChanges } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { Store } from '@ngrx/store';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import { CommentActions } from '../../../store/comment/comment.actions';
import { selectCommentsForEntity } from '../../../store/comment/comment.selectors';
import { Comment, CreateCommentRequest, MentionCandidate } from '../../../shared/models/comment.model';
import { CommentListComponent } from '../../../shared/components/comment-list/comment-list.component';
import { CommentFormComponent } from '../../../shared/components/comment-form/comment-form.component';

/** The discussion on a bug report (TM44): any member may comment and mention other members. */
@Component({
  selector: 'app-bug-report-comments',
  standalone: true,
  imports: [AsyncPipe, TranslateModule, CommentListComponent, CommentFormComponent],
  template: `
    <app-comment-list
      [comments]="(comments$ | async) ?? []"
      [currentUserId]="currentUserId()"
      [isAdmin]="isAdmin()"
      (editComment)="editing.set($event)"
      (deleteComment)="remove($event)"
    ></app-comment-list>
    @if (editing(); as comment) {
      <app-comment-form
        [initialContent]="comment.content"
        [isEditing]="true"
        (submitComment)="saveEdit(comment, $event)"
        (cancelEdit)="editing.set(null)"
      ></app-comment-form>
    } @else {
      <app-comment-form [mentionCandidates]="mentionCandidates()" (submitComment)="add($event)"></app-comment-form>
    }
  `,
})
export class BugReportCommentsComponent implements OnChanges {
  private readonly store = inject(Store);

  readonly projectId = input.required<string>();
  readonly bugReportId = input.required<string>();
  readonly currentUserId = input<string | null>(null);
  /** Project or system admin: may delete anyone's comment. */
  readonly isAdmin = input(false);
  readonly mentionCandidates = input<MentionCandidate[]>([]);

  readonly editing = signal<Comment | null>(null);
  comments$: Observable<Comment[]> = of([]);

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['projectId'] && !changes['bugReportId']) {
      return;
    }
    this.comments$ = this.store.select(selectCommentsForEntity('BUG_REPORT', this.bugReportId()));
    this.store.dispatch(CommentActions.loadComments({
      projectId: this.projectId(),
      entityType: 'BUG_REPORT',
      entityId: this.bugReportId(),
    }));
  }

  add(request: CreateCommentRequest): void {
    this.store.dispatch(CommentActions.createComment({
      projectId: this.projectId(),
      entityType: 'BUG_REPORT',
      entityId: this.bugReportId(),
      request,
    }));
  }

  saveEdit(comment: Comment, request: CreateCommentRequest): void {
    this.store.dispatch(CommentActions.updateComment({
      projectId: this.projectId(),
      commentId: comment.id,
      request: { content: request.content },
    }));
    this.editing.set(null);
  }

  remove(comment: Comment): void {
    this.store.dispatch(CommentActions.deleteComment({ projectId: this.projectId(), commentId: comment.id }));
  }
}
