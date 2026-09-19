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
import { ALL_BUG_STATUSES, BugReport, BugReportLink, BugReportStatus, ChangeBugStatusRequest } from '../../../shared/models/bug-report.model';
import { BugReportApiService } from '../../../core/services/bug-report-api.service';
import { ChangeBugStatusDialogComponent, ChangeBugStatusDialogData } from '../change-bug-status-dialog/change-bug-status-dialog.component';
import { WatchToggleComponent } from '../../../shared/components/watch-toggle/watch-toggle.component';
import { CustomFieldsDisplayComponent } from '../../../shared/components/custom-fields/custom-fields-display.component';
import { EntityHistoryComponent } from '../../../shared/components/entity-history/entity-history.component';

@Component({
  selector: 'app-bug-report-detail',
  standalone: true,
  imports: [
    CustomFieldsDisplayComponent,
    EntityHistoryComponent,
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
  private readonly bugReportApi = inject(BugReportApiService);
  private readonly destroyRef = inject(DestroyRef);

  projectId = '';
  bugId = '';
  bugReport$: Observable<BugReport | undefined> = of(undefined);
  readonly statuses = ALL_BUG_STATUSES;

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    // A duplicate-of link leads to this same route, which reuses the component: follow the param.
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.bugId = params.get('bugId') ?? '';
      if (this.projectId && this.bugId) {
        this.store.dispatch(BugReportActions.loadBugReport({ projectId: this.projectId, id: this.bugId }));
        this.bugReport$ = this.store.select(selectBugReportById(this.bugId));
      }
    });
  }

  onStatusChange(bug: BugReport, newStatus: BugReportStatus): void {
    if (newStatus === bug.status) return;

    const dialogRef = this.dialog.open(ChangeBugStatusDialogComponent, {
      data: { projectId: this.projectId, currentStatus: bug.status, newStatus, bugIds: [bug.id] } as ChangeBugStatusDialogData,
    });

    dialogRef.afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((request: ChangeBugStatusRequest | undefined) => {
        if (request) {
          this.store.dispatch(BugReportActions.changeBugReportStatus({ projectId: this.projectId, id: bug.id, request }));
        }
      });
  }

  /** Removes a later occurrence; where the bug was found stays. */
  unlink(bug: BugReport, link: BugReportLink): void {
    this.bugReportApi.unlink(this.projectId, bug.id, link.id).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((updated) => this.store.dispatch(BugReportActions.loadBugReportSuccess({ bugReport: updated })));
  }

  deleteBugReport(id: string): void {
    this.store.dispatch(BugReportActions.deleteBugReport({ projectId: this.projectId, id }));
  }
}
