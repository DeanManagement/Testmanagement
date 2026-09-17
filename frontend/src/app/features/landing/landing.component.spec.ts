import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { TranslateModule } from '@ngx-translate/core';
import { describe, expect, it } from 'vitest';
import { selectIsAuthenticated } from '../../store/auth/auth.selectors';
import { LandingComponent } from './landing.component';

function render(isAuthenticated: boolean): HTMLElement {
  TestBed.configureTestingModule({
    imports: [LandingComponent, TranslateModule.forRoot()],
    providers: [provideRouter([]), provideNoopAnimations(), provideMockStore()],
  });
  TestBed.inject(MockStore).overrideSelector(selectIsAuthenticated, isAuthenticated);
  const fixture = TestBed.createComponent(LandingComponent);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

const href = (element: HTMLElement, testId: string) =>
  element.querySelector(`[data-test-id="${testId}"]`)?.getAttribute('href') ?? null;

describe('LandingComponent', () => {
  describe('when the visitor is not signed in', () => {
    it('should offer to sign in, from the header and from the hero', () => {
      const page = render(false);

      expect(href(page, 'landing-login-btn')).toBe('/login');
      expect(href(page, 'landing-get-started')).toBe('/login');
      expect(page.querySelector('[data-test-id="landing-dashboard-btn"]')).toBeNull();
    });
  });

  describe('when the visitor already has a session', () => {
    it('should offer the dashboard instead of "Sign In"', () => {
      const page = render(true);

      expect(page.querySelector('[data-test-id="landing-login-btn"]')).toBeNull();
      expect(href(page, 'landing-dashboard-btn')).toBe('/dashboard');
      expect(href(page, 'landing-get-started')).toBe('/dashboard');
    });
  });
});
