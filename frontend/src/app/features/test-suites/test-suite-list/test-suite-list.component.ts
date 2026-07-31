import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AsyncPipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { Subject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { TranslateModule } from '@ngx-translate/core';
import { TestSuiteActions } from '../../../store/test-suite/test-suite.actions';
import { selectAllTestSuites, selectTestSuitesLoading, selectTestSuitesError, selectTestSuitePage } from '../../../store/test-suite/test-suite.selectors';
import { TestSuiteQuery } from '../../../shared/models/test-suite.model';

@Component({
  selector: 'app-test-suite-list',
  standalone: true,
  imports: [
    AsyncPipe,
    RouterLink,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatPaginatorModule,
    MatSortModule,
    TranslateModule,
  ],
  templateUrl: './test-suite-list.component.html',
  styleUrl: './test-suite-list.component.scss',
})
export class TestSuiteListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  projectId = '';
  testSuites$ = this.store.select(selectAllTestSuites);
  loading$ = this.store.select(selectTestSuitesLoading);
  error$ = this.store.select(selectTestSuitesError);
  page$ = this.store.select(selectTestSuitePage);
  displayedColumns = ['name', 'description', 'testCaseCount', 'actions'];
  searchTerm = '';
  sortActive = 'updatedAt';
  sortDirection: 'asc' | 'desc' = 'desc';

  private currentQuery: TestSuiteQuery = {};
  private readonly searchChange$ = new Subject<void>();

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    if (!this.projectId) {
      return;
    }

    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.searchTerm = params.get('q') ?? '';
      this.sortActive = params.get('sort')?.split(',')[0] ?? 'updatedAt';
      this.sortDirection = (params.get('sort')?.split(',')[1] as 'asc' | 'desc') ?? 'desc';

      this.currentQuery = {
        q: this.searchTerm || undefined,
        page: params.get('page') ? Number(params.get('page')) : 0,
        size: params.get('size') ? Number(params.get('size')) : 50,
        sort: params.get('sort') ?? 'updatedAt,desc',
      };
      this.store.dispatch(TestSuiteActions.loadTestSuites({ projectId: this.projectId, query: this.currentQuery }));
    });

    this.searchChange$
      .pipe(debounceTime(300), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.applyFilters());
  }

  applyFilters(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { q: this.searchTerm || null, page: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  onSearchInput(): void {
    this.searchChange$.next();
  }

  onPage(event: PageEvent): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page: event.pageIndex, size: event.pageSize },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  onSortChange(sort: Sort): void {
    const sortParam = sort.direction ? `${sort.active},${sort.direction}` : null;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { sort: sortParam, page: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  retry(): void {
    this.store.dispatch(TestSuiteActions.loadTestSuites({ projectId: this.projectId, query: this.currentQuery }));
  }

  deleteTestSuite(id: string): void {
    this.store.dispatch(TestSuiteActions.deleteTestSuite({ projectId: this.projectId, id }));
  }
}
