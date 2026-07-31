import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AsyncPipe, LowerCasePipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatTreeModule, MatTreeFlatDataSource, MatTreeFlattener } from '@angular/material/tree';
import { FlatTreeControl } from '@angular/cdk/tree';
import { MatMenuModule } from '@angular/material/menu';
import { MatBadgeModule } from '@angular/material/badge';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject } from 'rxjs';
import { debounceTime, take } from 'rxjs/operators';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { ImportTestCasesDialogComponent } from '../import-test-cases-dialog/import-test-cases-dialog.component';
import { TestCaseActions } from '../../../store/test-case/test-case.actions';
import { selectAllTestCases, selectTestCasesLoading, selectSelectedTestCaseIds, selectHasSelection, selectTestCasePage } from '../../../store/test-case/test-case.selectors';
import { TestCaseFolderActions } from '../../../store/test-case-folder/test-case-folder.actions';
import { selectFolderTree } from '../../../store/test-case-folder/test-case-folder.selectors';
import { BulkStatusDialogComponent } from '../bulk-status-dialog/bulk-status-dialog.component';
import { BulkAddToSuiteDialogComponent } from '../bulk-add-to-suite-dialog/bulk-add-to-suite-dialog.component';
import { FolderNameDialogComponent, FolderNameDialogData } from '../folder-name-dialog/folder-name-dialog.component';
import { TestSuiteApiService } from '../../../core/services/test-suite-api.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { TestCaseFolder } from '../../../shared/models/test-case-folder.model';
import { Priority, TestCaseQuery, TestCaseStatus } from '../../../shared/models/test-case.model';

interface FlatFolderNode {
  id: string;
  name: string;
  level: number;
  expandable: boolean;
  testCaseCount: number;
  parentId: string | null;
}

@Component({
  selector: 'app-test-case-list',
  standalone: true,
  imports: [
    AsyncPipe,
    LowerCasePipe,
    RouterLink,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatCheckboxModule,
    MatTreeModule,
    MatMenuModule,
    MatBadgeModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatPaginatorModule,
    MatSortModule,
    MatTooltipModule,
    TranslateModule,
  ],
  templateUrl: './test-case-list.component.html',
  styleUrl: './test-case-list.component.scss',
})
export class TestCaseListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly testSuiteApi = inject(TestSuiteApiService);
  private readonly testCaseApi = inject(TestCaseApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);

  /** Upper bound for cross-page bulk selection (PRD-022 §4.4). */
  private static readonly MAX_SELECTION = 500;

  projectId = '';
  testCases$ = this.store.select(selectAllTestCases);
  loading$ = this.store.select(selectTestCasesLoading);
  selectedIds$ = this.store.select(selectSelectedTestCaseIds);
  hasSelection$ = this.store.select(selectHasSelection);
  folders$ = this.store.select(selectFolderTree);
  page$ = this.store.select(selectTestCasePage);
  displayedColumns = ['select', 'key', 'title', 'priority', 'status', 'labels', 'actions'];

  selectedFolderId: string | null = null;
  /** Table row density; persisted across sessions (PRD-008 §2.3). */
  density: 'comfortable' | 'compact' =
    (localStorage.getItem('tc-density') as 'comfortable' | 'compact') ?? 'comfortable';
  searchTerm = '';
  statusFilter: TestCaseStatus | '' = '';
  priorityFilter: Priority | '' = '';
  sortActive = 'updatedAt';
  sortDirection: 'asc' | 'desc' = 'desc';
  allStatuses: TestCaseStatus[] = ['DRAFT', 'ACTIVE', 'DEPRECATED'];
  allPriorities: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

  private readonly searchChange$ = new Subject<void>();

  private transformer = (node: TestCaseFolder, level: number): FlatFolderNode => ({
    id: node.id,
    name: node.name,
    level,
    expandable: node.children.length > 0,
    testCaseCount: node.testCaseCount,
    parentId: node.parentId,
  });

  treeControl = new FlatTreeControl<FlatFolderNode>(
    node => node.level,
    node => node.expandable
  );

  treeFlattener = new MatTreeFlattener<TestCaseFolder, FlatFolderNode>(
    this.transformer,
    node => node.level,
    node => node.expandable,
    node => node.children
  );

  dataSource = new MatTreeFlatDataSource(this.treeControl, this.treeFlattener);

  hasChild = (_: number, node: FlatFolderNode) => node.expandable;

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    if (!this.projectId) {
      return;
    }

    this.store.dispatch(TestCaseFolderActions.loadFolders({ projectId: this.projectId }));
    this.folders$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(folders => {
      this.dataSource.data = folders;
    });

    // URL query params are the single source of truth for filters + paging.
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.searchTerm = params.get('q') ?? '';
      this.statusFilter = (params.get('status') as TestCaseStatus) ?? '';
      this.priorityFilter = (params.get('priority') as Priority) ?? '';
      this.selectedFolderId = params.get('folderId');
      this.sortActive = params.get('sort')?.split(',')[0] ?? 'updatedAt';
      this.sortDirection = (params.get('sort')?.split(',')[1] as 'asc' | 'desc') ?? 'desc';

      const query: TestCaseQuery = {
        q: this.searchTerm || undefined,
        status: this.statusFilter ? [this.statusFilter] : undefined,
        priority: this.priorityFilter ? [this.priorityFilter] : undefined,
        folderId: this.selectedFolderId,
        page: params.get('page') ? Number(params.get('page')) : 0,
        size: params.get('size') ? Number(params.get('size')) : 50,
        sort: params.get('sort') ?? 'updatedAt,desc',
      };
      this.store.dispatch(TestCaseFolderActions.selectFolder({ folderId: this.selectedFolderId }));
      this.store.dispatch(TestCaseActions.loadTestCases({ projectId: this.projectId, query }));
    });

    this.searchChange$
      .pipe(debounceTime(300), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.applyFilters());
  }

  /** Writes the current filter form into the URL, resetting to the first page. */
  applyFilters(): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        q: this.searchTerm || null,
        status: this.statusFilter || null,
        priority: this.priorityFilter || null,
        page: null,
      },
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

  selectFolder(folderId: string | null): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { folderId: folderId || null, page: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  createFolder(parentId?: string | null): void {
    const dialogRef = this.dialog.open(FolderNameDialogComponent, {
      width: '400px',
      data: { title: 'folder.createTitle', name: '' } as FolderNameDialogData,
    });
    dialogRef.afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe(name => {
      if (name) {
        this.store.dispatch(TestCaseFolderActions.createFolder({
          projectId: this.projectId,
          request: { name, parentId: parentId ?? undefined },
        }));
      }
    });
  }

  renameFolder(node: FlatFolderNode): void {
    const dialogRef = this.dialog.open(FolderNameDialogComponent, {
      width: '400px',
      data: { title: 'folder.renameTitle', name: node.name } as FolderNameDialogData,
    });
    dialogRef.afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe(name => {
      if (name) {
        this.store.dispatch(TestCaseFolderActions.updateFolder({
          projectId: this.projectId,
          folderId: node.id,
          request: { name },
        }));
      }
    });
  }

  deleteFolder(node: FlatFolderNode): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: {
        titleKey: 'common.delete',
        messageKey: 'folder.deleteConfirm',
        messageParams: { name: node.name },
        danger: true,
      } as ConfirmDialogData,
    });
    dialogRef.afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe(confirmed => {
      if (confirmed) {
        this.store.dispatch(TestCaseFolderActions.deleteFolder({
          projectId: this.projectId,
          folderId: node.id,
        }));
      }
    });
  }

  onDragStart(event: DragEvent, testCaseId: string): void {
    event.dataTransfer?.setData('text/plain', testCaseId);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    const target = event.currentTarget as HTMLElement;
    target.classList.add('drag-over');
  }

  onDragLeave(event: DragEvent): void {
    const target = event.currentTarget as HTMLElement;
    target.classList.remove('drag-over');
  }

  onDropOnFolder(event: DragEvent, folderId: string | null): void {
    event.preventDefault();
    const target = event.currentTarget as HTMLElement;
    target.classList.remove('drag-over');
    const testCaseId = event.dataTransfer?.getData('text/plain');
    if (testCaseId) {
      this.store.dispatch(TestCaseFolderActions.moveTestCases({
        projectId: this.projectId,
        request: {
          testCaseIds: [testCaseId],
          targetFolderId: folderId,
        },
      }));
    }
  }

  getCreateLink(): Record<string, string> {
    if (this.selectedFolderId) {
      return { folderId: this.selectedFolderId };
    }
    return {};
  }

  toggleSelect(id: string, selectedIds: string[]): void {
    // Adding (not removing) past the cap is rejected with feedback (PRD-022 §4.4).
    if (!selectedIds.includes(id) && selectedIds.length >= TestCaseListComponent.MAX_SELECTION) {
      this.showSelectionLimit();
      return;
    }
    this.store.dispatch(TestCaseActions.toggleSelectTestCase({ id }));
  }

  isSelected(id: string, selectedIds: string[]): boolean {
    return selectedIds.includes(id);
  }

  selectAll(ids: string[]): void {
    let capped = ids;
    if (ids.length > TestCaseListComponent.MAX_SELECTION) {
      capped = ids.slice(0, TestCaseListComponent.MAX_SELECTION);
      this.showSelectionLimit();
    }
    this.store.dispatch(TestCaseActions.selectAllTestCases({ ids: capped }));
  }

  private showSelectionLimit(): void {
    this.snackBar.open(
      this.translate.instant('bulk.selectionLimit', { max: TestCaseListComponent.MAX_SELECTION }),
      'OK',
      { duration: 4000 },
    );
  }

  deselectAll(): void {
    this.store.dispatch(TestCaseActions.deselectAllTestCases());
  }

  toggleSelectAll(testCases: { id: string }[], selectedIds: string[]): void {
    if (selectedIds.length === testCases.length) {
      this.deselectAll();
    } else {
      this.selectAll(testCases.map(tc => tc.id));
    }
  }

  bulkUpdateStatus(selectedIds: string[]): void {
    const dialogRef = this.dialog.open(BulkStatusDialogComponent, { width: '400px' });
    dialogRef.afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe(status => {
      if (status) {
        this.store.dispatch(TestCaseActions.bulkUpdateStatus({
          projectId: this.projectId,
          testCaseIds: selectedIds,
          status,
        }));
      }
    });
  }

  bulkDelete(selectedIds: string[]): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: {
        titleKey: 'bulk.delete',
        messageKey: 'bulk.confirmDelete',
        messageParams: { count: selectedIds.length },
        secondaryMessageKey: 'bulk.deleteWarning',
        danger: true,
      } as ConfirmDialogData,
    });
    dialogRef.afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe(confirmed => {
      if (confirmed) {
        this.store.dispatch(TestCaseActions.bulkDelete({
          projectId: this.projectId,
          testCaseIds: selectedIds,
        }));
      }
    });
  }

  bulkAddToSuite(selectedIds: string[]): void {
    const dialogRef = this.dialog.open(BulkAddToSuiteDialogComponent, { width: '400px' });
    dialogRef.componentInstance.projectId = this.projectId;
    dialogRef.afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe(suiteId => {
      if (suiteId) {
        this.testSuiteApi.bulkAddTestCases(this.projectId, suiteId, selectedIds)
          .pipe(take(1), takeUntilDestroyed(this.destroyRef))
          .subscribe(() => {
            this.store.dispatch(TestCaseActions.deselectAllTestCases());
          });
      }
    });
  }

  deleteTestCase(id: string): void {
    this.store.dispatch(TestCaseActions.deleteTestCase({ projectId: this.projectId, id }));
  }

  toggleDensity(): void {
    this.density = this.density === 'compact' ? 'comfortable' : 'compact';
    localStorage.setItem('tc-density', this.density);
  }

  exportTestCases(format: 'json' | 'csv', excel = false): void {
    this.testCaseApi.export(this.projectId, format, excel)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `test-cases.${format}`;
        a.click();
        setTimeout(() => URL.revokeObjectURL(url));
      });
  }

  openImport(): void {
    const dialogRef = this.dialog.open(ImportTestCasesDialogComponent, { width: '560px' });
    dialogRef.componentInstance.projectId = this.projectId;
    dialogRef.afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe((result) => {
      if (result) {
        this.snackBar.open(
          this.translate.instant('import.done', { imported: result.imported, skipped: result.skipped }),
          'OK',
          { duration: 5000 },
        );
        this.store.dispatch(TestCaseActions.loadTestCases({ projectId: this.projectId }));
      }
    });
  }
}
