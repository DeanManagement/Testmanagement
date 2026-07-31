import { Routes } from '@angular/router';
import { BugReportListComponent } from './bug-report-list/bug-report-list.component';
import { BugReportFormComponent } from './bug-report-form/bug-report-form.component';
import { BugReportDetailComponent } from './bug-report-detail/bug-report-detail.component';
import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';

export const bugReportsRoutes: Routes = [
  { path: '', component: BugReportListComponent },
  { path: 'new', component: BugReportFormComponent, canDeactivate: [unsavedChangesGuard] },
  { path: ':bugId', component: BugReportDetailComponent },
  { path: ':bugId/edit', component: BugReportFormComponent, canDeactivate: [unsavedChangesGuard] },
];
