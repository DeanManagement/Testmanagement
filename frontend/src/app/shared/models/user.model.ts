export interface User {
  id: string;
  email: string;
  displayName: string;
  systemAdmin: boolean;
  createdAt: string;
}

export interface CreateUserRequest {
  email: string;
  displayName: string;
  password: string;
  systemAdmin: boolean;
}

export interface UpdateUserRequest {
  displayName: string;
  systemAdmin?: boolean;
  password?: string;
}
