import { Routes } from '@angular/router';
import { TestRunListComponent } from './test-run-list/test-run-list.component';
import { TestRunFormComponent } from './test-run-form/test-run-form.component';
import { TestRunDetailComponent } from './test-run-detail/test-run-detail.component';
import { TestRunReportComponent } from './test-run-report/test-run-report.component';
import { AllureReportViewerComponent } from './allure-report-viewer/allure-report-viewer.component';
import { TestRunCompareComponent } from './test-run-compare/test-run-compare.component';

export const testRunsRoutes: Routes = [
  { path: '', component: TestRunListComponent },
  { path: 'new', component: TestRunFormComponent },
  // Before ':runId', which would otherwise take "compare" for a run id.
  { path: 'compare', component: TestRunCompareComponent },
  { path: ':runId', component: TestRunDetailComponent },
  { path: ':runId/report', component: TestRunReportComponent },
  { path: ':runId/allure-report', component: AllureReportViewerComponent },
];
