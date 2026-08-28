/**
 * Shared MSW (Mock Service Worker) request handlers for customer-app tests.
 *
 * Tests import the handlers they need; the generic 404 passthrough ensures
 * unexpected requests surface as a clear failure instead of silently
 * returning nothing.
 */
import { http, HttpResponse } from 'msw';

export const API_BASE =
    import.meta.env.VITE_API_BASE_URL || 'http://localhost:3001/api/v1';

/** A reusable "successful ApiResponse envelope" — matches backend common.ApiResponse */
export const ok = (data, message = 'OK') =>
    HttpResponse.json({
        success: true,
        message,
        data,
        errors: null,
        timestamp: new Date().toISOString(),
    });

export const err = (status, message, errors = null) =>
    HttpResponse.json(
        {
            success: false,
            message,
            data: null,
            errors,
            timestamp: new Date().toISOString(),
        },
        { status }
    );

/** Common handlers — tests can `handlers.push(...)` to extend. */
export const handlers = [
    /**
     * Default handler for /auth/me — AuthContext's bootstrap effect
     * (`fetch /auth/me` on mount) always issues this call when a token
     * is present in localStorage. Returning 401 lets the AuthContext
     * silently clear the session. Individual tests that need a valid
     * session can override via `server.use(...)`.
     */
    http.get(`${API_BASE}/auth/me`, () => err(401, 'Unauthenticated')),

    /**
     * Default handler for /auth/refresh — apiClient's 401 interceptor
     * falls back to a refresh attempt. We return 401 so the interceptor
     * takes the force-logout path. Tests that exercise the refresh flow
     * override this.
     */
    http.post(`${API_BASE}/auth/refresh`, () =>
        err(401, 'Refresh failed')
    ),
];
