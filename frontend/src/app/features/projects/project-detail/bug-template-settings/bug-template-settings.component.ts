import { Component, DestroyRef, EventEmitter, inject, Input, OnChanges, Output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { take } from 'rxjs/operators';
import { ProjectApiService } from '../../../../core/services/project-api.service';
import { BugTemplateRequest, Project } from '../../../../shared/models/project.model';
import { EnvironmentInputComponent } from '../../../../shared/components/environment-input/environment-input.component';

/**
 * PRD-045: what a new bug report starts with. Project admins only; the form fills empty fields from
 * it on create, and MCP does the same for agents.
 */
@Component({
  selector: 'app-bug-template-settings',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, TranslateModule, EnvironmentInputComponent],
  template: `
    <div class="bug-template" data-test-id="bug-template-settings">
      <h4>{{ 'bugReport.template.title' | translate }}</h4>
      <p class="setting-description">{{ 'bugReport.template.description' | translate }}</p>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'bugReport.form.description' | translate }}</mat-label>
        <textarea matInput rows="3" [(ngModel)]="template.description" data-test-id="bug-template-description"></textarea>
      </mat-form-field>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'bugReport.form.stepsToReproduce' | translate }}</mat-label>
        <textarea matInput rows="3" [(ngModel)]="template.stepsToReproduce" data-test-id="bug-template-steps"></textarea>
      </mat-form-field>
      <app-environment-input class="full-width" [projectId]="project.id" labelKey="bugReport.template.environment"
                             testId="bug-template-environment" [(ngModel)]="template.environment" />
      <div class="actions">
        @if (saved()) {
          <span class="saved" role="status">{{ 'common.savedSuccessfully' | translate }}</span>
        }
        <button mat-stroked-button type="button" (click)="save()" data-test-id="bug-template-save">
          {{ 'common.save' | translate }}
        </button>
      </div>
    </div>
  `,
  styles: [`
    .full-width { width: 100%; display: block; }
    .actions { display: flex; justify-content: flex-end; align-items: center; gap: 12px; }
    .saved { font-size: 13px; color: var(--tm-text-secondary); }
  `],
})
export class BugTemplateSettingsComponent implements OnChanges {
  private readonly projectApi = inject(ProjectApiService);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) project!: Project;
  @Output() readonly changed = new EventEmitter<Project>();

  template: BugTemplateRequest = { description: '', stepsToReproduce: '', environment: '' };
  readonly saved = signal(false);

  ngOnChanges(): void {
    this.template = {
      description: this.project.bugTemplateDescription ?? '',
      stepsToReproduce: this.project.bugTemplateSteps ?? '',
      environment: this.project.bugTemplateEnvironment ?? '',
    };
  }

  save(): void {
    this.saved.set(false);
    this.projectApi.updateBugTemplate(this.project.id, this.template)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((project) => {
        this.saved.set(true);
        this.changed.emit(project);
      });
  }
}
