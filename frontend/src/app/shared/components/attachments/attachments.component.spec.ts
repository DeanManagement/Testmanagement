import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideTranslateService } from '@ngx-translate/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AttachmentsComponent } from './attachments.component';
import { Attachment } from '../../models/test-case.model';

/** PRD-044: the card on the case page, and the read-only list while executing a run. */
describe('AttachmentsComponent', () => {
  const url = '/api/projects/p1/test-cases/tc1/attachments';

  let fixture: ComponentFixture<AttachmentsComponent>;
  let component: AttachmentsComponent;
  let http: HttpTestingController;

  function attachment(id: string, fileName = `${id}.pdf`, sha256 = `sha-${id}`): Attachment {
    return {
      id, testCaseId: 'tc1', fileName, contentType: 'application/pdf', sizeBytes: 2048, sha256,
      createdAt: '2026-09-19T10:00:00Z', createdBy: null,
    };
  }

  function setUp(inputs: { canEdit?: boolean; execution?: boolean }, existing: Attachment[]): void {
    fixture = TestBed.createComponent(AttachmentsComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.componentRef.setInput('testCaseId', 'tc1');
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

    component.upload(new File(['%PDF-'], 'spec.pdf', { type: 'application/pdf' }));
    http.expectOne(url).flush(attachment('a1', 'spec.pdf'));

    expect(component.attachments().map((a) => a.fileName)).toEqual(['spec.pdf']);
    expect(component.progress()).toBeNull();
  });

  it('shows the server validation message inline', () => {
    setUp({ canEdit: true }, []);

    component.upload(new File(['<html>'], 'x.pdf', { type: 'application/pdf' }));
    http.expectOne(url).flush({ message: 'The file\'s content does not match its type application/pdf' },
      { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(byTestId('attachment-error')?.textContent).toContain('does not match');
    expect(component.attachments()).toEqual([]);
  });

  it('warns when the same bytes are already attached', () => {
    setUp({ canEdit: true }, [attachment('a1', 'first.pdf', 'same')]);

    component.upload(new File(['%PDF-'], 'again.pdf', { type: 'application/pdf' }));
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
});
