import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideTranslateService } from '@ngx-translate/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { ComponentFixture } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { IssueLinksComponent } from './issue-links.component';
import { IssueLink } from '../../models/issue-tracker.model';

/**
 * The interesting behaviour here is bookkeeping around re-linking: the backend returns the same row
 * when an already-linked issue is linked again, so the list must update in place rather than grow.
 */
describe('IssueLinksComponent', () => {

  const PROJECT = 'p1';
  const RUN = 'r1';
  const RESULT = 'res1';
  const linksUrl = `/api/projects/${PROJECT}/test-runs/${RUN}/results/${RESULT}/issues`;

  let fixture: ComponentFixture<IssueLinksComponent>;
  let component: IssueLinksComponent;
  let http: HttpTestingController;

  function link(id: string, externalId: string, state: IssueLink['state'] = 'OPEN'): IssueLink {
    return {
      id,
      testResultId: RESULT,
      provider: 'GITLAB',
      externalId,
      url: `https://gitlab.test/${externalId}`,
      title: 'An issue',
      state,
      stateCheckedAt: null,
    };
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [IssueLinksComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslateService(),
        provideAnimationsAsync(),
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(IssueLinksComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('projectId', PROJECT);
    fixture.componentRef.setInput('runId', RUN);
    fixture.componentRef.setInput('resultId', RESULT);
    fixture.componentRef.setInput('canEdit', true);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
  });

  function flushInitialLoad(links: IssueLink[] = []): void {
    http.expectOne(linksUrl).flush(links);
  }

  it('loads existing links for the result', () => {
    flushInitialLoad([link('l1', 'group/project#7')]);

    expect(component.links.length).toBe(1);
    expect(component.links[0].externalId).toBe('group/project#7');
    expect(component.loading).toBe(false);
  });

  it('keeps the section usable when loading fails', () => {
    http.expectOne(linksUrl).error(new ProgressEvent('network error'));

    expect(component.links).toEqual([]);
    expect(component.loading).toBe(false);
  });

  it('replaces rather than duplicates when an already-linked issue is linked again', () => {
    flushInitialLoad([link('l1', 'group/project#7', 'OPEN')]);

    component.linkExisting({
      externalId: 'group/project#7',
      url: 'https://gitlab.test/7',
      title: 'An issue',
      state: 'CLOSED',
    });
    http.expectOne(linksUrl).flush(link('l1', 'group/project#7', 'CLOSED'));

    expect(component.links.length).toBe(1);
    expect(component.links[0].state).toBe('CLOSED');
  });

  it('appends a genuinely new link', () => {
    flushInitialLoad([link('l1', 'group/project#7')]);

    component.linkExisting({
      externalId: 'group/project#9',
      url: 'https://gitlab.test/9',
      title: 'Another',
      state: 'OPEN',
    });
    http.expectOne(linksUrl).flush(link('l2', 'group/project#9'));

    expect(component.links.map((l) => l.id)).toEqual(['l1', 'l2']);
  });

  it('files a new issue without sending an external id', () => {
    flushInitialLoad();

    component.createIssue();
    const request = http.expectOne(linksUrl);

    expect(request.request.body).toEqual({ create: true });
    request.flush(link('l1', 'group/project#12'));
    expect(component.links.length).toBe(1);
  });

  it('removes a link on unlink', () => {
    const existing = link('l1', 'group/project#7');
    flushInitialLoad([existing]);

    component.unlink(existing);
    http.expectOne(`${linksUrl}/l1`).flush(null);

    expect(component.links).toEqual([]);
  });

  it('leaves the list untouched when unlink fails', () => {
    const existing = link('l1', 'group/project#7');
    flushInitialLoad([existing]);

    component.unlink(existing);
    http.expectOne(`${linksUrl}/l1`).error(new ProgressEvent('boom'));

    expect(component.links.length).toBe(1);
    expect(component.busy).toBe(false);
  });

  it('does not call refresh when there is nothing to refresh', () => {
    flushInitialLoad();

    component.refresh();

    // No outstanding request — http.verify() in afterEach would fail if one were made.
    expect(component.busy).toBe(false);
  });

  it('keeps cached state when a refresh fails', () => {
    flushInitialLoad([link('l1', 'group/project#7', 'OPEN')]);

    component.refresh();
    http.expectOne(`${linksUrl}/refresh`).error(new ProgressEvent('tracker down'));

    expect(component.links[0].state).toBe('OPEN');
    expect(component.busy).toBe(false);
  });

  it('does not search on an empty term', () => {
    flushInitialLoad();
    component.openSearch();

    component.onSearchInput('   ');

    expect(component.searchResults).toEqual([]);
  });
});
