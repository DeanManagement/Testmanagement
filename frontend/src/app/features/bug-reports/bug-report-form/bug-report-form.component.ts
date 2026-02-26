import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { BugReportActions } from '../../../store/bug-report/bug-report.actions';
import { selectBugReportById } from '../../../store/bug-report/bug-report.selectors';
import { BugReportStatus, Priority } from '../../../shared/models/bug-report.model';
import { ProjectMemberApiService } from '../../../core/services/project-member-api.service';
import { ProjectMember } from '../../../shared/models/project-member.model';

@Component({
  selector: 'app-bug-report-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    TranslateModule,
  ],
  templateUrl: './bug-report-form.component.html',
  styleUrl: './bug-report-form.component.scss',
})
export class BugReportFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly memberApi = inject(ProjectMemberApiService);

  projectId = '';
  bugId = '';
  isEdit = false;
  members: ProjectMember[] = [];

  priorities: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
  statuses: BugReportStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'WONTFIX'];

  form = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(255)]],
    description: [''],
    stepsToReproduce: [''],
    expectedBehavior: [''],
    actualBehavior: [''],
    priority: ['HIGH' as Priority, [Validators.required]],
    status: ['OPEN' as BugReportStatus, [Validators.required]],
    environment: [''],
    testResultId: [''],
    testRunId: [''],
    assigneeId: [''],
  });

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.bugId = this.route.snapshot.paramMap.get('bugId') ?? '';
    this.isEdit = !!this.bugId;

    if (this.projectId) {
      this.memberApi.getByProject(this.projectId).subscribe((m) => (this.members = m));
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

    if (this.isEdit && this.projectId) {
      this.store.dispatch(BugReportActions.loadBugReport({ projectId: this.projectId, id: this.bugId }));
      this.store.select(selectBugReportById(this.bugId)).subscribe((bug) => {
        if (bug) {
          this.form.patchValue({
            title: bug.title,
            description: bug.description || '',
            stepsToReproduce: bug.stepsToReproduce || '',
            expectedBehavior: bug.expectedBehavior || '',
            actualBehavior: bug.actualBehavior || '',
            priority: bug.priority,
            status: bug.status,
            environment: bug.environment || '',
            testResultId: bug.testResultId || '',
            testRunId: bug.testRunId || '',
            assigneeId: bug.assigneeId || '',
          });
        }
      });
    }
  }

  onSubmit(): void {
    if (this.form.invalid) return;

    const value = this.form.value;
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
            status: value.status! as BugReportStatus,
            environment: value.environment || undefined,
            testResultId: value.testResultId || undefined,
            testRunId: value.testRunId || undefined,
            assigneeId: value.assigneeId || undefined,
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
          },
        })
      );
    }
  }
}
