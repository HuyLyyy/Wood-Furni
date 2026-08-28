/**
 * Shared MSW request handlers for admin-app tests.
 */
import { http, HttpResponse } from 'msw';

export const API_BASE =
    import.meta.env.VITE_API_BASE_URL || 'http://localhost:3001/api/v1';

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

export const handlers = [
    /**
     * Default handler for /auth/me — AuthContext's bootstrap effect
     * (`fetch /auth/me` on mount) always issues this call when a token
     * is present in localStorage. Returning 401 lets the AuthContext
     * silently clear the session. Individual tests that need a valid
     * session can override via `server.use(...)`.
     */
    http.get(`${API_BASE}/auth/me`, () =>
        err(401, 'Unauthenticated')
    ),
];
