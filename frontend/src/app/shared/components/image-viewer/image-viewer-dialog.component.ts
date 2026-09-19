import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { take } from 'rxjs/operators';
import { AuthImagePipe } from '../../pipes/auth-image.pipe';
import { saveFile } from '../../utils/save-file';

export interface ImageViewerData {
  /** An API URL (fetched with the JWT) or a data: URL of an image not yet uploaded. */
  src: string;
  fileName?: string | null;
}

const FALLBACK_FILE_NAME = 'image';

/**
 * One full-size view for every image in the app (PRD-051 §3.5): fit to the viewport, or 100 % with
 * scrolling. Esc and the backdrop close it, as for any MatDialog.
 */
@Component({
  selector: 'app-image-viewer-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatTooltipModule, TranslateModule, AuthImagePipe],
  template: `
    <div class="viewer-bar">
      <span class="viewer-name" data-test-id="image-viewer-name">{{ data.fileName }}</span>
      <button mat-button type="button" (click)="actualSize.set(!actualSize())" data-test-id="image-viewer-size">
        {{ (actualSize() ? 'imageViewer.fit' : 'imageViewer.actualSize') | translate }}
      </button>
      <button mat-icon-button type="button" (click)="download()" [matTooltip]="'imageViewer.download' | translate"
              [attr.aria-label]="'imageViewer.download' | translate" data-test-id="image-viewer-download">
        <mat-icon>download</mat-icon>
      </button>
      <button mat-icon-button type="button" mat-dialog-close [attr.aria-label]="'common.close' | translate"
              data-test-id="image-viewer-close">
        <mat-icon>close</mat-icon>
      </button>
    </div>
    <div class="viewer-body" [class.actual]="actualSize()">
      <img [src]="data.src | authImage" [alt]="data.fileName ?? ''" data-test-id="image-viewer-img" />
    </div>
  `,
  styles: `
    :host { display: flex; flex-direction: column; max-height: 90vh; }
    .viewer-bar { display: flex; align-items: center; gap: 4px; padding: 4px 8px; }
    .viewer-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 500; }
    .viewer-body { overflow: auto; display: flex; justify-content: center; padding: 0 8px 8px; }
    .viewer-body img { max-width: 100%; max-height: calc(90vh - 64px); object-fit: contain; }
    .viewer-body.actual { justify-content: flex-start; }
    .viewer-body.actual img { max-width: none; max-height: none; }
  `,
})
export class ImageViewerDialogComponent {
  readonly data = inject<ImageViewerData>(MAT_DIALOG_DATA);
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);

  readonly actualSize = signal(false);

  download(): void {
    const fileName = this.data.fileName || FALLBACK_FILE_NAME;
    if (this.data.src.startsWith('data:')) {
      saveFile(this.data.src, fileName);
      return;
    }
    this.http.get(this.data.src, { responseType: 'blob' })
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((blob) => saveFile(blob, fileName));
  }
}
