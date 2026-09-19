import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { take } from 'rxjs/operators';
import { Store } from '@ngrx/store';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { BugReportActions } from '../../../store/bug-report/bug-report.actions';
import { selectBugReportById } from '../../../store/bug-report/bug-report.selectors';
import { Priority } from '../../../shared/models/bug-report.model';
import { ProjectApiService } from '../../../core/services/project-api.service';
import { Project } from '../../../shared/models/project.model';
import { ProjectMemberApiService } from '../../../core/services/project-member-api.service';
import { ProjectMember } from '../../../shared/models/project-member.model';
import { HasUnsavedChanges } from '../../../core/guards/unsaved-changes.guard';
import { FieldErrorComponent } from '../../../shared/components/field-error/field-error.component';
import { CustomFieldsFormComponent } from '../../../shared/components/custom-fields/custom-fields-form.component';
import { CustomFieldValues } from '../../../shared/models/custom-field.model';
import { EnvironmentInputComponent } from '../../../shared/components/environment-input/environment-input.component';
import { EnvironmentApiService } from '../../../core/services/environment-api.service';
import { BugReportApiService } from '../../../core/services/bug-report-api.service';
import { AttachmentsComponent } from '../../../shared/components/attachments/attachments.component';

@Component({
  selector: 'app-bug-report-form',
  standalone: true,
  imports: [
    AttachmentsComponent,
    CustomFieldsFormComponent,
    FieldErrorComponent,
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    EnvironmentInputComponent,
    TranslateModule,
  ],
  templateUrl: './bug-report-form.component.html',
  styleUrl: './bug-report-form.component.scss',
})
export class BugReportFormComponent implements OnInit, HasUnsavedChanges {
  private readonly environmentApi = inject(EnvironmentApiService);
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly memberApi = inject(ProjectMemberApiService);
  private readonly projectApi = inject(ProjectApiService);
  private readonly bugReportApi = inject(BugReportApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  projectId = '';
  /** Set when filing from an exploratory session; links the new bug to it. */
  exploratorySessionId: string | null = null;
  /** Set when reporting from a failed step (PRD-047); the bug records it as where it was found. */
  stepResultId: string | null = null;
  bugId = '';
  isEdit = false;
  saving = false;
  dirty = false;
  members: ProjectMember[] = [];
  /** PRD-051: files picked before the bug exists; the create effect uploads them after saving. */
  queuedFiles: File[] = [];
  /** Step screenshots of the result reported from, which the server copies onto the new bug. */
  screenshotCount = 0;

  priorities: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

  form = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(255)]],
    description: [''],
    stepsToReproduce: [''],
    expectedBehavior: [''],
    actualBehavior: [''],
    priority: ['HIGH' as Priority, [Validators.required]],
    environment: [''],
    testResultId: [''],
    testRunId: [''],
    assigneeId: [''],
    customFields: this.fb.control<CustomFieldValues>({}),
  });

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.bugId = this.route.snapshot.paramMap.get('bugId') ?? '';
    this.isEdit = !!this.bugId;

    if (this.projectId) {
      this.memberApi.getByProject(this.projectId).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe((m) => {
        this.members = m;
        this.cdr.detectChanges();
      });
    }

    // Pre-fill from query params (when coming from test runner)
    const params = this.route.snapshot.queryParams;
    if (params['testResultId']) {
      this.form.patchValue({ testResultId: params['testResultId'] });
    }
    if (params['testRunId']) {
      this.form.patchValue({ testRunId: params['testRunId'] });
    }
    if (params['environment']) {
      this.form.patchValue({ environment: params['environment'] });
    }
    if (params['testCaseTitle']) {
      this.form.patchValue({ title: `Bug: ${params['testCaseTitle']}` });
    }
    // PRD-034: filed from an exploratory session note.
    if (params['title']) {
      this.form.patchValue({ title: params['title'] });
    }
    if (params['description']) {
      this.form.patchValue({ description: params['description'] });
    }
    // PRD-047: reported from a failed step, whose number and actual result come along.
    if (params['stepsToReproduce']) {
      this.form.patchValue({ stepsToReproduce: params['stepsToReproduce'] });
    }
    if (params['actualBehavior']) {
      this.form.patchValue({ actualBehavior: params['actualBehavior'] });
    }
    this.stepResultId = params['stepResultId'] ?? null;
    this.screenshotCount = Number(params['screenshots']) || 0;
    this.exploratorySessionId = params['exploratorySessionId'] ?? null;

    if (!this.isEdit && this.projectId) {
      this.projectApi.getById(this.projectId).pipe(take(1), takeUntilDestroyed(this.destroyRef))
        .subscribe((project) => this.applyTemplate(project));
    }

    if (this.isEdit && this.projectId) {
      this.store.dispatch(BugReportActions.loadBugReport({ projectId: this.projectId, id: this.bugId }));
      this.store.select(selectBugReportById(this.bugId)).pipe(takeUntilDestroyed(this.destroyRef)).subscribe((bug) => {
        if (bug) {
          this.form.patchValue({
            title: bug.title,
            description: bug.description || '',
            stepsToReproduce: bug.stepsToReproduce || '',
            expectedBehavior: bug.expectedBehavior || '',
            actualBehavior: bug.actualBehavior || '',
            priority: bug.priority,
            environment: bug.environment || '',
            testResultId: bug.testResultId || '',
            testRunId: bug.testRunId || '',
            assigneeId: bug.assigneeId || '',
            customFields: bug.customFields ?? {},
          });
          this.cdr.detectChanges();
        }
      });
    }
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.dirty = false;
    this.form.markAsPristine();
    this.saving = true;

    const value = this.form.value;
    // Saving by name may register a new environment, so the cached list is stale afterwards.
    this.environmentApi.invalidate(this.projectId);
    if (this.isEdit) {
      this.store.dispatch(
        BugReportActions.updateBugReport({
          projectId: this.projectId,
          id: this.bugId,
          request: {
            title: value.title!,
            description: value.description || undefined,
            stepsToReproduce: value.stepsToReproduce || undefined,
            expectedBehavior: value.expectedBehavior || undefined,
            actualBehavior: value.actualBehavior || undefined,
            priority: value.priority! as Priority,
            environment: value.environment || undefined,
            testResultId: value.testResultId || undefined,
            testRunId: value.testRunId || undefined,
            assigneeId: value.assigneeId || undefined,
            customFields: value.customFields ?? undefined,
          },
        })
      );
    } else {
      this.store.dispatch(
        BugReportActions.createBugReport({
          projectId: this.projectId,
          request: {
            title: value.title!,
            description: value.description || undefined,
            stepsToReproduce: value.stepsToReproduce || undefined,
            expectedBehavior: value.expectedBehavior || undefined,
            actualBehavior: value.actualBehavior || undefined,
            priority: value.priority! as Priority,
            environment: value.environment || undefined,
            testResultId: value.testResultId || undefined,
            testRunId: value.testRunId || undefined,
            assigneeId: value.assigneeId || undefined,
            exploratorySessionId: this.exploratorySessionId ?? undefined,
            stepResultId: this.stepResultId ?? undefined,
            customFields: value.customFields ?? undefined,
          },
          files: this.queuedFiles,
        })
      );
    }
  }


  /**
   * PRD-045: the project's template fills what is still empty, never what came from a test run or
   * a session note. Editing a bug never re-applies it. Marked pristine: nobody typed it.
   */
  private applyTemplate(project: Project): void {
    const controls = this.form.controls;
    const fill = (control: typeof controls.description, template: string | null) => {
      if (template && !control.value) {
        control.setValue(template);
      }
    };
    fill(controls.description, project.bugTemplateDescription);
    fill(controls.stepsToReproduce, project.bugTemplateSteps);
    fill(controls.environment, project.bugTemplateEnvironment);
    this.form.markAsPristine();
    this.cdr.detectChanges();
  }

  /** Edit mode uploads straight to the bug; a new bug queues its files until it is saved. */
  get attachmentsUrl(): string | null {
    return this.isEdit ? this.bugReportApi.attachmentsUrl(this.projectId, this.bugId) : null;
  }

  hasUnsavedChanges(): boolean {
    // form.dirty covers every field; dirty covers what lives outside the form controls and
    // re-arms the guard after a failed save. Queued files are lost on leaving, so they count too.
    return (this.form.dirty || this.dirty || this.queuedFiles.length > 0) && !this.saving;
  }
}
