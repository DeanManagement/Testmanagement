import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
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
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TestCaseActions } from '../../../store/test-case/test-case.actions';
import { selectAllTestCases, selectTestCasesLoading, selectSelectedTestCaseIds, selectHasSelection } from '../../../store/test-case/test-case.selectors';
import { TestCaseFolderActions } from '../../../store/test-case-folder/test-case-folder.actions';
import { selectFolderTree, selectSelectedFolderId } from '../../../store/test-case-folder/test-case-folder.selectors';
import { BulkStatusDialogComponent } from '../bulk-status-dialog/bulk-status-dialog.component';
import { BulkAddToSuiteDialogComponent } from '../bulk-add-to-suite-dialog/bulk-add-to-suite-dialog.component';
import { FolderNameDialogComponent, FolderNameDialogData } from '../folder-name-dialog/folder-name-dialog.component';
import { TestSuiteApiService } from '../../../core/services/test-suite-api.service';
import { TestCaseFolder } from '../../../shared/models/test-case-folder.model';
import { TestCase, Priority, TestCaseStatus } from '../../../shared/models/test-case.model';

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
    TranslateModule,
  ],
  templateUrl: './test-case-list.component.html',
  styleUrl: './test-case-list.component.scss',
})
export class TestCaseListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly testSuiteApi = inject(TestSuiteApiService);
  private readonly translate = inject(TranslateService);

  projectId = '';
  testCases$ = this.store.select(selectAllTestCases);
  loading$ = this.store.select(selectTestCasesLoading);
  selectedIds$ = this.store.select(selectSelectedTestCaseIds);
  hasSelection$ = this.store.select(selectHasSelection);
  folders$ = this.store.select(selectFolderTree);
  selectedFolderId$ = this.store.select(selectSelectedFolderId);
  displayedColumns = ['select', 'key', 'title', 'priority', 'status', 'labels', 'actions'];

  selectedFolderId: string | null = null;
  searchTerm = '';
  statusFilter: TestCaseStatus | '' = '';
  priorityFilter: Priority | '' = '';
  allStatuses: TestCaseStatus[] = ['DRAFT', 'ACTIVE', 'DEPRECATED'];
  allPriorities: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

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
    if (this.projectId) {
      this.store.dispatch(TestCaseActions.loadTestCases({ projectId: this.projectId }));
      this.store.dispatch(TestCaseFolderActions.loadFolders({ projectId: this.projectId }));
    }

    this.folders$.subscribe(folders => {
      this.dataSource.data = folders;
    });

    this.selectedFolderId$.subscribe(folderId => {
      this.selectedFolderId = folderId;
    });
  }

  filteredTestCases(testCases: TestCase[]): TestCase[] {
    let result = testCases;
    if (this.searchTerm) {
      const term = this.searchTerm.toLowerCase();
      result = result.filter(tc =>
        tc.key.toLowerCase().includes(term) ||
        tc.title.toLowerCase().includes(term)
      );
    }
    if (this.statusFilter) {
      result = result.filter(tc => tc.status === this.statusFilter);
    }
    if (this.priorityFilter) {
      result = result.filter(tc => tc.priority === this.priorityFilter);
    }
    return result;
  }

  selectFolder(folderId: string | null): void {
    this.store.dispatch(TestCaseFolderActions.selectFolder({ folderId }));
    if (folderId) {
      this.store.dispatch(TestCaseActions.loadTestCases({ projectId: this.projectId, folderId }));
    } else {
      this.store.dispatch(TestCaseActions.loadTestCases({ projectId: this.projectId }));
    }
  }

  createFolder(parentId?: string | null): void {
    const dialogRef = this.dialog.open(FolderNameDialogComponent, {
      width: '400px',
      data: { title: 'folder.createTitle', name: '' } as FolderNameDialogData,
    });
    dialogRef.afterClosed().subscribe(name => {
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
    dialogRef.afterClosed().subscribe(name => {
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
    const message = this.translate.instant('folder.deleteConfirm', { name: node.name });
    if (confirm(message)) {
      this.store.dispatch(TestCaseFolderActions.deleteFolder({
        projectId: this.projectId,
        folderId: node.id,
      }));
    }
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

  toggleSelect(id: string): void {
    this.store.dispatch(TestCaseActions.toggleSelectTestCase({ id }));
  }

  isSelected(id: string, selectedIds: string[]): boolean {
    return selectedIds.includes(id);
  }

  selectAll(ids: string[]): void {
    this.store.dispatch(TestCaseActions.selectAllTestCases({ ids }));
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
    dialogRef.afterClosed().subscribe(status => {
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
    const message = this.translate.instant('bulk.confirmDelete', { count: selectedIds.length })
      + ' ' + this.translate.instant('bulk.deleteWarning');
    if (confirm(message)) {
      this.store.dispatch(TestCaseActions.bulkDelete({
        projectId: this.projectId,
        testCaseIds: selectedIds,
      }));
    }
  }

  bulkAddToSuite(selectedIds: string[]): void {
    const dialogRef = this.dialog.open(BulkAddToSuiteDialogComponent, { width: '400px' });
    dialogRef.componentInstance.projectId = this.projectId;
    dialogRef.afterClosed().subscribe(suiteId => {
      if (suiteId) {
        this.testSuiteApi.bulkAddTestCases(this.projectId, suiteId, selectedIds).subscribe(() => {
          this.store.dispatch(TestCaseActions.deselectAllTestCases());
        });
      }
    });
  }

  deleteTestCase(id: string): void {
    this.store.dispatch(TestCaseActions.deleteTestCase({ projectId: this.projectId, id }));
  }
}
