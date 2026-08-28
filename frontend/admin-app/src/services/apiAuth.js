import apiClient from './apiClient';

/**
 * Auth API — same backend contract as customer-app, but the FE rejects
 * CUSTOMER role accounts at the LoginPage level (see features/auth/LoginPage).
 *
 *   POST /auth/login   body: { email, password } → AuthResponse
 *   GET  /auth/me                              → UserSummary
 *   POST /auth/refresh body: { refreshToken }  → AuthResponse
 *   POST /auth/logout                         → ApiResponse<void>
 */
export const authApi = {
    login: (payload) => apiClient.post('/auth/login', payload).then((r) => r.data.data),
    me: () => apiClient.get('/auth/me').then((r) => r.data.data),
    refresh: (refreshToken) => apiClient.post('/auth/refresh', { refreshToken }).then((r) => r.data.data),
    logout: () => apiClient.post('/auth/logout').then((r) => r.data),
};