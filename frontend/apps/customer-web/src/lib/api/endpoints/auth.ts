import { authApi, withoutAuthRefresh } from '../client';
import type {
  AuthTokensResponse,
  LoginRequest,
  RegisterRequest,
  RequestPasswordResetRequest,
} from '../types';

/** auth-service — `adapter/in/rest/{login,registration,token,passwordreset}`. */
export const authEndpoints = {
  /** POST /api/v1/auth/register → 201 with tokens; the account is created signed-in. */
  async register(body: RegisterRequest): Promise<AuthTokensResponse> {
    const { data } = await authApi.post<AuthTokensResponse>(
      '/api/v1/auth/register',
      body,
      withoutAuthRefresh(),
    );
    return data;
  },

  /** POST /api/v1/auth/login */
  async login(body: LoginRequest): Promise<AuthTokensResponse> {
    const { data } = await authApi.post<AuthTokensResponse>(
      '/api/v1/auth/login',
      body,
      withoutAuthRefresh(),
    );
    return data;
  },

  /** POST /api/v1/auth/logout — revokes the presented refresh token only. */
  async logout(refreshToken: string): Promise<void> {
    await authApi.post('/api/v1/auth/logout', { refreshToken }, withoutAuthRefresh());
  },

  /** POST /api/v1/auth/logout-all — revokes every session for the authenticated user. */
  async logoutAll(): Promise<void> {
    await authApi.post('/api/v1/auth/logout-all', {}, withoutAuthRefresh());
  },

  /** POST /api/v1/auth/password-reset/request — always 2xx, never reveals account existence. */
  async requestPasswordReset(body: RequestPasswordResetRequest): Promise<void> {
    await authApi.post('/api/v1/auth/password-reset/request', body, withoutAuthRefresh());
  },
};
