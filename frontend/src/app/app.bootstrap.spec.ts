import { ApplicationRef } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { afterEach, describe, expect, it } from 'vitest';
import { AppComponent } from './app.component';
import { appConfig } from './app.config';

/**
 * Regression guard for NG0200 ("Circular dependency detected for _AuthService").
 *
 * TranslateService loads its fallback language over HttpClient the moment it is constructed, and
 * the HTTP interceptors inject AuthService. So if AuthService injects TranslateService as a field,
 * constructing AuthService kicks off a request whose interceptor chain injects the very service
 * still being built, and bootstrap dies.
 *
 * This only shows up through a real bootstrap — a TestBed that injects the services directly does
 * not reproduce it — so the assertion is deliberately made against bootstrapApplication.
 */
describe('application bootstrap', () => {
  let appRef: ApplicationRef | null = null;

  afterEach(() => {
    appRef?.destroy();
    appRef = null;
    document.body.innerHTML = '';
    localStorage.removeItem('auth_token');
  });

  it('boots without a circular dependency', async () => {
    document.body.innerHTML = '<app-root></app-root>';

    await expect(
      bootstrapApplication(AppComponent, appConfig).then((ref) => (appRef = ref))
    ).resolves.toBeDefined();
  });
});
