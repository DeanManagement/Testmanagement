import { Routes } from '@angular/router';
import { TestRunListComponent } from './test-run-list/test-run-list.component';
import { TestRunFormComponent } from './test-run-form/test-run-form.component';
import { TestRunDetailComponent } from './test-run-detail/test-run-detail.component';

export const testRunsRoutes: Routes = [
  { path: '', component: TestRunListComponent },
  { path: 'new', component: TestRunFormComponent },
  { path: ':runId', component: TestRunDetailComponent },
];
