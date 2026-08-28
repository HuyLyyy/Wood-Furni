import { createContext, useContext, useEffect, useState, useCallback } from 'react';
import { authApi } from '../services/apiAuth.js';
import { tokenStorage } from '../services/apiClient.js';
import { isStaffRole } from '../utils/roleMeta.js';

const AuthContext = createContext(null);

/**
 * AuthProvider (admin-app variant)
 *
 * Same lifecycle as customer-app's AuthProvider, but with one extra
 * guarantee: any account whose role resolves to CUSTOMER is considered
 * unauthorised for the admin app and we force a logout.
 *
 * On mount, if there's already a saved user and their role is CUSTOMER,
 * we clear localStorage and set isAuthenticated=false. This handles the
 * edge case where a user logged into the customer app, then opens the
 * admin app in the same browser (different localStorage namespaces, but
 * if they share via cookies/SSO later, we want to be defensive).
 */
export function AuthProvider({ children }) {
    const [user, setUser] = useState(() => tokenStorage.getUser());
    const [loading, setLoading] = useState(true);
    const [isAuthenticated, setIsAuthenticated] = useState(false);

    useEffect(() => {
        let cancelled = false;

        async function bootstrap() {
            const saved = tokenStorage.getUser();
            const access = tokenStorage.getAccess();

            // Defensive: kick out any CUSTOMER role if it somehow ended up here.
            if (saved && !isStaffRole(saved.role)) {
                tokenStorage.clear();
                setUser(null);
                setIsAuthenticated(false);
                setLoading(false);
                return;
            }

            if (!access) {
                setLoading(false);
                return;
            }

            try {
                const me = await authApi.me();
                if (cancelled) return;
                if (!isStaffRole(me.role)) {
                    tokenStorage.clear();
                    setUser(null);
                    setIsAuthenticated(false);
                    setLoading(false);
                    return;
                }
                tokenStorage.setUser(me);
                setUser(me);
                setIsAuthenticated(true);
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
        return () => { cancelled = true; };
    }, []);

    const login = useCallback(async (email, password) => {
        const authResponse = await authApi.login({ email, password });
        // Reject CUSTOMER role at the FE — we are the admin app.
        if (!isStaffRole(authResponse.user?.role)) {
            // Clear any tokens we accidentally received
            tokenStorage.clear();
            throw new Error('Tài khoản này không có quyền truy cập Admin Console. Vui lòng dùng tài khoản nhân viên.');
        }
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
            // ignore
        }
        tokenStorage.clear();
        setUser(null);
        setIsAuthenticated(false);
    }, []);

    return (
        <AuthContext.Provider value={{ user, isAuthenticated, loading, login, logout }}>
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth() {
    const ctx = useContext(AuthContext);
    if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
    return ctx;
}