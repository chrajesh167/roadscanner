import { authApi, withoutAuthRefresh } from '../client';
import type { AuthTokensResponse, LoginRequest } from '../types';

/**
 * auth-service — `adapter/in/rest/{login,token}`.
 *
 * The console needs sign-in and sign-out only. It deliberately does not expose registration: an
 * admin account is granted by `POST /api/v1/auth/roles`, which an existing admin performs — a
 * console that could mint its own administrators would be a privilege-escalation path.
 */
export const authEndpoints = {
  /** POST /api/v1/auth/login */
  async login(body: LoginRequest): Promise<AuthTokensResponse> {
    const { data } = await authApi.post<AuthTokensResponse>(
      '/api/v1/auth/login',
      body,
      withoutAuthRefresh(),
    );
    return data;
  },

  /** POST /api/v1/auth/logout — revokes this session only. */
  async logout(refreshToken: string): Promise<void> {
    await authApi.post('/api/v1/auth/logout', { refreshToken }, withoutAuthRefresh());
  },

  /** POST /api/v1/auth/logout-all — revokes every session for the caller. */
  async logoutAll(): Promise<void> {
    await authApi.post('/api/v1/auth/logout-all', {}, withoutAuthRefresh());
  },
};
