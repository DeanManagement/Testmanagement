import { Directive, HostListener, inject, input } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { ImageViewerDialogComponent, ImageViewerData } from './image-viewer-dialog.component';

/**
 * Makes a thumbnail open the shared image viewer (PRD-051): click, Enter or Space. Put it on the
 * {@code <img>} with the same URL the thumbnail shows; {@code enlargeName} names the download.
 */
@Directive({
  selector: 'img[appEnlarge]',
  standalone: true,
  host: {
    role: 'button',
    tabindex: '0',
    class: 'enlargeable',
    '[attr.aria-label]': 'label',
  },
})
export class EnlargeImageDirective {
  private readonly dialog = inject(MatDialog);
  /** Read instead of the alt text, so the control says what it does. */
  readonly label = inject(TranslateService).instant('imageViewer.enlarge');

  readonly appEnlarge = input.required<string | null | undefined>();
  readonly enlargeName = input<string | null>(null);

  @HostListener('click', ['$event'])
  @HostListener('keydown.enter', ['$event'])
  @HostListener('keydown.space', ['$event'])
  open(event: Event): void {
    const src = this.appEnlarge();
    if (!src) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    this.dialog.open<ImageViewerDialogComponent, ImageViewerData>(ImageViewerDialogComponent, {
      data: { src, fileName: this.enlargeName() },
      maxWidth: '95vw',
      autoFocus: 'dialog',
    });
  }
}
