import { Routes } from '@angular/router';
import { SettingsLayoutComponent } from './settings-layout/settings-layout.component';

export const settingsRoutes: Routes = [
  {
    path: '',
    component: SettingsLayoutComponent,
    children: [
      {
        path: '',
        redirectTo: 'api-keys',
        pathMatch: 'full',
      },
      {
        path: 'api-keys',
        loadComponent: () =>
          import('./api-key-list/api-key-list.component').then(
            (m) => m.ApiKeyListComponent
          ),
      },
      {
        path: 'users',
        loadComponent: () =>
          import('./user-list/user-list.component').then(
            (m) => m.UserListComponent
          ),
      },
    ],
  },
];
