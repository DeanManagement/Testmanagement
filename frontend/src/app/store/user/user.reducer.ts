import { createReducer, on } from '@ngrx/store';
import { UserActions } from './user.actions';
import { userAdapter, initialUserState } from './user.state';

export const userReducer = createReducer(
  initialUserState,

  on(UserActions.loadUsers, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(UserActions.loadUsersSuccess, (state, { users }) =>
    userAdapter.setAll(users, { ...state, loading: false })
  ),

  on(UserActions.loadUsersFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  on(UserActions.createUserSuccess, (state, { user }) =>
    userAdapter.addOne(user, state)
  ),

  on(UserActions.createUserFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(UserActions.updateUserSuccess, (state, { user }) =>
    userAdapter.upsertOne(user, state)
  ),

  on(UserActions.updateUserFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(UserActions.deleteUserSuccess, (state, { id }) =>
    userAdapter.removeOne(id, state)
  ),

  on(UserActions.deleteUserFailure, (state, { error }) => ({
    ...state,
    error,
  }))
);
