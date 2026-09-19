import {
  Component, DestroyRef, EventEmitter, HostListener, inject, Input, OnChanges, Output, signal, SimpleChanges,
} from '@angular/core';
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
import { AttachmentApiService } from '../../../core/services/attachment-api.service';
import { Attachment } from '../../models/test-case.model';
import { AuthImagePipe } from '../../pipes/auth-image.pipe';
import { LocalizedDatePipe } from '../../pipes/localized-date.pipe';
import { saveFile } from '../../utils/save-file';
import { ConfirmDialogComponent, ConfirmDialogData } from '../confirm-dialog/confirm-dialog.component';
import { EnlargeImageDirective } from '../image-viewer/enlarge-image.directive';

const IMAGE_TYPES = new Set(['image/png', 'image/jpeg', 'image/gif', 'image/webp']);
const HTTP_CONTENT_TOO_LARGE = 413;
const BYTES_PER_KB = 1024;

/**
 * Files attached to a test case (PRD-044) or a bug report (PRD-051). A tester uploads by picker,
 * drag and drop or paste, downloads and deletes; while executing a run ({@code execution}) the list is
 * read-only, collapsed, and absent when empty. Image thumbnails open the shared viewer.
 *
 * <p>Without a {@code url} the owner is not saved yet (the Report Bug form): files queue here and
 * {@code queuedChange} hands them to the form, which uploads them once the owner exists.
 *
 * <p>Self-contained like {@code IssueLinksComponent}: the list belongs to one owner and nothing else
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
    EnlargeImageDirective,
    LocalizedDatePipe,
  ],
  templateUrl: './attachments.component.html',
  styleUrl: './attachments.component.scss',
})
export class AttachmentsComponent implements OnChanges {
  private readonly api = inject(AttachmentApiService);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  /** The owner's attachment collection; null while the owner is not saved yet. */
  @Input({ required: true }) url!: string | null;
  /** TESTER and up: upload and delete. */
  @Input() canEdit = false;
  /** In the run execution view: read-only, collapsed, hidden when there is nothing attached. */
  @Input() execution = false;
  /** Files waiting for the owner to be saved; emitted on every change. */
  @Output() readonly queuedChange = new EventEmitter<File[]>();

  readonly attachments = signal<Attachment[]>([]);
  readonly queued = signal<File[]>([]);
  readonly loaded = signal(false);
  /** Upload progress in percent, or null when no upload is running. */
  readonly progress = signal<number | null>(null);
  readonly error = signal<string | null>(null);
  readonly duplicateOf = signal<string | null>(null);
  readonly dragging = signal(false);
  /** Files dropped or pasted while another upload runs; uploaded one after another. */
  private readonly waiting: File[] = [];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['url'] && this.url) {
      this.load(this.url);
    }
  }

  private load(url: string): void {
    this.loaded.set(false);
    this.api.list(url)
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
    const files = Array.from(input.files ?? []);
    input.value = '';
    this.addFiles(files);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    if (this.canEdit) {
      this.addFiles(Array.from(event.dataTransfer?.files ?? []));
    }
  }

  /** A pasted screenshot is attached; pasted text is left to whatever field has the focus. */
  @HostListener('document:paste', ['$event'])
  onPaste(event: ClipboardEvent): void {
    const files = Array.from(event.clipboardData?.files ?? []);
    if (!this.canEdit || this.execution || !files.length || event.defaultPrevented) {
      return;
    }
    event.preventDefault();
    this.addFiles(files);
  }

  addFiles(files: File[]): void {
    if (!files.length) {
      return;
    }
    if (!this.url) {
      this.queued.update((list) => [...list, ...files]);
      this.queuedChange.emit(this.queued());
      return;
    }
    // Cleared per batch, not per file, so a refusal stays visible while the rest upload.
    this.error.set(null);
    this.duplicateOf.set(null);
    this.waiting.push(...files);
    this.uploadNext();
  }

  removeQueued(file: File): void {
    this.queued.update((list) => list.filter((f) => f !== file));
    this.queuedChange.emit(this.queued());
  }

  private uploadNext(): void {
    const file = this.waiting.shift();
    if (!file || this.progress() !== null || !this.url) {
      if (file) {
        this.waiting.unshift(file);
      }
      return;
    }
    this.progress.set(0);
    this.api.upload(this.url, file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress && event.total) {
            this.progress.set(Math.round((100 * event.loaded) / event.total));
          } else if (event.type === HttpEventType.Response && event.body) {
            this.added(event.body);
            this.uploadNext();
          }
        },
        error: (err: HttpErrorResponse) => {
          this.progress.set(null);
          this.error.set(err.status === HTTP_CONTENT_TOO_LARGE
            ? 'attachment.tooLarge'
            : (err.error?.message ?? 'attachment.uploadFailed'));
          this.uploadNext();
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
    if (!this.url) {
      return;
    }
    this.api.download(this.url, attachment.id)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((blob) => saveFile(blob, attachment.fileName));
  }

  remove(attachment: Attachment): void {
    const url = this.url;
    if (!url) {
      return;
    }
    this.dialog.open(ConfirmDialogComponent, {
      data: {
        titleKey: 'attachment.delete',
        messageKey: 'attachment.deleteConfirm',
        messageParams: { name: attachment.fileName },
        danger: true,
      } as ConfirmDialogData,
    }).afterClosed().pipe(
      filter(Boolean),
      switchMap(() => this.api.delete(url, attachment.id)),
      take(1),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => this.attachments.update((list) => list.filter((a) => a.id !== attachment.id)));
  }

  isImage(attachment: Attachment): boolean {
    return IMAGE_TYPES.has(attachment.contentType);
  }

  imageUrl(attachment: Attachment): string {
    return `${this.url}/${attachment.id}`;
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
