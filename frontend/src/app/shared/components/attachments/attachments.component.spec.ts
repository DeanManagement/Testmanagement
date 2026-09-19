import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideTranslateService } from '@ngx-translate/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AttachmentsComponent } from './attachments.component';
import { Attachment } from '../../models/test-case.model';

/**
 * PRD-044/051: the card on a case or bug page, the queue in the Report Bug form, and the read-only
 * list while executing a run.
 */
describe('AttachmentsComponent', () => {
  const url = '/api/projects/p1/test-cases/tc1/attachments';

  let fixture: ComponentFixture<AttachmentsComponent>;
  let component: AttachmentsComponent;
  let http: HttpTestingController;

  function attachment(id: string, fileName = `${id}.pdf`, sha256 = `sha-${id}`): Attachment {
    return {
      id, testCaseId: 'tc1', bugReportId: null, fileName, contentType: 'application/pdf', sizeBytes: 2048, sha256,
      createdAt: '2026-09-19T10:00:00Z', createdBy: null,
    };
  }

  function setUp(inputs: { canEdit?: boolean; execution?: boolean }, existing: Attachment[]): void {
    fixture = TestBed.createComponent(AttachmentsComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('url', url);
    fixture.componentRef.setInput('canEdit', inputs.canEdit ?? false);
    fixture.componentRef.setInput('execution', inputs.execution ?? false);
    fixture.detectChanges();
    http.expectOne(url).flush(existing);
    fixture.detectChanges();
  }

  function byTestId(id: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-test-id="${id}"]`);
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AttachmentsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideTranslateService(), provideAnimationsAsync()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists the case attachments', () => {
    setUp({ canEdit: true }, [attachment('a1'), attachment('a2')]);

    expect(fixture.nativeElement.querySelectorAll('.attachment-row').length).toBe(2);
  });

  it('hides upload and delete from a viewer', () => {
    setUp({ canEdit: false }, [attachment('a1')]);

    expect(byTestId('attachment-upload-btn')).toBeNull();
    expect(byTestId('attachment-delete')).toBeNull();
  });

  it('offers upload and delete to a tester', () => {
    setUp({ canEdit: true }, [attachment('a1')]);

    expect(byTestId('attachment-upload-btn')).not.toBeNull();
    expect(byTestId('attachment-delete')).not.toBeNull();
  });

  it('appends an uploaded file to the list', () => {
    setUp({ canEdit: true }, []);

    component.addFiles([new File(['%PDF-'], 'spec.pdf', { type: 'application/pdf' })]);
    http.expectOne(url).flush(attachment('a1', 'spec.pdf'));

    expect(component.attachments().map((a) => a.fileName)).toEqual(['spec.pdf']);
    expect(component.progress()).toBeNull();
  });

  it('shows the server validation message inline', () => {
    setUp({ canEdit: true }, []);

    component.addFiles([new File(['<html>'], 'x.pdf', { type: 'application/pdf' })]);
    http.expectOne(url).flush({ message: 'The file\'s content does not match its type application/pdf' },
      { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(byTestId('attachment-error')?.textContent).toContain('does not match');
    expect(component.attachments()).toEqual([]);
  });

  it('warns when the same bytes are already attached', () => {
    setUp({ canEdit: true }, [attachment('a1', 'first.pdf', 'same')]);

    component.addFiles([new File(['%PDF-'], 'again.pdf', { type: 'application/pdf' })]);
    http.expectOne(url).flush(attachment('a2', 'again.pdf', 'same'));

    expect(component.duplicateOf()).toBe('first.pdf');
  });

  describe('in the run execution view', () => {
    it('renders nothing when the case has no attachments', () => {
      setUp({ execution: true }, []);

      expect(byTestId('attachments-execution')).toBeNull();
    });

    it('lists attachments collapsed and read-only, even for a tester', () => {
      setUp({ execution: true, canEdit: true }, [attachment('a1')]);

      const details = byTestId('attachments-execution') as HTMLDetailsElement;
      expect(details.open).toBe(false);
      expect(byTestId('attachment-delete')).toBeNull();
      expect(byTestId('attachment-upload-btn')).toBeNull();
    });
  });
  it('uploads several files one after another', () => {
    setUp({ canEdit: true }, []);

    component.addFiles([new File(['a'], 'a.txt', { type: 'text/plain' }), new File(['b'], 'b.txt', { type: 'text/plain' })]);
    http.expectOne(url).flush(attachment('a1', 'a.txt'));
    http.expectOne(url).flush(attachment('a2', 'b.txt'));

    expect(component.attachments().map((a) => a.fileName)).toEqual(['a.txt', 'b.txt']);
  });

  it('keeps uploading the rest when one file is refused', () => {
    setUp({ canEdit: true }, []);

    component.addFiles([new File(['<svg/>'], 'x.svg'), new File(['b'], 'b.txt', { type: 'text/plain' })]);
    http.expectOne(url).flush({ message: 'refused' }, { status: 400, statusText: 'Bad Request' });
    http.expectOne(url).flush(attachment('a2', 'b.txt'));

    expect(component.attachments().map((a) => a.fileName)).toEqual(['b.txt']);
    expect(component.error()).toBe('refused');
  });

  it('attaches an image pasted anywhere on the page', () => {
    setUp({ canEdit: true }, []);

    document.dispatchEvent(paste([new File(['png'], 'image.png', { type: 'image/png' })]));

    http.expectOne(url).flush(attachment('a1', 'image.png'));
    expect(component.attachments().map((a) => a.fileName)).toEqual(['image.png']);
  });

  it('leaves pasted text alone', () => {
    setUp({ canEdit: true }, []);
    const event = paste([]);

    document.dispatchEvent(event);

    expect(event.defaultPrevented).toBe(false);
  });

  it('ignores a paste from a viewer', () => {
    setUp({ canEdit: false }, []);

    document.dispatchEvent(paste([new File(['png'], 'image.png', { type: 'image/png' })]));

    http.expectNone(url);
  });

  it('opens the image viewer from a thumbnail', async () => {
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:x');
    setUp({ canEdit: true }, [{ ...attachment('a1', 'shot.png'), contentType: 'image/png' }]);
    http.expectOne(`${url}/a1`).flush(new Blob(['png']));

    (fixture.nativeElement.querySelector('.attachment-thumb') as HTMLElement).click();
    await fixture.whenStable();

    expect(document.querySelector('[data-test-id="image-viewer-name"]')?.textContent).toContain('shot.png');
  });

  describe('before the owner is saved', () => {
    beforeEach(() => {
      fixture = TestBed.createComponent(AttachmentsComponent);
      component = fixture.componentInstance;
      fixture.componentRef.setInput('url', null);
      fixture.componentRef.setInput('canEdit', true);
      fixture.detectChanges();
    });

    it('queues files instead of uploading them, and reports the queue', () => {
      const emitted: File[][] = [];
      component.queuedChange.subscribe((files) => emitted.push(files));
      const file = new File(['a'], 'a.txt', { type: 'text/plain' });

      component.addFiles([file]);
      fixture.detectChanges();

      http.expectNone(() => true);
      expect(emitted).toEqual([[file]]);
      expect(byTestId('attachment-queue')?.textContent).toContain('a.txt');
    });

    it('takes a queued file back out', () => {
      const file = new File(['a'], 'a.txt', { type: 'text/plain' });
      component.addFiles([file]);

      component.removeQueued(file);

      expect(component.queued()).toEqual([]);
    });
  });
});

/** A paste event carrying files, which jsdom's ClipboardEvent cannot be constructed with. */
function paste(files: File[]): Event {
  const event = new Event('paste', { cancelable: true });
  Object.defineProperty(event, 'clipboardData', { value: { files } });
  return event;
}
