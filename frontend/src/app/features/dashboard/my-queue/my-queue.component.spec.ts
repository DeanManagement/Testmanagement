import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { MyQueueComponent } from './my-queue.component';
import { MyQueueResponse } from '../../../shared/models/my-queue.model';

/**
 * The widget used to hold its state in plain fields. The app is zoneless, so writing one from an
 * HTTP callback notifies nothing: the response arrived, the component knew it was no longer
 * loading, and the spinner still sat on the dashboard until some unrelated interaction happened to
 * trigger change detection. The empty response is the important case — it is the one most users
 * hit, and it renders nothing, so a stuck spinner is all they ever see.
 */
describe('MyQueueComponent', () => {
  let fixture: ComponentFixture<MyQueueComponent>;
  let http: HttpTestingController;

  const EMPTY: MyQueueResponse = {
    dueTestPlans: [],
    inProgressRuns: [],
    staleBugReports: [],
    oldDraftTestCases: [],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MyQueueComponent, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    fixture = TestBed.createComponent(MyQueueComponent);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function spinner(): Element | null {
    return fixture.nativeElement.querySelector('[data-test-id="my-queue-loading"]');
  }

  async function respondWith(body: MyQueueResponse | null, status?: number) {
    fixture.detectChanges();
    const request = http.expectOne('/api/me/queue');
    if (status) {
      request.flush(null, { status, statusText: 'error' });
    } else {
      request.flush(body);
    }
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('shows the spinner until the response arrives', async () => {
    fixture.detectChanges();
    expect(spinner()).not.toBeNull();
    http.expectOne('/api/me/queue').flush(EMPTY);
  });

  it('clears the spinner on an empty queue', async () => {
    await respondWith(EMPTY);

    expect(spinner()).toBeNull();
    // An empty queue renders nothing at all — the widget is deliberately invisible rather than
    // showing an empty container.
    expect(fixture.nativeElement.querySelector('[data-test-id="my-queue"]')).toBeNull();
  });

  it('clears the spinner and renders the card when there is something to do', async () => {
    await respondWith({
      ...EMPTY,
      inProgressRuns: [{
        id: 'a3f1c2d4-0000-0000-0000-000000000001',
        key: 'TES-Run-1',
        name: 'Release smoke',
        projectId: 'b4e2d3c5-0000-0000-0000-000000000002',
        updatedAt: '2026-08-06T08:00:00Z',
      }],
    } as MyQueueResponse);

    expect(spinner()).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-test-id="my-queue-in-progress-runs"]'))
        .not.toBeNull();
  });

  it('clears the spinner when the request fails', async () => {
    // The widget fails silently, but it must not fail *visibly* by spinning forever.
    await respondWith(null, 500);

    expect(spinner()).toBeNull();
  });
});
