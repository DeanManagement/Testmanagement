import { Routes } from '@angular/router';
import { TestRunListComponent } from './test-run-list/test-run-list.component';
import { TestRunFormComponent } from './test-run-form/test-run-form.component';
import { TestRunDetailComponent } from './test-run-detail/test-run-detail.component';
import { TestRunReportComponent } from './test-run-report/test-run-report.component';
import { AllureReportViewerComponent } from './allure-report-viewer/allure-report-viewer.component';

export const testRunsRoutes: Routes = [
  { path: '', component: TestRunListComponent },
  { path: 'new', component: TestRunFormComponent },
  { path: ':runId', component: TestRunDetailComponent },
  { path: ':runId/report', component: TestRunReportComponent },
  { path: ':runId/allure-report', component: AllureReportViewerComponent },
];
