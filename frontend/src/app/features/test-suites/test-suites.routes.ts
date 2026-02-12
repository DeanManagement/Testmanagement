import { Routes } from '@angular/router';
import { TestSuiteListComponent } from './test-suite-list/test-suite-list.component';
import { TestSuiteFormComponent } from './test-suite-form/test-suite-form.component';
import { TestSuiteDetailComponent } from './test-suite-detail/test-suite-detail.component';
import { TestSuiteReportComponent } from './test-suite-report/test-suite-report.component';

export const testSuitesRoutes: Routes = [
  { path: '', component: TestSuiteListComponent },
  { path: 'new', component: TestSuiteFormComponent },
  { path: ':suiteId', component: TestSuiteDetailComponent },
  { path: ':suiteId/edit', component: TestSuiteFormComponent },
  { path: ':suiteId/report', component: TestSuiteReportComponent },
];
