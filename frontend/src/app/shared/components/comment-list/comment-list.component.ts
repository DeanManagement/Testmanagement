import { Component, EventEmitter, Input, Output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { Comment } from '../../models/comment.model';

@Component({
  selector: 'app-comment-list',
  standalone: true,
  imports: [
    DatePipe,
    MatButtonModule,
    MatIconModule,
    TranslateModule,
  ],
  templateUrl: './comment-list.component.html',
  styleUrl: './comment-list.component.scss',
})
export class CommentListComponent {
  @Input() comments: Comment[] = [];
  @Input() currentUserId: string | null = null;
  @Input() isAdmin = false;

  @Output() editComment = new EventEmitter<Comment>();
  @Output() deleteComment = new EventEmitter<Comment>();

  isEdited(comment: Comment): boolean {
    return comment.createdAt !== comment.updatedAt;
  }

  canEdit(comment: Comment): boolean {
    return this.currentUserId !== null && comment.authorId === this.currentUserId;
  }

  canDelete(comment: Comment): boolean {
    return this.canEdit(comment) || this.isAdmin;
  }
}
