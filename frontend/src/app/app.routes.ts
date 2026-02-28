import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { adminGuard } from './core/guards/admin.guard';
import { ShellComponent } from './core/components/shell/shell.component';

export const routes: Routes = [
  // Public routes — no shell
  {
    path: '',
    pathMatch: 'full',
    loadChildren: () =>
      import('./features/landing/landing.routes').then((m) => m.landingRoutes),
  },
  {
    path: 'login',
    loadChildren: () =>
      import('./features/login/login.routes').then((m) => m.loginRoutes),
  },
  // Authenticated routes — wrapped in ShellComponent
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadChildren: () =>
          import('./features/dashboard/dashboard.routes').then((m) => m.dashboardRoutes),
      },
      {
        path: 'projects',
        loadChildren: () =>
          import('./features/projects/projects.routes').then((m) => m.projectsRoutes),
      },
      {
        path: 'settings',
        canActivate: [adminGuard],
        loadChildren: () =>
          import('./features/settings/settings.routes').then((m) => m.settingsRoutes),
      },
      {
        path: 'my-bugs',
        loadChildren: () =>
          import('./features/my-bug-reports/my-bug-reports.routes').then((m) => m.myBugReportsRoutes),
      },
      {
        path: 'my-test-runs',
        loadChildren: () =>
          import('./features/my-test-runs/my-test-runs.routes').then((m) => m.myTestRunsRoutes),
      },
      {
        path: 'my-watched',
        loadChildren: () =>
          import('./features/my-watched/my-watched.routes').then((m) => m.myWatchedRoutes),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
