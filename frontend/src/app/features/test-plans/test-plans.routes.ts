import { Routes } from '@angular/router';
import { TestPlanListComponent } from './test-plan-list/test-plan-list.component';
import { TestPlanFormComponent } from './test-plan-form/test-plan-form.component';
import { TestPlanDetailComponent } from './test-plan-detail/test-plan-detail.component';
import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';

export const testPlansRoutes: Routes = [
  { path: '', component: TestPlanListComponent },
  { path: 'new', component: TestPlanFormComponent, canDeactivate: [unsavedChangesGuard] },
  { path: ':planId', component: TestPlanDetailComponent },
  { path: ':planId/edit', component: TestPlanFormComponent, canDeactivate: [unsavedChangesGuard] },
];
