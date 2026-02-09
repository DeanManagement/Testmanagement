import { Routes } from '@angular/router';
import { ProjectListComponent } from './project-list/project-list.component';
import { ProjectFormComponent } from './project-form/project-form.component';
import { ProjectDetailComponent } from './project-detail/project-detail.component';

export const projectsRoutes: Routes = [
  { path: '', component: ProjectListComponent },
  { path: 'new', component: ProjectFormComponent },
  { path: ':id', component: ProjectDetailComponent },
  { path: ':id/edit', component: ProjectFormComponent },
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
];
