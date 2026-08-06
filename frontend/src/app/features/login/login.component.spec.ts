import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideStore } from '@ngrx/store';
import { TranslateModule } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { LoginComponent } from './login.component';
import { AuthConfig } from '../../shared/models/sso.model';
import { authReducer } from '../../store/auth/auth.reducer';

/**
 * The sign-in screen asks the server which methods are available and starts optimistic: the
 * password form shows until told otherwise. That answer arrives on an HTTP callback, so under
 * zoneless change detection a plain field would leave the SSO buttons unrendered until some
 * unrelated interaction ticked change detection — an SSO-only installation would look like it had
 * no way in at all.
 */
describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LoginComponent, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        provideStore({ auth: authReducer }),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    fixture = TestBed.createComponent(LoginComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  async function respondWith(config: AuthConfig) {
    http.expectOne('/api/auth/config').flush(config);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('renders an SSO button once the config arrives', async () => {
    await respondWith({
      localLoginEnabled: true,
      providers: [{ slug: 'okta', displayName: 'Okta' }],
    });

    // Asserting on the rendered button rather than its label: no translations are loaded here,
    // so the label renders as its i18n key.
    expect(fixture.nativeElement.querySelector('[data-test-id="login-sso-okta"]')).not.toBeNull();
  });

  it('hides the password form when the server reports SSO only', async () => {
    await respondWith({
      localLoginEnabled: false,
      providers: [{ slug: 'okta', displayName: 'Okta' }],
    });

    expect(fixture.nativeElement.querySelector('input[type="password"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-test-id="login-sso-buttons"]')).not.toBeNull();
  });

  it('keeps the password form when the config call fails', async () => {
    // Optimistic by design: a broken config endpoint must not lock everyone out.
    http.expectOne('/api/auth/config').flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('input[type="password"]')).not.toBeNull();
  });
});
