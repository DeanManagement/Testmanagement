import { CommonModule } from '@angular/common';
import { Component, DestroyRef, ElementRef, HostListener, OnInit, ViewChild, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { TranslateModule } from '@ngx-translate/core';
import { combineLatest, map, Observable, Subject } from 'rxjs';
import { debounceTime, take } from 'rxjs/operators';
import { selectAllProjects } from '../../../store/project/project.selectors';
import { selectAllTestCases, selectTestCaseProjectId } from '../../../store/test-case/test-case.selectors';
import { selectAllTestRuns, selectTestRunProjectId } from '../../../store/test-run/test-run.selectors';
import { selectAllBugReports } from '../../../store/bug-report/bug-report.selectors';
import { SearchApiService, SearchHit } from '../../../core/services/search-api.service';

/**
 * Globally available command palette opened with Cmd/Ctrl-K. Provides
 * fuzzy text search over data that the user has already touched during the
 * session — projects (always loaded), plus test cases / test runs / bug
 * reports from the currently-scoped project's NgRx slices.
 *
 * <p>This is deliberately a client-side index. A server-side global search
 * lives in the v2.0 roadmap (REQUIREMENTS.md §13.8). Doing this client-side
 * first ships the daily-use win without adding new infrastructure or hot
 * network paths.
 */
type ItemType = 'project' | 'testCase' | 'testRun' | 'bugReport';

interface CommandItem {
  type: ItemType;
  /** Display key (`PROJ-1`, project key, etc.). May be empty for bug reports. */
  key: string;
  /** Human-readable title. */
  title: string;
  /** Router link to navigate to on Enter. */
  link: any[];
  /** Optional subtitle — e.g. the project name for nested entities. */
  subtitle?: string;
  /** Material icon name. */
  icon: string;
}

@Component({
  selector: 'app-command-palette',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    TranslateModule,
  ],
  templateUrl: './command-palette.component.html',
  styleUrl: './command-palette.component.scss',
})
export class CommandPaletteComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly router = inject(Router);
  private readonly dialogRef = inject(MatDialogRef<CommandPaletteComponent>);
  private readonly searchApi = inject(SearchApiService);
  private readonly destroyRef = inject(DestroyRef);

  /** Threshold below which the local fuzzy match is topped up with server results. */
  private static readonly SERVER_FALLBACK_THRESHOLD = 5;
  private readonly serverQuery$ = new Subject<string>();

  @ViewChild('searchInput') searchInput?: ElementRef<HTMLInputElement>;

  query = '';
  /**
   * Signals rather than plain fields: the server fallback appends results from
   * an async subscribe, which under zoneless change detection would otherwise
   * never trigger a re-render (the original plain-array version silently
   * dropped every server hit).
   */
  readonly results = signal<CommandItem[]>([]);
  readonly serverPending = signal(false);
  readonly serverError = signal(false);
  selectedIndex = 0;

  /**
   * Cap on results per type. The palette is a discoverability tool, not a
   * full search — beyond the cap users should narrow the query.
   */
  private static readonly PER_TYPE_LIMIT = 6;

  /** Stream of every indexable item, regardless of query. Recomputed when stores change. */
  private readonly allItems$: Observable<CommandItem[]> = combineLatest([
    this.store.select(selectAllProjects),
    this.store.select(selectAllTestCases),
    this.store.select(selectTestCaseProjectId),
    this.store.select(selectAllTestRuns),
    this.store.select(selectTestRunProjectId),
    this.store.select(selectAllBugReports),
  ]).pipe(
    map(([projects, testCases, tcProjectId, testRuns, runProjectId, bugReports]) => {
      const items: CommandItem[] = [];

      for (const p of projects) {
        items.push({
          type: 'project',
          key: p.key,
          title: p.name,
          link: ['/projects', p.id],
          icon: 'folder',
        });
      }

      const projectKeyById = new Map(projects.map((p) => [p.id, p.key] as const));
      const projectNameById = new Map(projects.map((p) => [p.id, p.name] as const));

      if (tcProjectId) {
        for (const tc of testCases) {
          items.push({
            type: 'testCase',
            key: tc.key,
            title: tc.title,
            link: ['/projects', tcProjectId, 'test-cases', tc.id],
            subtitle: projectNameById.get(tcProjectId),
            icon: 'description',
          });
        }
      }
      if (runProjectId) {
        for (const r of testRuns) {
          items.push({
            type: 'testRun',
            key: r.key,
            title: r.name,
            link: ['/projects', runProjectId, 'test-runs', r.id],
            subtitle: projectNameById.get(runProjectId),
            icon: 'play_circle',
          });
        }
      }
      for (const b of bugReports) {
        items.push({
          type: 'bugReport',
          key: projectKeyById.get(b.projectId) ?? '',
          title: b.title,
          link: ['/projects', b.projectId, 'bug-reports', b.id],
          subtitle: projectNameById.get(b.projectId),
          icon: 'bug_report',
        });
      }

      return items;
    }),
  );

  private cachedAll: CommandItem[] = [];

  ngOnInit(): void {
    // The palette is recreated per open; drop the cached index on destroy so
    // stale (possibly logged-out) data never outlives the dialog.
    this.destroyRef.onDestroy(() => (this.cachedAll = []));
    this.allItems$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((items) => {
      this.cachedAll = items;
      this.applyQuery();
    });
    // Debounced server-side search tops up sparse local results (PRD-007).
    this.serverQuery$
      .pipe(debounceTime(250), takeUntilDestroyed(this.destroyRef))
      .subscribe((q) => this.serverFallback(q));
    // Defer focus until the dialog finishes opening — the input doesn't
    // exist on the very first tick.
    setTimeout(() => this.searchInput?.nativeElement.focus(), 0);
  }

  onQueryChange(): void {
    this.selectedIndex = 0;
    this.serverError.set(false);
    this.applyQuery();
    const q = this.query.trim();
    if (q.length >= 2 && this.results().length < CommandPaletteComponent.SERVER_FALLBACK_THRESHOLD) {
      this.serverPending.set(true);
      this.serverQuery$.next(q);
    } else {
      this.serverPending.set(false);
    }
  }

  /** Fetch server results and append any not already shown locally. */
  private serverFallback(q: string): void {
    if (q !== this.query.trim() || q.length < 2) {
      this.serverPending.set(false);
      return;
    }
    this.searchApi.search(q).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (response) => {
        // Ignore stale responses for an outdated query.
        if (q !== this.query.trim()) {
          return;
        }
        this.serverPending.set(false);
        const serverItems = [
          ...response.projects,
          ...response.testCases,
          ...response.testRuns,
          ...response.bugReports,
        ].map((hit) => this.toCommandItem(hit));

        const current = this.results();
        const seen = new Set(current.map((r) => r.link.join('/')));
        const appended = [...current];
        for (const item of serverItems) {
          const id = item.link.join('/');
          if (!seen.has(id)) {
            appended.push(item);
            seen.add(id);
          }
        }
        this.results.set(appended);
      },
      error: () => {
        if (q === this.query.trim()) {
          this.serverPending.set(false);
          this.serverError.set(true);
        }
      },
    });
  }

  private toCommandItem(hit: SearchHit): CommandItem {
    const map: Record<SearchHit['type'], { segment: string[]; icon: string }> = {
      project: { segment: ['/projects', hit.id], icon: 'folder' },
      testCase: { segment: ['/projects', hit.projectId, 'test-cases', hit.id], icon: 'description' },
      testRun: { segment: ['/projects', hit.projectId, 'test-runs', hit.id], icon: 'play_circle' },
      bugReport: { segment: ['/projects', hit.projectId, 'bug-reports', hit.id], icon: 'bug_report' },
    };
    const meta = map[hit.type];
    return {
      type: hit.type,
      key: hit.key ?? '',
      title: hit.title,
      link: meta.segment,
      subtitle: hit.snippet ?? undefined,
      icon: meta.icon,
    };
  }

  private applyQuery(): void {
    const q = this.query.trim().toLowerCase();
    if (!q) {
      this.results.set(this.cachedAll.slice(0, CommandPaletteComponent.PER_TYPE_LIMIT * 4));
      return;
    }
    const tokens = q.split(/\s+/);

    const byType: Record<ItemType, CommandItem[]> = {
      project: [],
      testCase: [],
      testRun: [],
      bugReport: [],
    };
    for (const item of this.cachedAll) {
      const haystack = `${item.key} ${item.title}`.toLowerCase();
      if (tokens.every((t) => haystack.includes(t))) {
        byType[item.type].push(item);
      }
    }
    this.results.set([
      ...byType.project.slice(0, CommandPaletteComponent.PER_TYPE_LIMIT),
      ...byType.testCase.slice(0, CommandPaletteComponent.PER_TYPE_LIMIT),
      ...byType.testRun.slice(0, CommandPaletteComponent.PER_TYPE_LIMIT),
      ...byType.bugReport.slice(0, CommandPaletteComponent.PER_TYPE_LIMIT),
    ]);
  }

  @HostListener('keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        if (this.results().length > 0) {
          this.selectedIndex = (this.selectedIndex + 1) % this.results().length;
        }
        break;
      case 'ArrowUp':
        event.preventDefault();
        if (this.results().length > 0) {
          this.selectedIndex = (this.selectedIndex - 1 + this.results().length) % this.results().length;
        }
        break;
      case 'Enter':
        event.preventDefault();
        this.selectCurrent();
        break;
      case 'Escape':
        event.preventDefault();
        this.dialogRef.close();
        break;
    }
  }

  selectItem(index: number): void {
    this.selectedIndex = index;
    this.selectCurrent();
  }

  private selectCurrent(): void {
    const item = this.results()[this.selectedIndex];
    if (item) {
      this.router.navigate(item.link);
      this.dialogRef.close();
    }
  }
}
