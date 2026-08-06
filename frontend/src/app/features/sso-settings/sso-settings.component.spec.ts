import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { SsoSettingsComponent } from './sso-settings.component';

/**
 * The protocol choice is not cosmetic: it changes which fields mean anything and which scopes are
 * requested. The backend enforces all of it, so what is worth pinning here is that the form does
 * not send GitHub a configuration the server will reject, and does not quietly keep OIDC-only
 * values a user typed before switching.
 */
describe('SsoSettingsComponent', () => {
  let fixture: ComponentFixture<SsoSettingsComponent>;
  let component: SsoSettingsComponent;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [SsoSettingsComponent, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    fixture = TestBed.createComponent(SsoSettingsComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    http.expectOne('/api/admin/sso/providers').flush([]);
    http.expectOne('/api/admin/sso/settings').flush({ localLoginEnabled: true });
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  it('swaps the prefilled scopes when the protocol changes', () => {
    component.openCreate();
    expect(component.formScopes).toBe('openid,profile,email');

    component.onProtocolChange('GITHUB');

    // `openid` means nothing to GitHub, and without user:email the /user/emails call is refused,
    // which leaves the login with no address to provision an account from.
    expect(component.formScopes).toBe('read:user,user:email');
  });

  it('drops an admin claim typed before switching to GitHub', () => {
    component.openCreate();
    component.formAdminClaim = 'groups';
    component.formAdminClaimValue = 'admins';

    component.onProtocolChange('GITHUB');

    expect(component.formAdminClaim).toBe('');
    expect(component.formAdminClaimValue).toBe('');
  });

  it('never sends an admin claim for a GitHub provider', async () => {
    component.openCreate();
    component.onProtocolChange('GITHUB');
    component.formSlug = 'gh';
    component.formDisplayName = 'GitHub';
    component.formIssuerUri = 'https://github.com';
    component.formClientId = 'client';
    component.formClientSecret = 'secret';
    // Set directly, as a stale value bound to a hidden field could be. The backend rejects an
    // admin claim on GitHub outright, so sending one turns a valid form into a 400.
    component.formAdminClaim = 'groups';

    component.save();

    const request = http.expectOne('/api/admin/sso/providers');
    expect(request.request.body.protocol).toBe('GITHUB');
    expect(request.request.body.adminClaim).toBeUndefined();
    request.flush({});

    http.expectOne('/api/admin/sso/providers').flush([]);
    http.expectOne('/api/admin/sso/settings').flush({ localLoginEnabled: true });
    await fixture.whenStable();
  });

  it('sends OIDC by default so an unchanged form behaves as before', () => {
    component.openCreate();
    component.formSlug = 'acme';
    component.formDisplayName = 'Acme';
    component.formIssuerUri = 'https://idp.example.com';
    component.formClientId = 'client';
    component.formClientSecret = 'secret';

    component.save();

    const request = http.expectOne('/api/admin/sso/providers');
    expect(request.request.body.protocol).toBe('OIDC');
    request.flush({});

    http.expectOne('/api/admin/sso/providers').flush([]);
    http.expectOne('/api/admin/sso/settings').flush({ localLoginEnabled: true });
  });
});
