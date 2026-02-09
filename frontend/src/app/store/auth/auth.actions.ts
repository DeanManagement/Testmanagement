import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { AuthUser } from '../../core/models/auth.model';

export const AuthActions = createActionGroup({
  source: 'Auth',
  events: {
    'Login': emptyProps(),
    'Login Success': props<{ user: AuthUser }>(),
    'Login Failure': props<{ error: string }>(),
    'Logout': emptyProps(),
    'Logout Success': emptyProps(),
  },
});
