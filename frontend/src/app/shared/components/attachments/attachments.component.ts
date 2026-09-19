import { Component, DestroyRef, inject, Input, OnChanges, signal, SimpleChanges } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { NgTemplateOutlet } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { filter, switchMap, take } from 'rxjs/operators';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { Attachment } from '../../models/test-case.model';
import { AuthImagePipe } from '../../pipes/auth-image.pipe';
import { LocalizedDatePipe } from '../../pipes/localized-date.pipe';
import { ConfirmDialogComponent, ConfirmDialogData } from '../confirm-dialog/confirm-dialog.component';

const IMAGE_TYPES = new Set(['image/png', 'image/jpeg', 'image/gif', 'image/webp']);
const HTTP_CONTENT_TOO_LARGE = 413;
const BYTES_PER_KB = 1024;

/**
 * Files attached to a test case (PRD-044). On the case page a tester uploads, downloads and deletes;
 * while executing a run ({@code execution}) the list is read-only, collapsed, and absent when empty.
 *
 * <p>Self-contained like {@code IssueLinksComponent}: the list belongs to one case and nothing else
 * reads it, so it has no store slice. Downloads go through HttpClient so the JWT travels in the
 * header, never in a URL.
 */
@Component({
  selector: 'app-attachments',
  standalone: true,
  imports: [
    NgTemplateOutlet,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
    TranslateModule,
    AuthImagePipe,
    LocalizedDatePipe,
  ],
  templateUrl: './attachments.component.html',
  styleUrl: './attachments.component.scss',
})
export class AttachmentsComponent implements OnChanges {
  private readonly api = inject(TestCaseApiService);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) projectId!: string;
  @Input({ required: true }) testCaseId!: string;
  /** TESTER and up: upload and delete. */
  @Input() canEdit = false;
  /** In the run execution view: read-only, collapsed, hidden when there is nothing attached. */
  @Input() execution = false;

  readonly attachments = signal<Attachment[]>([]);
  readonly loaded = signal(false);
  /** Upload progress in percent, or null when no upload is running. */
  readonly progress = signal<number | null>(null);
  readonly error = signal<string | null>(null);
  readonly duplicateOf = signal<string | null>(null);
  readonly dragging = signal(false);

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['testCaseId'] || changes['projectId']) && this.projectId && this.testCaseId) {
      this.load();
    }
  }

  private load(): void {
    this.loaded.set(false);
    this.api.getAttachments(this.projectId, this.testCaseId)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (attachments) => {
          this.attachments.set(attachments);
          this.loaded.set(true);
        },
        error: () => {
          this.attachments.set([]);
          this.loaded.set(true);
        },
      });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (file) {
      this.upload(file);
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file && this.canEdit) {
      this.upload(file);
    }
  }

  upload(file: File): void {
    if (this.progress() !== null) {
      return;
    }
    this.error.set(null);
    this.duplicateOf.set(null);
    this.progress.set(0);
    this.api.uploadAttachment(this.projectId, this.testCaseId, file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress && event.total) {
            this.progress.set(Math.round((100 * event.loaded) / event.total));
          } else if (event.type === HttpEventType.Response && event.body) {
            this.added(event.body);
          }
        },
        error: (err: HttpErrorResponse) => {
          this.progress.set(null);
          this.error.set(err.status === HTTP_CONTENT_TOO_LARGE
            ? 'attachment.tooLarge'
            : (err.error?.message ?? 'attachment.uploadFailed'));
        },
      });
  }

  /** Same bytes twice is allowed, since people re-upload on purpose, but worth a word. */
  private added(attachment: Attachment): void {
    const twin = this.attachments().find((a) => a.sha256 === attachment.sha256);
    this.duplicateOf.set(twin?.fileName ?? null);
    this.attachments.update((list) => [...list, attachment]);
    this.progress.set(null);
  }

  download(attachment: Attachment): void {
    this.api.downloadAttachment(this.projectId, this.testCaseId, attachment.id)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = attachment.fileName;
        a.click();
        setTimeout(() => URL.revokeObjectURL(url));
      });
  }

  remove(attachment: Attachment): void {
    this.dialog.open(ConfirmDialogComponent, {
      data: {
        titleKey: 'attachment.delete',
        messageKey: 'attachment.deleteConfirm',
        messageParams: { name: attachment.fileName },
        danger: true,
      } as ConfirmDialogData,
    }).afterClosed().pipe(
      filter(Boolean),
      switchMap(() => this.api.deleteAttachment(this.projectId, this.testCaseId, attachment.id)),
      take(1),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => this.attachments.update((list) => list.filter((a) => a.id !== attachment.id)));
  }

  isImage(attachment: Attachment): boolean {
    return IMAGE_TYPES.has(attachment.contentType);
  }

  imageUrl(attachment: Attachment): string {
    return `${this.api.attachmentsUrl(this.projectId, this.testCaseId)}/${attachment.id}`;
  }

  icon(attachment: Attachment): string {
    if (this.isImage(attachment)) return 'image';
    if (attachment.contentType === 'application/pdf') return 'picture_as_pdf';
    if (attachment.contentType.startsWith('text/') || attachment.contentType.endsWith('json')
      || attachment.contentType.endsWith('xml')) return 'description';
    return 'folder_zip';
  }

  formatSize(bytes: number): string {
    if (bytes < BYTES_PER_KB) return `${bytes} B`;
    if (bytes < BYTES_PER_KB * BYTES_PER_KB) return `${(bytes / BYTES_PER_KB).toFixed(1)} KB`;
    return `${(bytes / (BYTES_PER_KB * BYTES_PER_KB)).toFixed(1)} MB`;
  }
}
