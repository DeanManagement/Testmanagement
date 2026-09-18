import { Routes } from '@angular/router';
import { SessionListComponent } from './session-list.component';
import { SessionDetailComponent } from './session-detail.component';

export const exploratorySessionsRoutes: Routes = [
  { path: '', component: SessionListComponent },
  { path: ':sessionId', component: SessionDetailComponent },
];
