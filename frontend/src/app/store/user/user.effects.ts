import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { catchError, map, mergeMap } from 'rxjs/operators';
import { UserApiService } from '../../core/services/user-api.service';
import { UserActions } from './user.actions';

@Injectable()
export class UserEffects {
  private readonly actions$ = inject(Actions);
  private readonly userApi = inject(UserApiService);

  loadUsers$ = createEffect(() =>
    this.actions$.pipe(
      ofType(UserActions.loadUsers),
      mergeMap(() =>
        this.userApi.getAll().pipe(
          map((users) => UserActions.loadUsersSuccess({ users })),
          catchError((error) =>
            of(UserActions.loadUsersFailure({ error: error.message }))
          )
        )
      )
    )
  );

  createUser$ = createEffect(() =>
    this.actions$.pipe(
      ofType(UserActions.createUser),
      mergeMap(({ request }) =>
        this.userApi.create(request).pipe(
          map((user) => UserActions.createUserSuccess({ user })),
          catchError((error) =>
            of(UserActions.createUserFailure({ error: error.message }))
          )
        )
      )
    )
  );

  updateUser$ = createEffect(() =>
    this.actions$.pipe(
      ofType(UserActions.updateUser),
      mergeMap(({ id, request }) =>
        this.userApi.update(id, request).pipe(
          map((user) => UserActions.updateUserSuccess({ user })),
          catchError((error) =>
            of(UserActions.updateUserFailure({ error: error.message }))
          )
        )
      )
    )
  );

  deleteUser$ = createEffect(() =>
    this.actions$.pipe(
      ofType(UserActions.deleteUser),
      mergeMap(({ id }) =>
        this.userApi.delete(id).pipe(
          map(() => UserActions.deleteUserSuccess({ id })),
          catchError((error) =>
            of(UserActions.deleteUserFailure({ error: error.message }))
          )
        )
      )
    )
  );
}
