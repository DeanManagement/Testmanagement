import { Injectable, inject, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

export const SUPPORTED_LANGUAGES = ['en', 'de'] as const;
export type Language = (typeof SUPPORTED_LANGUAGES)[number];

const STORAGE_KEY = 'tm-language';
const FALLBACK: Language = 'en';

/**
 * The UI language: which one is active, remembering it, and telling the document.
 *
 * Switching used to be a bare `translate.use()` in two components, with `en` hardcoded at
 * bootstrap. Nothing was stored, so every reload, bookmark or new tab came back in English, and
 * `<html lang>` stayed `en` while the page was German — a screen reader then reads German text
 * with English pronunciation (WCAG 3.1.1). Like the theme, the choice is local to the browser;
 * there is no per-user preference on the server.
 */
@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly translate = inject(TranslateService);
  private readonly currentSignal = signal<Language>(FALLBACK);

  /** The language in effect. */
  readonly current = this.currentSignal.asReadonly();

  /** Applies the remembered language, else the browser's if offered, else English. Call once at bootstrap. */
  init(): void {
    this.translate.addLangs([...SUPPORTED_LANGUAGES]);
    this.apply(this.stored() ?? this.fromBrowser() ?? FALLBACK);
  }

  use(language: Language): void {
    this.apply(language);
    try {
      localStorage.setItem(STORAGE_KEY, language);
    } catch {
      // Private mode or blocked storage: the switch still works for this page view.
    }
  }

  private apply(language: Language): void {
    this.currentSignal.set(language);
    this.translate.use(language);
    document.documentElement.lang = language;
  }

  private stored(): Language | null {
    try {
      return asSupported(localStorage.getItem(STORAGE_KEY));
    } catch {
      return null;
    }
  }

  private fromBrowser(): Language | null {
    return asSupported(navigator.language?.slice(0, 2).toLowerCase() ?? null);
  }
}

function asSupported(value: string | null): Language | null {
  return SUPPORTED_LANGUAGES.find((language) => language === value) ?? null;
}
