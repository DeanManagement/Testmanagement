import { Component, DestroyRef, inject, Input, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { forkJoin } from 'rxjs';
import { take } from 'rxjs/operators';
import { BuildServerApiService } from '../../../core/services/build-server-api.service';
import { ProjectWorkflow } from '../../../shared/models/build-server.model';

/**
 * Project admins choose which workflows testers are offered in the Automation panel, out of every
 * workflow on a build server a system administrator has made available to this project.
 */
@Component({
  selector: 'app-project-workflow-settings',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatSelectModule, TranslateModule],
  template: `
    <div class="project-workflows" data-test-id="project-workflow-settings">
      <h4>{{ 'automation.projectSettings.title' | translate }}</h4>
      <p class="description">{{ 'automation.projectSettings.description' | translate }}</p>
      @if (available().length === 0) {
        <p class="description" data-test-id="project-workflows-none">
          {{ 'automation.projectSettings.noneAvailable' | translate }}
        </p>
      } @else {
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>{{ 'automation.projectSettings.offered' | translate }}</mat-label>
          <mat-select multiple [(ngModel)]="selectedIds" data-test-id="project-workflows-select">
            @for (workflow of available(); track workflow.id) {
              <mat-option [value]="workflow.id">{{ workflow.name }} · {{ workflow.serverName }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <div class="actions">
          @if (saved()) {
            <span class="saved" role="status">{{ 'common.savedSuccessfully' | translate }}</span>
          }
          <button mat-stroked-button type="button" (click)="save()" data-test-id="project-workflows-save">
            {{ 'common.save' | translate }}
          </button>
        </div>
      }
    </div>
  `,
  styles: [`
    .full-width { width: 100%; display: block; }
    .description { font-size: 13px; color: var(--tm-text-secondary); }
    .actions { display: flex; justify-content: flex-end; align-items: center; gap: 12px; }
    .saved { font-size: 13px; color: var(--tm-text-secondary); }
  `],
})
export class ProjectWorkflowSettingsComponent implements OnInit {
  private readonly api = inject(BuildServerApiService);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) projectId!: string;

  readonly available = signal<ProjectWorkflow[]>([]);
  readonly saved = signal(false);
  selectedIds: string[] = [];

  ngOnInit(): void {
    forkJoin({
      available: this.api.getAvailableWorkflows(this.projectId),
      offered: this.api.getProjectWorkflows(this.projectId),
    }).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe(({ available, offered }) => {
        this.selectedIds = offered.map((workflow) => workflow.id);
        this.available.set(available);
      });
  }

  save(): void {
    this.saved.set(false);
    this.api.setProjectWorkflows(this.projectId, this.selectedIds)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.saved.set(true));
  }
}
