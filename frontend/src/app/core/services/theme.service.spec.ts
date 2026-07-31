import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ThemeService } from './theme.service';

/**
 * The service touches three pieces of browser state — localStorage, matchMedia and the document
 * root class — so each test stubs matchMedia and asserts on the resolved theme plus the class.
 */
describe('ThemeService', () => {

    let listeners: Array<() => void>;
    let systemPrefersDark: boolean;

    // jsdom does not implement matchMedia at all, so it is defined rather than spied on.
    function stubMatchMedia(): void {
        listeners = [];
        const query = {
            get matches() {
                return systemPrefersDark;
            },
            media: '(prefers-color-scheme: dark)',
            addEventListener: (_: string, handler: () => void) => listeners.push(handler),
            removeEventListener: () => undefined,
        } as unknown as MediaQueryList;
        vi.stubGlobal('matchMedia', () => query);
    }

    function service(): ThemeService {
        return TestBed.inject(ThemeService);
    }

    beforeEach(() => {
        localStorage.removeItem('tm-theme');
        document.documentElement.classList.remove('tm-dark');
        systemPrefersDark = false;
        stubMatchMedia();
        TestBed.configureTestingModule({ providers: [ThemeService] });
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
        localStorage.removeItem('tm-theme');
        document.documentElement.classList.remove('tm-dark');
    });

    it('defaults to system and follows the OS preference on first visit', () => {
        systemPrefersDark = true;
        const theme = service();
        theme.init();

        expect(theme.preference()).toBe('system');
        expect(theme.resolved()).toBe('dark');
        expect(document.documentElement.classList.contains('tm-dark')).toBe(true);
    });

    it('persists an explicit choice and applies it over the OS preference', () => {
        systemPrefersDark = true;
        const theme = service();
        theme.init();

        theme.setPreference('light');

        expect(theme.resolved()).toBe('light');
        expect(localStorage.getItem('tm-theme')).toBe('light');
        expect(document.documentElement.classList.contains('tm-dark')).toBe(false);
    });

    it('restores the stored preference on the next visit', () => {
        localStorage.setItem('tm-theme', 'dark');
        const theme = service();
        theme.init();

        expect(theme.preference()).toBe('dark');
        expect(theme.resolved()).toBe('dark');
    });

    it('tracks OS changes while on system, and stops once a choice is made', () => {
        const theme = service();
        theme.init();
        expect(theme.resolved()).toBe('light');

        systemPrefersDark = true;
        listeners.forEach(handler => handler());
        expect(theme.resolved()).toBe('dark');

        theme.setPreference('light');
        systemPrefersDark = false;
        listeners.forEach(handler => handler());
        expect(theme.resolved()).toBe('light');

        systemPrefersDark = true;
        listeners.forEach(handler => handler());
        expect(theme.resolved()).toBe('light');
    });

    it('emits only when the effective theme actually changes', () => {
        const theme = service();
        theme.init();

        const seen: string[] = [];
        theme.resolvedChanges.subscribe(value => seen.push(value));

        theme.setPreference('light');
        theme.setPreference('dark');
        theme.setPreference('dark');

        expect(seen).toEqual(['dark']);
    });

    it('falls back to system when localStorage is unavailable', () => {
        vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
            throw new Error('denied');
        });
        vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
            throw new Error('denied');
        });
        systemPrefersDark = true;

        const theme = service();
        theme.init();
        expect(theme.preference()).toBe('system');
        expect(theme.resolved()).toBe('dark');

        expect(() => theme.setPreference('light')).not.toThrow();
        expect(theme.resolved()).toBe('light');
    });
});
