import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { filter, map, take, switchMap } from 'rxjs/operators';
import { selectAuthInitialized } from '../../store/auth/auth.selectors';
import { selectIsSystemAdmin } from '../../store/auth/auth.selectors';

export const adminGuard: CanActivateFn = () => {
  const store = inject(Store);
  const router = inject(Router);

  return store.select(selectAuthInitialized).pipe(
    filter((initialized) => initialized),
    take(1),
    switchMap(() => store.select(selectIsSystemAdmin).pipe(take(1))),
    map((isAdmin) => {
      if (isAdmin) {
        return true;
      }
      return router.createUrlTree(['/dashboard']);
    })
  );
};
