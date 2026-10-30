import { Component, ElementRef, EventEmitter, Input, OnChanges, Output, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { CreateCommentRequest, MentionCandidate } from '../../models/comment.model';

/** An "@" at the start or after whitespace, then what has been typed of the name so far. */
const MENTION_TRIGGER = /(^|\s)@([^\s@]*)$/;
const MAX_SUGGESTIONS = 6;

@Component({
  selector: 'app-comment-form',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    TranslateModule,
  ],
  templateUrl: './comment-form.component.html',
  styleUrl: './comment-form.component.scss',
})
export class CommentFormComponent implements OnChanges {
  @Input() initialContent = '';
  @Input() isEditing = false;
  /** People who can be mentioned with "@" (TM44); none turns mentions off. */
  @Input() mentionCandidates: MentionCandidate[] = [];

  @Output() submitComment = new EventEmitter<CreateCommentRequest>();
  @Output() cancelEdit = new EventEmitter<void>();

  @ViewChild('textarea') textareaRef?: ElementRef<HTMLTextAreaElement>;

  content = '';
  maxLength = 2000;
  /** What follows the "@" being typed, or null when no mention is being typed. */
  mentionQuery: string | null = null;
  private readonly picked = new Map<string, string>();

  /** Focus the comment textarea — used by the test-run keyboard shortcut. */
  focus(): void {
    this.textareaRef?.nativeElement.focus();
  }

  ngOnChanges(): void {
    this.content = this.initialContent;
  }

  get suggestions(): MentionCandidate[] {
    const query = this.mentionQuery?.toLowerCase();
    if (query === undefined) {
      return [];
    }
    return this.mentionCandidates
      .filter((c) => c.displayName.toLowerCase().includes(query))
      .slice(0, MAX_SUGGESTIONS);
  }

  onInput(): void {
    if (this.mentionCandidates.length === 0) {
      return;
    }
    this.mentionQuery = this.textBeforeCaret().match(MENTION_TRIGGER)?.[2] ?? null;
  }

  onKeydown(event: KeyboardEvent): void {
    if (this.mentionQuery === null) {
      return;
    }
    if (event.key === 'Escape') {
      this.mentionQuery = null;
    } else if (event.key === 'Enter' && this.suggestions.length > 0) {
      event.preventDefault();
      this.pick(this.suggestions[0]);
    }
  }

  /** Replaces the "@partial" before the caret with the full name. */
  pick(candidate: MentionCandidate): void {
    const before = this.textBeforeCaret();
    const after = this.content.slice(before.length);
    const replaced = before.replace(/@[^\s@]*$/, `@${candidate.displayName} `);
    this.content = replaced + after;
    this.picked.set(candidate.userId, candidate.displayName);
    this.mentionQuery = null;
    const textarea = this.textareaRef?.nativeElement;
    if (textarea) {
      textarea.value = this.content;
      textarea.focus();
      textarea.setSelectionRange(replaced.length, replaced.length);
    }
  }

  onSubmit(): void {
    const trimmed = this.content.trim();
    if (trimmed) {
      // A picked name the author deleted again is no longer a mention.
      const mentionedUserIds = [...this.picked]
        .filter(([, name]) => trimmed.includes(`@${name}`))
        .map(([userId]) => userId);
      this.submitComment.emit(mentionedUserIds.length > 0 ? { content: trimmed, mentionedUserIds } : { content: trimmed });
      this.picked.clear();
      this.mentionQuery = null;
      if (!this.isEditing) {
        this.content = '';
      }
    }
  }

  onCancel(): void {
    this.content = this.initialContent;
    this.cancelEdit.emit();
  }

  private textBeforeCaret(): string {
    const caret = this.textareaRef?.nativeElement.selectionStart ?? this.content.length;
    return this.content.slice(0, caret);
  }
}
