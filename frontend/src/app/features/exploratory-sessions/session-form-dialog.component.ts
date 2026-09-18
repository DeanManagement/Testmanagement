import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { forkJoin } from 'rxjs';
import { take } from 'rxjs/operators';
import { ProjectMemberApiService } from '../../core/services/project-member-api.service';
import { TestPlanApiService } from '../../core/services/test-plan-api.service';
import { EnvironmentInputComponent } from '../../shared/components/environment-input/environment-input.component';
import {
  CreateSessionRequest,
  MAX_TIMEBOX_MINUTES,
  MIN_TIMEBOX_MINUTES,
} from '../../shared/models/exploratory-session.model';
import { ProjectMember } from '../../shared/models/project-member.model';
import { TestPlan } from '../../shared/models/test-plan.model';

export interface SessionFormDialogData {
  projectId: string;
  /** Pre-selects a plan, e.g. when opened from a plan's page. */
  testPlanId?: string;
}

/** Charter, time box and optional plan, environment and tester for a new session (PRD-034). */
@Component({
  selector: 'app-session-form-dialog',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    TranslateModule, EnvironmentInputComponent],
  template: `
    <h2 mat-dialog-title>{{ 'session.form.title' | translate }}</h2>
    <mat-dialog-content class="form">
      <mat-form-field appearance="outline">
        <mat-label>{{ 'session.charter' | translate }}</mat-label>
        <textarea matInput rows="3" [(ngModel)]="charter" required
                  [placeholder]="'session.form.charterHint' | translate" data-test-id="session-charter-input"></textarea>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>{{ 'session.form.timebox' | translate }}</mat-label>
        <input matInput type="number" [min]="minTimebox" [max]="maxTimebox" [(ngModel)]="timeboxMinutes"
               data-test-id="session-timebox-input" />
        <mat-hint>{{ 'session.form.timeboxHint' | translate: { min: minTimebox, max: maxTimebox } }}</mat-hint>
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>{{ 'session.plan' | translate }}</mat-label>
        <mat-select [(ngModel)]="testPlanId" data-test-id="session-plan-select">
          <mat-option value="">{{ 'session.form.none' | translate }}</mat-option>
          @for (plan of plans; track plan.id) {
            <mat-option [value]="plan.id">{{ plan.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <app-environment-input [projectId]="data.projectId" testId="session-environment-input" [(ngModel)]="environment" />
      <mat-form-field appearance="outline">
        <mat-label>{{ 'session.tester' | translate }}</mat-label>
        <mat-select [(ngModel)]="testerId" data-test-id="session-tester-select">
          <mat-option value="">{{ 'session.form.none' | translate }}</mat-option>
          @for (member of members; track member.userId) {
            <mat-option [value]="member.userId">{{ member.displayName }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'common.cancel' | translate }}</button>
      <button mat-flat-button [disabled]="!valid" (click)="save()" data-test-id="session-save-btn">
        {{ 'session.form.create' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: ['.form { display: flex; flex-direction: column; gap: 4px; min-width: min(90vw, 460px); }'],
})
export class SessionFormDialogComponent implements OnInit {
  private readonly dialogRef = inject(MatDialogRef<SessionFormDialogComponent, CreateSessionRequest>);
  private readonly planApi = inject(TestPlanApiService);
  private readonly memberApi = inject(ProjectMemberApiService);
  readonly data: SessionFormDialogData = inject(MAT_DIALOG_DATA);

  readonly minTimebox = MIN_TIMEBOX_MINUTES;
  readonly maxTimebox = MAX_TIMEBOX_MINUTES;
  charter = '';
  timeboxMinutes = 60;
  testPlanId = this.data.testPlanId ?? '';
  environment = '';
  testerId = '';
  plans: TestPlan[] = [];
  members: ProjectMember[] = [];

  ngOnInit(): void {
    forkJoin({ plans: this.planApi.getAll(this.data.projectId), members: this.memberApi.getByProject(this.data.projectId) })
      .pipe(take(1))
      .subscribe(({ plans, members }) => {
        this.plans = plans;
        this.members = members.filter((member) => member.role !== 'VIEWER');
      });
  }

  get valid(): boolean {
    return this.charter.trim().length > 0
      && this.timeboxMinutes >= this.minTimebox && this.timeboxMinutes <= this.maxTimebox;
  }

  save(): void {
    this.dialogRef.close({
      charter: this.charter.trim(),
      timeboxMinutes: this.timeboxMinutes,
      testPlanId: this.testPlanId || undefined,
      environment: this.environment.trim() || undefined,
      testerId: this.testerId || undefined,
    });
  }
}
