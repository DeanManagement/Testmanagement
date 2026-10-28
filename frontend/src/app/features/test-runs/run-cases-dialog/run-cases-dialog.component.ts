import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { LowerCasePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { Observable } from 'rxjs';
import { filter, take } from 'rxjs/operators';
import { TestRunApiService } from '../../../core/services/test-run-api.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { TestCasePickerComponent } from '../../../shared/components/test-case-picker/test-case-picker.component';
import { TestResult, TestRun } from '../../../shared/models/test-run.model';
import { TestCaseSummary } from '../../../shared/models/test-suite.model';
import { ProjectMember } from '../../../shared/models/project-member.model';
import { MatSelectModule } from '@angular/material/select';

export interface RunCasesDialogData {
  projectId: string;
  run: TestRun;
  /** TM23: who results can be assigned to; empty hides the assignee picker. */
  members?: ProjectMember[];
}

/**
 * The cases of an open run (TM21, TM22): drag to set the execution order, remove a case, add more.
 * Every change is saved at once; the dialog closes with the run as the server last returned it.
 */
@Component({
  selector: 'app-run-cases-dialog',
  standalone: true,
  imports: [CdkDropList, CdkDrag, CdkDragHandle, LowerCasePipe, MatButtonModule, MatDialogModule, MatIconModule,
    MatTooltipModule, MatSelectModule, TranslateModule, TestCasePickerComponent],
  template: `
    <h2 mat-dialog-title>{{ 'testRun.cases.title' | translate }}</h2>
    <mat-dialog-content>
      <p class="hint">{{ 'testRun.cases.hint' | translate }}</p>
      <div cdkDropList class="case-list" (cdkDropListDropped)="drop($event)" data-test-id="run-cases-list">
        @for (result of run().results; track result.id) {
          <div class="case-row" cdkDrag cdkDragLockAxis="y" [attr.data-test-id]="'run-case-' + result.id">
            <mat-icon cdkDragHandle class="drag-handle" [attr.aria-label]="'testRun.cases.drag' | translate">drag_indicator</mat-icon>
            <span class="case-name">
              <span class="case-key">{{ result.testCaseKey }}</span> {{ result.testCaseTitle }}
              @if (result.parameterSetName) { <span class="param">· {{ result.parameterSetName }}</span> }
            </span>
            <span class="badge badge--{{ result.status | lowercase }} badge--small">{{ 'resultStatus.' + result.status | translate }}</span>
            @if (data.members?.length) {
              <mat-select class="assignee" [value]="result.assigneeId ?? null" [disabled]="busy()"
                          [placeholder]="'testRun.assignee.label' | translate"
                          [attr.aria-label]="'testRun.assignee.label' | translate"
                          (selectionChange)="assign(result, $event.value)" [attr.data-test-id]="'run-case-assignee-' + result.id">
                <mat-option [value]="null">{{ 'testRun.assignee.unassign' | translate }}</mat-option>
                @for (m of data.members; track m.userId) {
                  <mat-option [value]="m.userId">{{ m.displayName }}</mat-option>
                }
              </mat-select>
            }
            <button mat-icon-button type="button" (click)="remove(result)" [disabled]="busy()"
                    [matTooltip]="'testRun.cases.remove' | translate"
                    [attr.data-test-id]="'run-case-remove-' + result.id">
              <mat-icon>remove_circle_outline</mat-icon>
            </button>
          </div>
        }
      </div>
      <h3>{{ 'testRun.cases.addTitle' | translate }}</h3>
      <app-test-case-picker [projectId]="data.projectId" [(selected)]="toAdd" />
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button type="button" [disabled]="toAdd().length === 0 || busy()" (click)="add()"
              data-test-id="run-cases-add-btn">
        {{ 'testRun.cases.add' | translate: { count: toAdd().length } }}
      </button>
      <button mat-flat-button type="button" (click)="close()" data-test-id="run-cases-close-btn">{{ 'common.close' | translate }}</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .hint { color: var(--tm-text-secondary); margin-top: 0; }
    .case-list { display: flex; flex-direction: column; gap: 4px; margin-bottom: 16px; }
    .case-row { display: flex; align-items: center; gap: 8px; padding: 4px 8px; border: 1px solid var(--tm-border);
      border-radius: 6px; background: var(--tm-surface); }
    .drag-handle { cursor: grab; color: var(--tm-text-secondary); }
    .case-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .case-key { font-weight: 600; }
    .param { color: var(--tm-text-secondary); }
    .assignee { width: 160px; flex: none; font-size: 13px; }
    .cdk-drag-placeholder { opacity: 0.3; }
  `],
})
export class RunCasesDialogComponent {
  readonly data = inject<RunCasesDialogData>(MAT_DIALOG_DATA);
  private readonly api = inject(TestRunApiService);
  private readonly dialog = inject(MatDialog);
  private readonly dialogRef = inject(MatDialogRef<RunCasesDialogComponent, TestRun>);
  private readonly destroyRef = inject(DestroyRef);

  readonly run = signal<TestRun>(this.data.run);
  readonly toAdd = signal<TestCaseSummary[]>([]);
  readonly busy = signal(false);

  drop(event: CdkDragDrop<unknown>): void {
    if (event.previousIndex === event.currentIndex) return;
    const ordered = [...(this.run().results ?? [])];
    moveItemInArray(ordered, event.previousIndex, event.currentIndex);
    // Shown at once; the server's answer replaces it, or the old order comes back on an error.
    const before = this.run();
    this.run.set({ ...before, results: ordered });
    this.save(this.api.reorderResults(this.data.projectId, before.id, ordered.map((r) => r.id)), before);
  }

  /** Removes the case (every parameter set of it); a recorded result is only discarded after asking. */
  remove(result: TestResult): void {
    const recorded = (this.run().results ?? [])
      .some((r) => r.testCaseId === result.testCaseId && r.status !== 'PENDING');
    if (!recorded) {
      this.save(this.api.removeTestCases(this.data.projectId, this.run().id, [result.testCaseId], false));
      return;
    }
    const data: ConfirmDialogData = {
      titleKey: 'testRun.cases.discardTitle',
      messageKey: 'testRun.cases.discardMessage',
      messageParams: { key: result.testCaseKey },
      danger: true,
    };
    this.dialog.open(ConfirmDialogComponent, { data }).afterClosed()
      .pipe(take(1), filter(Boolean), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.save(this.api.removeTestCases(this.data.projectId, this.run().id, [result.testCaseId], true)));
  }

  add(): void {
    const ids = this.toAdd().map((tc) => tc.id);
    this.save(this.api.addTestCases(this.data.projectId, this.run().id, ids), undefined, () => this.toAdd.set([]));
  }

  /** TM23: null hands the result back to the run's executor. */
  assign(result: TestResult, assigneeId: string | null): void {
    this.save(this.api.assignResults(this.data.projectId, this.run().id, [result.id], assigneeId));
  }

  close(): void {
    this.dialogRef.close(this.run());
  }

  private save(call: Observable<TestRun>, rollback?: TestRun, onSaved?: () => void): void {
    this.busy.set(true);
    call.pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (run) => {
        this.run.set(run);
        this.busy.set(false);
        onSaved?.();
      },
      error: () => {
        if (rollback) this.run.set(rollback);
        this.busy.set(false);
      },
    });
  }
}
