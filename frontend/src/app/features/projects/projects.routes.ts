import { Routes } from '@angular/router';
import { ProjectListComponent } from './project-list/project-list.component';
import { ProjectFormComponent } from './project-form/project-form.component';
import { ProjectDetailComponent } from './project-detail/project-detail.component';
import { ActivityFeedComponent } from './activity-feed/activity-feed.component';
import { ProjectDashboardComponent } from './project-dashboard/project-dashboard.component';
import { WebhookSettingsComponent } from '../webhooks/webhook-settings.component';
import { IssueTrackerSettingsComponent } from '../issue-tracker/issue-tracker-settings.component';
import { RequirementsComponent } from '../requirements/requirements.component';
import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';

export const projectsRoutes: Routes = [
  { path: '', component: ProjectListComponent },
  { path: 'new', component: ProjectFormComponent, canDeactivate: [unsavedChangesGuard] },
  { path: ':id', component: ProjectDetailComponent },
  { path: ':id/edit', component: ProjectFormComponent, canDeactivate: [unsavedChangesGuard] },
  { path: ':id/activity', component: ActivityFeedComponent },
  { path: ':id/dashboard', component: ProjectDashboardComponent },
  { path: ':id/webhooks', component: WebhookSettingsComponent },
  { path: ':id/issue-tracker', component: IssueTrackerSettingsComponent },
  { path: ':id/requirements', component: RequirementsComponent },
  {
    path: ':id/test-cases',
    loadChildren: () =>
      import('../test-cases/test-cases.routes').then((m) => m.testCasesRoutes),
  },
  {
    path: ':id/test-suites',
    loadChildren: () =>
      import('../test-suites/test-suites.routes').then((m) => m.testSuitesRoutes),
  },
  {
    path: ':id/test-runs',
    loadChildren: () =>
      import('../test-runs/test-runs.routes').then((m) => m.testRunsRoutes),
  },
  {
    path: ':id/test-plans',
    loadChildren: () =>
      import('../test-plans/test-plans.routes').then((m) => m.testPlansRoutes),
  },
  {
    path: ':id/bug-reports',
    loadChildren: () =>
      import('../bug-reports/bug-reports.routes').then((m) => m.bugReportsRoutes),
  },
];
