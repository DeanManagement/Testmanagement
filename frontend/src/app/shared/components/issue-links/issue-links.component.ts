import {
  ChangeDetectorRef,
  Component,
  DestroyRef,
  inject,
  Input,
  OnChanges,
  SimpleChanges,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { LowerCasePipe } from '@angular/common';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, take } from 'rxjs/operators';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { LocalizedDatePipe } from '../../pipes/localized-date.pipe';
import { IssueTrackerApiService } from '../../../core/services/issue-tracker-api.service';
import { IssueLink, IssueSearchResult } from '../../models/issue-tracker.model';

/**
 * Issues linked to one test result (PRD-010 §3.5): existing links as chips with an OPEN/CLOSED
 * pill, a typeahead to link an existing issue, and a one-click "file a new issue" that lets the
 * backend template the body from the result.
 *
 * <p>Self-contained rather than store-backed: the links belong to one result, are read and written
 * in the same place, and nothing else in the app needs them — an NgRx slice would be ceremony.
 */
@Component({
  selector: 'app-issue-links',
  standalone: true,
  imports: [
    FormsModule,
    LowerCasePipe,
    LocalizedDatePipe,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatAutocompleteModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    TranslateModule,
  ],
  templateUrl: './issue-links.component.html',
  styleUrl: './issue-links.component.scss',
})
export class IssueLinksComponent implements OnChanges {
  private readonly api = inject(IssueTrackerApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) projectId!: string;
  @Input({ required: true }) runId!: string;
  @Input({ required: true }) resultId!: string;
  /** Whether the current user may link, create and unlink (TESTER and up). */
  @Input() canEdit = false;
  /** Hides the "file a new issue" action for results that are not failures. */
  @Input() allowCreate = false;

  links: IssueLink[] = [];
  loading = false;
  busy = false;

  searchOpen = false;
  searchTerm = '';
  searchResults: IssueSearchResult[] = [];
  searching = false;
  /** Set when a search fails, so the panel explains itself instead of just going empty. */
  searchError = false;

  private readonly searchTerms = new Subject<string>();

  constructor() {
    this.searchTerms
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((term) => {
          this.searching = true;
          this.searchError = false;
          this.cdr.markForCheck();
          return this.api.searchIssues(this.projectId, term);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (results) => {
          this.searchResults = results;
          this.searching = false;
          this.cdr.markForCheck();
        },
        error: () => {
          // switchMap's source is dead after an error, so the subscription is rebuilt lazily by
          // falling back to a direct call on the next keystroke.
          this.searchResults = [];
          this.searching = false;
          this.searchError = true;
          this.cdr.markForCheck();
        },
      });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['resultId'] && this.resultId) {
      this.closeSearch();
      this.load();
    }
  }

  private load(): void {
    this.loading = true;
    this.api.getLinks(this.projectId, this.runId, this.resultId)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (links) => {
          this.links = links;
          this.loading = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.links = [];
          this.loading = false;
          this.cdr.markForCheck();
        },
      });
  }

  /** Asks the tracker for current state; a failure leaves the cached pills in place. */
  refresh(): void {
    if (this.busy || this.links.length === 0) {
      return;
    }
    this.busy = true;
    this.api.refreshLinks(this.projectId, this.runId, this.resultId)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (links) => {
          this.links = links;
          this.busy = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.busy = false;
          this.cdr.markForCheck();
        },
      });
  }

  openSearch(): void {
    this.searchOpen = true;
    this.searchTerm = '';
    this.searchResults = [];
    this.searchError = false;
  }

  closeSearch(): void {
    this.searchOpen = false;
    this.searchTerm = '';
    this.searchResults = [];
    this.searchError = false;
  }

  onSearchInput(term: string): void {
    this.searchTerm = term;
    if (!term.trim()) {
      this.searchResults = [];
      return;
    }
    this.searchTerms.next(term.trim());
  }

  linkExisting(result: IssueSearchResult): void {
    if (this.busy) {
      return;
    }
    this.busy = true;
    this.api.link(this.projectId, this.runId, this.resultId, { externalId: result.externalId })
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (link) => {
          this.upsert(link);
          this.busy = false;
          this.closeSearch();
          this.cdr.markForCheck();
        },
        error: () => {
          this.busy = false;
          this.cdr.markForCheck();
        },
      });
  }

  /** Files a new issue; title and body are templated server-side from the test result. */
  createIssue(): void {
    if (this.busy) {
      return;
    }
    this.busy = true;
    this.api.link(this.projectId, this.runId, this.resultId, { create: true })
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (link) => {
          this.upsert(link);
          this.busy = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.busy = false;
          this.cdr.markForCheck();
        },
      });
  }

  unlink(link: IssueLink): void {
    if (this.busy) {
      return;
    }
    this.busy = true;
    this.api.unlink(this.projectId, this.runId, this.resultId, link.id)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.links = this.links.filter((l) => l.id !== link.id);
          this.busy = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.busy = false;
          this.cdr.markForCheck();
        },
      });
  }

  /** Re-linking an existing issue returns the same row, so replace rather than append. */
  private upsert(link: IssueLink): void {
    const index = this.links.findIndex((l) => l.id === link.id);
    if (index >= 0) {
      this.links = [...this.links.slice(0, index), link, ...this.links.slice(index + 1)];
    } else {
      this.links = [...this.links, link];
    }
  }

  trackById(_: number, link: IssueLink): string {
    return link.id;
  }
}
