import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideStore } from '@ngrx/store';
import { TranslateModule } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ChangePasswordComponent } from './change-password.component';
import { authReducer } from '../../store/auth/auth.reducer';

/**
 * A rejected password change has to say so. The failure state is written from the HTTP error
 * callback, and under zoneless change detection a plain field written there notifies nothing — the
 * message never appears and the button stays disabled mid-submit, so the form looks simply dead.
 *
 * This screen is also the forced first-login step, which makes a silent failure a lockout.
 */
describe('ChangePasswordComponent', () => {
  let fixture: ComponentFixture<ChangePasswordComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ChangePasswordComponent, TranslateModule.forRoot()],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideNoopAnimations(),
        provideStore({ auth: authReducer }),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    fixture = TestBed.createComponent(ChangePasswordComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  async function submitWith(currentPassword: string) {
    const component = fixture.componentInstance;
    component.currentPassword = currentPassword;
    component.newPassword = 'a-long-enough-password';
    component.confirmPassword = 'a-long-enough-password';
    component.onSubmit();
    await fixture.whenStable();
  }

  it('renders the server error after a rejected change', async () => {
    await submitWith('wrong-current-password');

    http.expectOne('/api/auth/change-password')
        .flush({ message: 'Current password is incorrect' }, { status: 400, statusText: 'Bad Request' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.error-message')?.textContent)
        .toContain('Current password is incorrect');
    // The submit button must come back out of its loading state, or the form is stuck.
    expect(fixture.nativeElement.querySelector('button[type="submit"]')?.disabled).toBe(false);
  });

  it('falls back to a generic message when the server sends no body', async () => {
    await submitWith('wrong');

    http.expectOne('/api/auth/change-password')
        .flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.error-message')?.textContent)
        .toContain('Failed to change password');
  });
});
