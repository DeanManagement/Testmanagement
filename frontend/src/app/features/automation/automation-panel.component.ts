import { ChangeDetectorRef, Component, DestroyRef, inject, Input, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { interval } from 'rxjs';
import { filter, take } from 'rxjs/operators';
import { BuildServerApiService } from '../../core/services/build-server-api.service';
import {
  isActivePipelineRun,
  PipelineRun,
  ProjectWorkflow,
} from '../../shared/models/build-server.model';
import { LocalizedDatePipe } from '../../shared/pipes/localized-date.pipe';
import {
  TriggerPipelineDialogComponent,
  TriggerPipelineDialogData,
} from './trigger-pipeline-dialog/trigger-pipeline-dialog.component';

/** How often the panel re-reads run state while something is in flight (local DB read only). */
const LIVE_REFRESH_MS = 5000;

/**
 * The tester's side of PRD-024, embedded on the test-runs page: the workflows assigned to this
 * project with a Run button each, and the recent pipeline runs with live status. Hidden entirely
 * when no workflow is assigned. While any run is non-terminal the list auto-refreshes, so a
 * triggered run visibly progresses TRIGGERED → RUNNING → SUCCESS/FAILED without a reload.
 */
@Component({
  selector: 'app-automation-panel',
  standalone: true,
  imports: [
    RouterLink,
    LocalizedDatePipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    TranslateModule,
  ],
  templateUrl: './automation-panel.component.html',
  styleUrl: './automation-panel.component.scss',
})
export class AutomationPanelComponent implements OnInit {
  private readonly api = inject(BuildServerApiService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) projectId = '';

  workflows: ProjectWorkflow[] = [];
  runs: PipelineRun[] = [];
  triggeringId: string | null = null;
  refreshingId: string | null = null;

  ngOnInit(): void {
    if (!this.projectId) {
      return;
    }
    this.api.getProjectWorkflows(this.projectId)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (workflows) => {
          this.workflows = workflows;
          if (workflows.length > 0) {
            this.loadRuns();
          }
          this.cdr.markForCheck();
        },
        error: () => undefined,
      });

    interval(LIVE_REFRESH_MS)
      .pipe(
        filter(() => this.workflows.length > 0 && this.runs.some(isActivePipelineRun)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.loadRuns());
  }

  get visible(): boolean {
    return this.workflows.length > 0;
  }

  hasLiveRuns(): boolean {
    return this.runs.some(isActivePipelineRun);
  }

  isActive(run: PipelineRun): boolean {
    return isActivePipelineRun(run);
  }

  trigger(workflow: ProjectWorkflow): void {
    this.dialog.open(TriggerPipelineDialogComponent, {
      data: { workflow } satisfies TriggerPipelineDialogData,
    }).afterClosed()
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((request) => {
        if (!request) {
          return;
        }
        this.triggeringId = workflow.id;
        this.cdr.markForCheck();
        this.api.trigger(this.projectId, workflow.id, request)
          .pipe(take(1), takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: () => {
              this.triggeringId = null;
              this.snackBar.open(
                this.translate.instant('automation.triggered', { name: workflow.name }),
                undefined, { duration: 3000 });
              this.loadRuns();
            },
            error: (err) => {
              this.triggeringId = null;
              // The ERROR run is persisted server-side; reload so it shows with its message.
              this.loadRuns();
              this.snackBar.open(
                err?.error?.message ?? this.translate.instant('automation.triggerFailed'),
                undefined, { duration: 5000 });
            },
          });
      });
  }

  refresh(run: PipelineRun): void {
    this.refreshingId = run.id;
    this.cdr.markForCheck();
    this.api.refreshPipelineRun(this.projectId, run.id)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.refreshingId = null;
          this.runs = this.runs.map((r) => (r.id === updated.id ? updated : r));
          this.cdr.markForCheck();
        },
        error: () => {
          this.refreshingId = null;
          this.cdr.markForCheck();
        },
      });
  }

  private loadRuns(): void {
    this.api.getPipelineRuns(this.projectId, 0, 10)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.runs = page.content;
          this.cdr.markForCheck();
        },
        error: () => undefined,
      });
  }
}
