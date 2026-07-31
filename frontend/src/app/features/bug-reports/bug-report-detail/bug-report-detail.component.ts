import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe, LowerCasePipe } from '@angular/common';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import { take } from 'rxjs/operators';
import { BugReportActions } from '../../../store/bug-report/bug-report.actions';
import { selectBugReportById } from '../../../store/bug-report/bug-report.selectors';
import { BugReport, BugReportStatus } from '../../../shared/models/bug-report.model';
import { ChangeBugStatusDialogComponent, ChangeBugStatusDialogData } from '../change-bug-status-dialog/change-bug-status-dialog.component';
import { WatchToggleComponent } from '../../../shared/components/watch-toggle/watch-toggle.component';

@Component({
  selector: 'app-bug-report-detail',
  standalone: true,
  imports: [
    AsyncPipe,
    LocalizedDatePipe,
    LowerCasePipe,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    TranslateModule,
    WatchToggleComponent,
  ],
  templateUrl: './bug-report-detail.component.html',
  styleUrl: './bug-report-detail.component.scss',
})
export class BugReportDetailComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  projectId = '';
  bugId = '';
  bugReport$: Observable<BugReport | undefined> = of(undefined);
  statuses: BugReportStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'WONTFIX'];

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.bugId = this.route.snapshot.paramMap.get('bugId') ?? '';
    if (this.projectId && this.bugId) {
      this.store.dispatch(BugReportActions.loadBugReport({ projectId: this.projectId, id: this.bugId }));
      this.bugReport$ = this.store.select(selectBugReportById(this.bugId));
    }
  }

  onStatusChange(bug: BugReport, newStatus: BugReportStatus): void {
    if (newStatus === bug.status) return;

    const dialogRef = this.dialog.open(ChangeBugStatusDialogComponent, {
      data: { currentStatus: bug.status, newStatus } as ChangeBugStatusDialogData,
    });

    dialogRef.afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe((reason: string | undefined) => {
      if (reason) {
        this.store.dispatch(
          BugReportActions.changeBugReportStatus({
            projectId: this.projectId,
            id: bug.id,
            status: newStatus,
            reason,
          })
        );
      }
    });
  }

  deleteBugReport(id: string): void {
    this.store.dispatch(BugReportActions.deleteBugReport({ projectId: this.projectId, id }));
  }
}
