import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe, LowerCasePipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { TestCaseActions } from '../../../store/test-case/test-case.actions';
import { selectAllTestCases, selectTestCasesLoading, selectSelectedTestCaseIds, selectHasSelection } from '../../../store/test-case/test-case.selectors';
import { BulkStatusDialogComponent } from '../bulk-status-dialog/bulk-status-dialog.component';
import { BulkAddToSuiteDialogComponent } from '../bulk-add-to-suite-dialog/bulk-add-to-suite-dialog.component';
import { TestSuiteApiService } from '../../../core/services/test-suite-api.service';

@Component({
  selector: 'app-test-case-list',
  standalone: true,
  imports: [
    AsyncPipe,
    LowerCasePipe,
    RouterLink,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatCheckboxModule,
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

  projectId = '';
  testCases$ = this.store.select(selectAllTestCases);
  loading$ = this.store.select(selectTestCasesLoading);
  selectedIds$ = this.store.select(selectSelectedTestCaseIds);
  hasSelection$ = this.store.select(selectHasSelection);
  displayedColumns = ['select', 'key', 'title', 'priority', 'status', 'labels', 'actions'];

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    if (this.projectId) {
      this.store.dispatch(TestCaseActions.loadTestCases({ projectId: this.projectId }));
    }
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
    if (confirm(`Delete ${selectedIds.length} test cases? This action cannot be undone.`)) {
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
