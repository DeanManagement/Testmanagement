import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AsyncPipe, LowerCasePipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { TestRunActions } from '../../../store/test-run/test-run.actions';
import { selectAllTestRuns, selectTestRunsLoading } from '../../../store/test-run/test-run.selectors';
import { TestRun, TestRunStatus } from '../../../shared/models/test-run.model';
import { CloneTestRunDialogComponent, CloneTestRunDialogResult } from '../clone-test-run-dialog/clone-test-run-dialog.component';

@Component({
  selector: 'app-test-run-list',
  standalone: true,
  imports: [
    AsyncPipe,
    LowerCasePipe,
    RouterLink,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './test-run-list.component.html',
  styleUrl: './test-run-list.component.scss',
})
export class TestRunListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);

  projectId = '';
  testRuns$ = this.store.select(selectAllTestRuns);
  loading$ = this.store.select(selectTestRunsLoading);
  displayedColumns = ['key', 'name', 'environment', 'status', 'results', 'actions'];
  searchTerm = '';
  statusFilter: TestRunStatus | '' = '';
  allStatuses: TestRunStatus[] = ['PLANNED', 'IN_PROGRESS', 'COMPLETED', 'ABORTED'];

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    if (this.projectId) {
      this.store.dispatch(TestRunActions.loadTestRuns({ projectId: this.projectId }));
    }
  }

  filteredTestRuns(testRuns: TestRun[]): TestRun[] {
    let result = testRuns;
    if (this.searchTerm) {
      const term = this.searchTerm.toLowerCase();
      result = result.filter(r =>
        r.key.toLowerCase().includes(term) ||
        r.name.toLowerCase().includes(term)
      );
    }
    if (this.statusFilter) {
      result = result.filter(r => r.status === this.statusFilter);
    }
    return result;
  }

  cloneTestRun(run: TestRun): void {
    const dialogRef = this.dialog.open(CloneTestRunDialogComponent, {
      data: { name: run.name, environment: run.environment },
    });
    dialogRef.afterClosed().subscribe((result: CloneTestRunDialogResult | undefined) => {
      if (result) {
        this.store.dispatch(
          TestRunActions.cloneTestRun({
            projectId: this.projectId,
            runId: run.id,
            request: result,
          })
        );
      }
    });
  }

  deleteTestRun(id: string): void {
    this.store.dispatch(TestRunActions.deleteTestRun({ projectId: this.projectId, id }));
  }
}
