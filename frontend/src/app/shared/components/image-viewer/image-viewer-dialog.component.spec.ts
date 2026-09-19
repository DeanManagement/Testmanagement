import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { provideTranslateService } from '@ngx-translate/core';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ImageViewerData, ImageViewerDialogComponent } from './image-viewer-dialog.component';

/** PRD-051: the one full-size view behind every thumbnail. */
describe('ImageViewerDialogComponent', () => {
  const DATA_URL = 'data:image/png;base64,iVBORw0KGgo=';
  let fixture: ComponentFixture<ImageViewerDialogComponent>;
  let http: HttpTestingController;
  let savedAs: string[];

  function open(data: ImageViewerData): void {
    TestBed.configureTestingModule({
      imports: [ImageViewerDialogComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideTranslateService(),
        { provide: MAT_DIALOG_DATA, useValue: data }],
    });
    http = TestBed.inject(HttpTestingController);
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:x');
    globalThis.URL.revokeObjectURL = vi.fn();
    savedAs = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      savedAs.push(this.download);
    });
    fixture = TestBed.createComponent(ImageViewerDialogComponent);
    fixture.detectChanges();
  }

  function byTestId(id: string): HTMLElement {
    return fixture.nativeElement.querySelector(`[data-test-id="${id}"]`);
  }

  afterEach(() => vi.restoreAllMocks());

  it('names the image it shows', () => {
    open({ src: DATA_URL, fileName: 'step.png' });

    expect(byTestId('image-viewer-name').textContent).toContain('step.png');
  });

  it('switches between fit and 100 %', () => {
    open({ src: DATA_URL, fileName: 'step.png' });

    byTestId('image-viewer-size').click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.viewer-body').classList).toContain('actual');
  });

  it('downloads an image not yet uploaded under its name', () => {
    open({ src: DATA_URL, fileName: 'step.png' });

    byTestId('image-viewer-download').click();

    expect(savedAs).toEqual(['step.png']);
  });

  it('downloads a stored image through the API under its stored name', () => {
    open({ src: '/api/screenshots/s1', fileName: 'checkout.png' });
    http.expectOne('/api/screenshots/s1').flush(new Blob(['png']));

    byTestId('image-viewer-download').click();
    http.expectOne('/api/screenshots/s1').flush(new Blob(['png']));

    expect(savedAs).toEqual(['checkout.png']);
    http.verify();
  });
});
