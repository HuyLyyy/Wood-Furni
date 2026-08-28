import apiClient from './apiClient';

/**
 * Matches backend dto/AuthResponse.java:
 *   { accessToken, refreshToken, tokenType, expiresIn, user: UserSummary }
 * And the wrapper ApiResponse: { success, message, data, timestamp }.
 *
 * apiClient unwraps the envelope and returns response.data.data directly
 * (because we use an axios response interceptor? — no, we don't. Callers
 * rely on the fact that axios returns the full body; we surface data.data
 * here so the call site is clean).
 */

export const authApi = {
    register: (payload) =>
        apiClient.post('/auth/register', payload).then((r) => r.data.data),

    login: (payload) =>
        apiClient.post('/auth/login', payload).then((r) => r.data.data),

    refresh: (refreshToken) =>
        apiClient.post('/auth/refresh', { refreshToken }).then((r) => r.data.data),

    me: () => apiClient.get('/auth/me').then((r) => r.data.data),

    logout: () => apiClient.post('/auth/logout').then((r) => r.data),
};