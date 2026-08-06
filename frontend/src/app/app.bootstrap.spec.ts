import { ApplicationRef } from '@angular/core';
import { HttpBackend, HttpResponse } from '@angular/common/http';
import { bootstrapApplication } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import { afterEach, describe, expect, it } from 'vitest';
import { AppComponent } from './app.component';
import { appConfig } from './app.config';

/**
 * Two bootstrap-level guards, both for the same underlying trap: TranslateService loads its
 * fallback language over HttpClient the moment it is constructed, and the HTTP interceptors reach
 * back into services that (directly or transitively) construct TranslateService.
 *
 * Neither failure reproduces in a TestBed that injects the services directly — the first only
 * happens through a real bootstrap, and the second is swallowed by ngx-translate — so both are
 * asserted against bootstrapApplication.
 */
describe('application bootstrap', () => {
  let appRef: ApplicationRef | null = null;

  afterEach(() => {
    appRef?.destroy();
    appRef = null;
    document.body.innerHTML = '';
    localStorage.removeItem('auth_token');
  });

  /** Serves every request from memory so the translation load can actually complete in jsdom. */
  const stubBackend = (seen: string[]): HttpBackend => ({
    handle: (req): Observable<HttpResponse<unknown>> => {
      seen.push(req.url);
      return of(new HttpResponse({ status: 200, url: req.url, body: EN_FIXTURE }));
    },
  });

  const EN_FIXTURE = { landing: { hero: { title: 'Test Management Made Simple' } } };

  async function boot(extraProviders: unknown[] = []) {
    document.body.innerHTML = '<app-root></app-root>';
    appRef = await bootstrapApplication(AppComponent, {
      ...appConfig,
      providers: [...appConfig.providers, ...(extraProviders as never[])],
    });
    return appRef;
  }

  it('boots without a circular dependency', async () => {
    await expect(boot()).resolves.toBeDefined();
  });

  it('resolves translations — the interceptors must not construct TranslateService', async () => {
    // If an interceptor injects TranslateService (or a service that does) while the chain is being
    // built, the fallback-language request is the request that builds the chain. Angular throws
    // NG0200, ngx-translate swallows it, and the app boots with every key unresolved — no error
    // anywhere, just raw "landing.hero.title" on screen.
    const seen: string[] = [];
    const ref = await boot([{ provide: HttpBackend, useValue: stubBackend(seen) }]);

    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(seen).toContain('/assets/i18n/en.json');
    expect(ref.injector.get(TranslateService).instant('landing.hero.title'))
      .toBe('Test Management Made Simple');
  });
});
