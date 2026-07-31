export interface AuthUser {
  id: string;
  email: string;
  displayName: string;
  systemAdmin: boolean;
}

export interface LoginResponse {
  token: string;
  user: AuthUser;
  forcePasswordChange: boolean;
}
