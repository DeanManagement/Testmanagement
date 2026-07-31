import { Routes } from '@angular/router';
import { TestCaseListComponent } from './test-case-list/test-case-list.component';
import { TestCaseFormComponent } from './test-case-form/test-case-form.component';
import { TestCaseDetailComponent } from './test-case-detail/test-case-detail.component';
import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';

export const testCasesRoutes: Routes = [
  { path: '', component: TestCaseListComponent },
  { path: 'new', component: TestCaseFormComponent, canDeactivate: [unsavedChangesGuard] },
  { path: ':tcId', component: TestCaseDetailComponent },
  { path: ':tcId/edit', component: TestCaseFormComponent, canDeactivate: [unsavedChangesGuard] },
];
