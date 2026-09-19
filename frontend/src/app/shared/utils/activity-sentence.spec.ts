import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';
import de from '../../../assets/i18n/de.json';
import en from '../../../assets/i18n/en.json';
import { activitySentence, ActivityFact } from './activity-sentence';

const entry = (over: Partial<ActivityFact>): ActivityFact =>
  ({ actor: 'Anna', action: 'DELETED', entityType: 'TEST_RUN', entityName: 'Smoke', ...over });

describe('activitySentence', () => {
  let translate: TranslateService;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [TranslateModule.forRoot()] });
    translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.setTranslation('de', de);
  });

  it('puts the German verb last and gives the object its article', () => {
    translate.use('de');

    expect(activitySentence(translate, entry({}))).toBe('Anna hat den Testlauf «Smoke» gelöscht');
  });

  it('reads as an English sentence', () => {
    translate.use('en');

    expect(activitySentence(translate, entry({ action: 'STATUS_CHANGED' })))
      .toBe('Anna changed the status of test run "Smoke"');
  });

  it('leaves the name out when there is none', () => {
    translate.use('de');

    expect(activitySentence(translate, entry({ actor: 'System', action: 'CREATED', entityType: 'COMMENT', entityName: null })))
      .toBe('System hat einen Kommentar erstellt');
  });

  it('keeps a separable German verb together', () => {
    translate.use('de');

    expect(activitySentence(translate, entry({ action: 'COMPLETED', entityType: 'TEST_PLAN', entityName: 'R3' })))
      .toBe('Anna hat den Testplan «R3» abgeschlossen');
  });
});
