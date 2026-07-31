import { Injectable, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';

export type ThemePreference = 'light' | 'dark' | 'system';
export type ResolvedTheme = 'light' | 'dark';

const STORAGE_KEY = 'tm-theme';
const DARK_CLASS = 'tm-dark';

/**
 * Light/dark theming. The stored preference is one of light/dark/system; `system` follows the OS
 * and keeps following it while the window is open. Everything is local to the browser — there is
 * no server-side per-user preference (PRD-013).
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {

    private readonly preferenceSignal = signal<ThemePreference>('system');
    private readonly resolvedSignal = signal<ResolvedTheme>('light');
    private readonly changes = new Subject<ResolvedTheme>();

    /** The user's stored choice, including `system`. */
    readonly preference = this.preferenceSignal.asReadonly();

    /** The theme actually in effect, with `system` already resolved. */
    readonly resolved = this.resolvedSignal.asReadonly();

    /** Emits whenever the effective theme changes. Charts subscribe to re-render. */
    readonly resolvedChanges: Observable<ResolvedTheme> = this.changes.asObservable();

    private mediaQuery: MediaQueryList | null = null;

    /**
     * Applies the stored preference. Called once during app bootstrap, before the first screen
     * renders, so there is no flash of the wrong theme.
     */
    init(): void {
        this.mediaQuery = this.systemQuery();
        this.mediaQuery?.addEventListener('change', () => {
            // Only the `system` preference tracks the OS; an explicit choice wins.
            if (this.preferenceSignal() === 'system') {
                this.apply();
            }
        });
        this.preferenceSignal.set(this.read());
        this.apply();
    }

    setPreference(preference: ThemePreference): void {
        this.preferenceSignal.set(preference);
        this.write(preference);
        this.apply();
    }

    private apply(): void {
        const resolved: ResolvedTheme = this.preferenceSignal() === 'system'
            ? (this.mediaQuery?.matches ? 'dark' : 'light')
            : this.preferenceSignal() as ResolvedTheme;

        const root = document.documentElement;
        root.classList.toggle(DARK_CLASS, resolved === 'dark');
        // Themes native form controls, scrollbars and the canvas backdrop.
        root.style.colorScheme = resolved;

        if (this.resolvedSignal() !== resolved) {
            this.resolvedSignal.set(resolved);
            this.changes.next(resolved);
        }
    }

    /** Private browsing can throw on both read and write, which must not break the app. */
    private read(): ThemePreference {
        try {
            const stored = localStorage.getItem(STORAGE_KEY);
            return stored === 'light' || stored === 'dark' || stored === 'system' ? stored : 'system';
        } catch {
            return 'system';
        }
    }

    private write(preference: ThemePreference): void {
        try {
            localStorage.setItem(STORAGE_KEY, preference);
        } catch {
            // Preference is not persisted; the session still honours the choice.
        }
    }

    private systemQuery(): MediaQueryList | null {
        return typeof window !== 'undefined' && typeof window.matchMedia === 'function'
            ? window.matchMedia('(prefers-color-scheme: dark)')
            : null;
    }
}
