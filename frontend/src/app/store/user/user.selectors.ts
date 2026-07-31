import { createFeatureSelector, createSelector } from '@ngrx/store';
import { UserState, userAdapter } from './user.state';

export const selectUserState = createFeatureSelector<UserState>('users');

const { selectAll } = userAdapter.getSelectors();

export const selectAllUsers = createSelector(selectUserState, selectAll);

export const selectUsersLoading = createSelector(
  selectUserState,
  (state) => state.loading
);

export const selectUsersError = createSelector(
  selectUserState,
  (state) => state.error
);
