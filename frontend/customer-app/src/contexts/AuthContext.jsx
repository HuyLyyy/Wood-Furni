import { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { authApi } from '../services/apiAuth.js';
import { tokenStorage } from '../services/apiClient.js';

const AuthContext = createContext(null);

/**
 * AuthProvider
 *
 * State machine:
 *   initial → (no token) → unauthenticated
 *   initial → (token present) → fetch /auth/me → authenticated | unauthenticated
 *
 * Persists { accessToken, refreshToken, user } via tokenStorage (localStorage).
 *
 * NOTE: /auth/me is best-effort. If the call fails (network, expired token
 * that the refresh interceptor couldn't recover), we silently log the user out
 * — the API client will already have redirected to /login on 401.
 */
export function AuthProvider({ children }) {
    const [user, setUser] = useState(() => tokenStorage.getUser());
    const [loading, setLoading] = useState(true);
    const [isAuthenticated, setIsAuthenticated] = useState(
        Boolean(tokenStorage.getAccess() && tokenStorage.getUser())
    );

    // On first mount: if we have a token in localStorage, validate it by
    // hitting /auth/me. Restore user state on success, clear on failure.
    useEffect(() => {
        let cancelled = false;

        async function bootstrap() {
            const accessToken = tokenStorage.getAccess();
            if (!accessToken) {
                setLoading(false);
                return;
            }

            try {
                const me = await authApi.me();
                if (!cancelled) {
                    tokenStorage.setUser(me);
                    setUser(me);
                    setIsAuthenticated(true);
                }
            } catch {
                if (!cancelled) {
                    tokenStorage.clear();
                    setUser(null);
                    setIsAuthenticated(false);
                }
            } finally {
                if (!cancelled) setLoading(false);
            }
        }

        bootstrap();
        return () => {
            cancelled = true;
        };
    }, []);

    // -------- public actions --------

    const login = useCallback(async (email, password) => {
        const authResponse = await authApi.login({ email, password });
        // authResponse = { accessToken, refreshToken, tokenType, expiresIn, user }
        tokenStorage.setAccess(authResponse.accessToken);
        tokenStorage.setRefresh(authResponse.refreshToken);
        tokenStorage.setUser(authResponse.user);
        setUser(authResponse.user);
        setIsAuthenticated(true);
        return authResponse.user;
    }, []);

    const register = useCallback(async ({ email, password, fullName, phone, otpToken }) => {
        const authResponse = await authApi.register({ email, password, fullName, phone, otpToken });
        tokenStorage.setAccess(authResponse.accessToken);
        tokenStorage.setRefresh(authResponse.refreshToken);
        tokenStorage.setUser(authResponse.user);
        setUser(authResponse.user);
        setIsAuthenticated(true);
        return authResponse.user;
    }, []);

    const logout = useCallback(async () => {
        try {
            await authApi.logout();
        } catch {
            // Even if the server call fails, clear local state.
        }
        tokenStorage.clear();
        setUser(null);
        setIsAuthenticated(false);
    }, []);

    const value = {
        user,
        isAuthenticated,
        loading,
        login,
        register,
        logout,
    };

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/**
 * Hook to consume the auth context.
 * Throws a clear error if used outside of <AuthProvider> — easier to debug
 * than an undefined-context silent failure.
 */
export function useAuth() {
    const ctx = useContext(AuthContext);
    if (!ctx) {
        throw new Error('useAuth must be used inside <AuthProvider>');
    }
    return ctx;
}