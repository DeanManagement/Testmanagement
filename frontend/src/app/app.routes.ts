import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { adminGuard } from './core/guards/admin.guard';
import { ShellComponent } from './core/components/shell/shell.component';
import { NotificationSettingsComponent } from './features/notification-settings/notification-settings.component';

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
  {
    // Public and outside the shell: the browser lands here straight from the IdP, before any
    // token exists (PRD-012 §3.3).
    path: 'login/callback',
    loadComponent: () =>
      import('./features/login/sso-callback.component').then((m) => m.SsoCallbackComponent),
  },
  {
    path: 'change-password',
    canActivate: [authGuard],
    loadChildren: () =>
      import('./features/change-password/change-password.routes').then((m) => m.changePasswordRoutes),
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
      {
        path: 'notification-settings',
        component: NotificationSettingsComponent,
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
