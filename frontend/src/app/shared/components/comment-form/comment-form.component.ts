import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';

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

  @Output() submitComment = new EventEmitter<string>();
  @Output() cancelEdit = new EventEmitter<void>();

  content = '';
  maxLength = 2000;

  ngOnChanges(): void {
    this.content = this.initialContent;
  }

  onSubmit(): void {
    const trimmed = this.content.trim();
    if (trimmed) {
      this.submitComment.emit(trimmed);
      if (!this.isEditing) {
        this.content = '';
      }
    }
  }

  onCancel(): void {
    this.content = this.initialContent;
    this.cancelEdit.emit();
  }
}
