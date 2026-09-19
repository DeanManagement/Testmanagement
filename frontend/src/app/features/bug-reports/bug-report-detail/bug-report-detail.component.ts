import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
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
import { combineLatest, Observable, of } from 'rxjs';
import { take } from 'rxjs/operators';
import { BugReportActions } from '../../../store/bug-report/bug-report.actions';
import { selectBugReportById } from '../../../store/bug-report/bug-report.selectors';
import { ALL_BUG_STATUSES, BugReport, BugReportLink, BugReportStatus, ChangeBugStatusRequest } from '../../../shared/models/bug-report.model';
import { BugReportApiService } from '../../../core/services/bug-report-api.service';
import { ChangeBugStatusDialogComponent, ChangeBugStatusDialogData } from '../change-bug-status-dialog/change-bug-status-dialog.component';
import { WatchToggleComponent } from '../../../shared/components/watch-toggle/watch-toggle.component';
import { CustomFieldsDisplayComponent } from '../../../shared/components/custom-fields/custom-fields-display.component';
import { EntityHistoryComponent } from '../../../shared/components/entity-history/entity-history.component';
import { AttachmentsComponent } from '../../../shared/components/attachments/attachments.component';
import { ProjectMemberApiService } from '../../../core/services/project-member-api.service';
import { selectAuthUser } from '../../../store/auth/auth.selectors';

@Component({
  selector: 'app-bug-report-detail',
  standalone: true,
  imports: [
    AttachmentsComponent,
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
  private readonly memberApi = inject(ProjectMemberApiService);
  private readonly destroyRef = inject(DestroyRef);

  projectId = '';
  bugId = '';
  bugReport$: Observable<BugReport | undefined> = of(undefined);
  readonly statuses = ALL_BUG_STATUSES;
  /** TESTER and up may attach and remove files; viewers see them read-only. */
  readonly canWrite = signal(false);

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
    if (this.projectId) {
      this.loadRole();
    }
  }

  attachmentsUrl(bug: BugReport): string {
    return this.bugReportApi.attachmentsUrl(this.projectId, bug.id);
  }

  private loadRole(): void {
    combineLatest([this.memberApi.getByProject(this.projectId), this.store.select(selectAuthUser).pipe(take(1))])
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ([members, user]) => {
          const role = members.find((m) => m.userId === user?.id)?.role;
          this.canWrite.set(!!user && (user.systemAdmin || role === 'ADMIN' || role === 'TESTER'));
        },
        error: () => undefined,
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
