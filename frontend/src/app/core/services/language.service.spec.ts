import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LanguageService } from './language.service';

const STORAGE_KEY = 'tm-language';

function createService(): LanguageService {
  TestBed.configureTestingModule({ imports: [TranslateModule.forRoot()] });
  return TestBed.inject(LanguageService);
}

describe('LanguageService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.lang = 'en';
  });

  afterEach(() => vi.restoreAllMocks());

  describe('init', () => {
    describe('when a language was chosen before', () => {
      it('should start in that language, so a reload does not fall back to English', () => {
        localStorage.setItem(STORAGE_KEY, 'de');

        const service = createService();
        service.init();

        expect(TestBed.inject(TranslateService).currentLang).toBe('de');
        expect(service.current()).toBe('de');
      });

      it('should mark the document as that language for screen readers', () => {
        localStorage.setItem(STORAGE_KEY, 'de');

        createService().init();

        expect(document.documentElement.lang).toBe('de');
      });
    });

    describe('when nothing was chosen yet', () => {
      it('should follow a supported browser language', () => {
        vi.spyOn(navigator, 'language', 'get').mockReturnValue('de-CH');

        const service = createService();
        service.init();

        expect(service.current()).toBe('de');
      });

      it('should fall back to English for a browser language that is not offered', () => {
        vi.spyOn(navigator, 'language', 'get').mockReturnValue('fr-FR');

        const service = createService();
        service.init();

        expect(service.current()).toBe('en');
      });
    });

    describe('when the stored value is not a language on offer', () => {
      it('should ignore it rather than ask for a catalogue that does not exist', () => {
        localStorage.setItem(STORAGE_KEY, 'xx');
        vi.spyOn(navigator, 'language', 'get').mockReturnValue('en-US');

        const service = createService();
        service.init();

        expect(service.current()).toBe('en');
      });
    });
  });

  describe('use', () => {
    it('should switch the translations, remember the choice and update the document language', () => {
      const service = createService();
      service.init();

      service.use('de');

      expect(TestBed.inject(TranslateService).currentLang).toBe('de');
      expect(localStorage.getItem(STORAGE_KEY)).toBe('de');
      expect(document.documentElement.lang).toBe('de');
    });

    it('should still switch when storage is unavailable', () => {
      vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
        throw new Error('storage blocked');
      });
      const service = createService();
      service.init();

      service.use('de');

      expect(service.current()).toBe('de');
    });
  });
});
