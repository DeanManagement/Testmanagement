import { Component, DestroyRef, inject, input, OnChanges, output, signal, SimpleChanges } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { Observable } from 'rxjs';
import { BugReportApiService } from '../../../core/services/bug-report-api.service';
import { IssueTrackerApiService } from '../../../core/services/issue-tracker-api.service';
import { IssueLink } from '../../../shared/models/issue-tracker.model';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';

/**
 * TM50: hands a bug over to the project's Azure DevOps, or shows the work item it follows.
 * {@code linkedChange} tells the page whether the status is owned by the tracker; {@code statusChanged}
 * that the bug may have a new status and should be reloaded.
 */
@Component({
  selector: 'app-bug-report-tracker',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, TranslateModule,
    LocalizedDatePipe],
  templateUrl: './bug-report-tracker.component.html',
  styleUrl: './bug-report-tracker.component.scss',
})
export class BugReportTrackerComponent implements OnChanges {
  private readonly api = inject(BugReportApiService);
  private readonly trackerApi = inject(IssueTrackerApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly projectId = input.required<string>();
  readonly bugId = input.required<string>();
  readonly canEdit = input(false);
  readonly linkedChange = output<boolean>();
  readonly statusChanged = output<void>();

  readonly link = signal<IssueLink | null>(null);
  /** Handover is offered only when the project's tracker is Azure DevOps. */
  readonly available = signal(false);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  existingId = '';

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['projectId'] && !changes['bugId']) {
      return;
    }
    this.trackerApi.getStatus(this.projectId()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (status) => this.available.set(status.configured && status.provider === 'AZURE_DEVOPS'),
      error: () => this.available.set(false),
    });
    this.api.getTrackerIssue(this.projectId(), this.bugId()).pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((link) => this.setLink(link ?? null));
  }

  handOver(): void {
    this.run(this.api.handOver(this.projectId(), this.bugId(), this.existingId.trim() || undefined), (link) => {
      this.existingId = '';
      this.setLink(link);
    });
  }

  refresh(): void {
    this.run(this.api.refreshTrackerIssue(this.projectId(), this.bugId()), (link) => this.setLink(link));
  }

  detach(): void {
    this.run(this.api.detachTrackerIssue(this.projectId(), this.bugId()), () => this.setLink(null));
  }

  private setLink(link: IssueLink | null): void {
    this.link.set(link);
    this.linkedChange.emit(link !== null);
  }

  private run<T>(request$: Observable<T>, onSuccess: (value: T) => void): void {
    this.busy.set(true);
    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (value) => {
        this.busy.set(false);
        this.error.set(null);
        onSuccess(value);
        this.statusChanged.emit();
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err?.error?.message ?? 'bugReport.tracker.failed');
      },
    });
  }
}
