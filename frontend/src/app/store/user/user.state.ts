import { EntityState, createEntityAdapter } from '@ngrx/entity';
import { User } from '../../shared/models/user.model';

export const userAdapter = createEntityAdapter<User>();

export interface UserState extends EntityState<User> {
  loading: boolean;
  error: string | null;
}

export const initialUserState: UserState = userAdapter.getInitialState({
  loading: false,
  error: null,
});
