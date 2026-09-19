import { Component, computed, DestroyRef, inject, input, model, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Store } from '@ngrx/store';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import { catchError, debounceTime, filter, map, switchMap } from 'rxjs/operators';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { TestCaseFolderActions } from '../../../store/test-case-folder/test-case-folder.actions';
import { selectFolderTree } from '../../../store/test-case-folder/test-case-folder.selectors';
import { TestCaseFolder } from '../../models/test-case-folder.model';
import { TestCase } from '../../models/test-case.model';
import { TestCaseSummary } from '../../models/test-suite.model';

/** The server's max-page-size: a project with more matches is told to narrow the filters. */
const PAGE_SIZE = 200;
const SEARCH_DEBOUNCE_MS = 300;

interface PickerFilters {
  projectId: string;
  q: string;
  folderId: string | null;
  labels: string[];
}

interface PickerResults {
  cases: TestCase[];
  total: number;
}

/**
 * Picks test cases for a run or a suite (PRD-052): search, folder and label filters run on the
 * server, so a project of any size can be searched. The selection is two-way bound
 * ({@code [(selected)]}); a selected case the filters hide stays listed under "Selected", so
 * filtering never silently drops it.
 *
 * <p>It calls the API directly rather than the test case store, which belongs to the case list page.
 */
@Component({
  selector: 'app-test-case-picker',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    TranslateModule,
  ],
  templateUrl: './test-case-picker.component.html',
  styleUrl: './test-case-picker.component.scss',
})
export class TestCasePickerComponent {
  private readonly api = inject(TestCaseApiService);
  private readonly store = inject(Store);
  private readonly destroyRef = inject(DestroyRef);

  readonly projectId = input.required<string>();
  readonly selected = model<TestCaseSummary[]>([]);

  readonly search = signal('');
  readonly folderId = signal<string | null>(null);
  readonly labels = signal<string[]>([]);
  readonly availableLabels = signal<string[]>([]);
  readonly results = signal<PickerResults | null>(null);

  readonly folders = toSignal(this.store.select(selectFolderTree), { initialValue: [] as TestCaseFolder[] });
  readonly flatFolders = computed(() => flattenFolders(this.folders()));
  readonly selectedIds = computed(() => new Set(this.selected().map((tc) => tc.id)));
  readonly filtersActive = computed(() => !!(this.search().trim() || this.folderId() || this.labels().length));
  /** Selected cases the current filters hide. */
  readonly hiddenSelected = computed(() => {
    const shown = new Set((this.results()?.cases ?? []).map((tc) => tc.id));
    return this.selected().filter((tc) => !shown.has(tc.id));
  });

  private readonly filters = computed<PickerFilters>(() => ({
    projectId: this.projectId(),
    q: this.search().trim(),
    folderId: this.folderId(),
    labels: this.labels(),
  }));

  constructor() {
    toObservable(this.projectId).pipe(filter(Boolean), takeUntilDestroyed()).subscribe((projectId) => {
      this.store.dispatch(TestCaseFolderActions.loadFolders({ projectId }));
      this.api.getLabels(projectId).pipe(catchError(() => of([] as string[])))
        .subscribe((labels) => this.availableLabels.set(labels));
    });
    toObservable(this.filters).pipe(
      debounceTime(SEARCH_DEBOUNCE_MS),
      switchMap((filters) => this.load(filters)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((results) => this.results.set(results));
  }

  private load(filters: PickerFilters): Observable<PickerResults> {
    return this.api.getAll(filters.projectId, {
      q: filters.q || undefined,
      folderId: filters.folderId,
      includeSubfolders: true,
      label: filters.labels.length ? filters.labels : undefined,
      size: PAGE_SIZE,
      sort: 'title,asc',
    }).pipe(
      map((page): PickerResults => ({ cases: page.content, total: page.page.totalElements })),
      catchError(() => of<PickerResults>({ cases: [], total: 0 })),
    );
  }

  isSelected(id: string): boolean {
    return this.selectedIds().has(id);
  }

  toggle(testCase: TestCaseSummary): void {
    if (this.isSelected(testCase.id)) {
      this.selected.update((list) => list.filter((tc) => tc.id !== testCase.id));
    } else {
      this.selected.update((list) => [...list, summaryOf(testCase)]);
    }
  }

  selectAllVisible(): void {
    const ids = this.selectedIds();
    const added = (this.results()?.cases ?? []).filter((tc) => !ids.has(tc.id)).map(summaryOf);
    if (added.length) {
      this.selected.update((list) => [...list, ...added]);
    }
  }

  clearSelection(): void {
    this.selected.set([]);
  }

  resetFilters(): void {
    this.search.set('');
    this.folderId.set(null);
    this.labels.set([]);
  }
}

function summaryOf(testCase: TestCaseSummary): TestCaseSummary {
  return { id: testCase.id, key: testCase.key, title: testCase.title };
}

/** Folders depth first, each with its depth for indentation in the select. */
function flattenFolders(folders: TestCaseFolder[], depth = 0): (TestCaseFolder & { depth: number })[] {
  return folders.flatMap((folder) => [
    { ...folder, depth },
    ...flattenFolders(folder.children ?? [], depth + 1),
  ]);
}
