import { Routes } from '@angular/router';

export const settingsRoutes: Routes = [
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
];
