import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { catchError, of, switchMap, take } from 'rxjs';
import { Manual, ManualChapter, MyCapabilities, chaptersFor } from './manual.model';

/**
 * The user manual, filtered to what the reader can actually do.
 *
 * <p>A viewer is shown no instructions for writing test cases, and someone who is not an instance
 * admin sees no chapter on SSO or API keys. That is a readability decision rather than a security
 * one — every action is authorized server-side regardless — and it exists because a manual that
 * describes buttons you do not have is worse than a shorter one.
 *
 * <p>Content is fetched from `assets/manual/{lang}.json` on first visit rather than living in the
 * translation bundle, which is loaded on every page by everyone.
 */
@Component({
  selector: 'app-help',
  standalone: true,
  imports: [MatIconModule, MatProgressSpinnerModule, TranslateModule],
  templateUrl: './help.component.html',
  styleUrl: './help.component.scss',
})
export class HelpComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly manualTitle = signal('');
  readonly chapters = signal<ManualChapter[]>([]);
  readonly capabilities = signal<MyCapabilities | null>(null);

  ngOnInit(): void {
    this.load();
    // The manual is written per language, not translated key by key, so a language switch has to
    // re-fetch rather than re-render.
    this.translate.onLangChange
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load());
  }

  private load(): void {
    this.loading.set(true);
    this.failed.set(false);

    this.http
      .get<MyCapabilities>('/api/me/capabilities')
      .pipe(
        // Without capabilities the safe fallback is the shared chapters only: showing a viewer
        // instructions they cannot follow is worse than showing them less.
        catchError(() => of<MyCapabilities>({ systemAdmin: false, projectRoles: [], projectMemberships: 0 })),
        switchMap((capabilities) => {
          this.capabilities.set(capabilities);
          return this.http.get<Manual>(`assets/manual/${this.language()}.json`).pipe(
            catchError(() => {
              this.failed.set(true);
              return of<Manual>({ title: '', chapters: [] });
            })
          );
        }),
        take(1),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((manual) => {
        this.manualTitle.set(manual.title);
        this.chapters.set(chaptersFor(manual, this.capabilities()));
        this.loading.set(false);
      });
  }

  /** Falls back to English for any language the manual has not been written in. */
  private language(): string {
    const current = (this.translate.currentLang || this.translate.getDefaultLang() || 'en').slice(0, 2);
    return current === 'de' ? 'de' : 'en';
  }

  /** Describes the reader's own access, so it is clear why the manual is the length it is. */
  roleSummaryKey(): string {
    const caps = this.capabilities();
    if (caps?.systemAdmin) {
      return 'help.roles.systemAdmin';
    }
    switch (caps?.highestRole) {
      case 'ADMIN':
        return 'help.roles.projectAdmin';
      case 'TESTER':
        return 'help.roles.tester';
      case 'VIEWER':
        return 'help.roles.viewer';
      default:
        return 'help.roles.none';
    }
  }

  scrollTo(id: string): void {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
}
