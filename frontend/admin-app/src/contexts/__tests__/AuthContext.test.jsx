/**
 * TC-FE-AUTH-01b / TC-FE-AUTH-02b — admin-app AuthContext.login
 *
 * Tương tự customer-app nhưng có thêm invariant quan trọng: tài khoản
 * CUSTOMER bị reject ngay cả khi backend trả 200 (admin app chỉ cho staff).
 */
import { describe, it, expect, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { API_BASE, ok, err } from '../../test/handlers.js';
import { server } from '../../test/server.js';
import { renderWithProviders, waitFor } from '../../test/render.jsx';
import { tokenStorage } from '../../services/apiClient.js';
import { AuthProvider, useAuth } from '../../contexts/AuthContext.jsx';

function LoginProbe({ email, password, onResult }) {
    const auth = useAuth();
    return (
        <button
            data-testid="login-btn"
            onClick={async () => {
                try {
                    const user = await auth.login(email, password);
                    onResult({ ok: true, user });
                } catch (e) {
                    onResult({ ok: false, error: e });
                }
            }}
        >
            login
        </button>
    );
}

describe('AuthContext.login (admin-app)', () => {
    it('TC-FE-AUTH-01b: login staff (ADMIN) thành công → lưu token + user', async () => {
        const fake = {
            accessToken: 'admin.access',
            refreshToken: 'admin.refresh',
            tokenType: 'Bearer',
            expiresIn: 900,
            user: { id: 'u-99', email: 'admin@x.com', fullName: 'Admin', role: 'ADMIN' },
        };
        server.use(
            http.post(`${API_BASE}/auth/login`, () => ok(fake))
        );

        // Đăng ký /auth/me trả 200 + /auth/login MSW sẽ chạm tới MSW vẫn
        // log warning unhandled nếu không có. AuthProvider bootstrap gọi
        // /auth/me — handler 401 mặc định trong handlers.js đủ xài, nhưng
        // ta override cho rõ ràng.
        server.use(
            http.get(`${API_BASE}/auth/me`, () => ok(fake.user))
        );

        let captured = null;
        const { getByTestId } = renderWithProviders(
            <AuthProvider>
                <LoginProbe
                    email="admin@x.com"
                    password="pw"
                    onResult={(r) => (captured = r)}
                />
            </AuthProvider>
        );

        // Bootstrap xong: chưa có token, AuthProvider early-return, không
        // gọi /auth/me. Đợi loading settle (qua access === null) trước khi
        // click để tránh act() warning.
        await waitFor(() => {
            expect(tokenStorage.getAccess()).toBeNull();
        });
        getByTestId('login-btn').click();

        await vi.waitFor(() => {
            expect(captured?.ok).toBe(true);
        });

        expect(tokenStorage.getAccess()).toBe('admin.access');
        expect(tokenStorage.getUser().role).toBe('ADMIN');
    });

    it('TC-FE-AUTH-02b: login CUSTOMER (đúng mk) vẫn bị reject — không lưu token', async () => {
        // Backend 200 với role CUSTOMER — admin app phải cản lại ở FE.
        const fake = {
            accessToken: 'should-not-be-saved',
            refreshToken: 'should-not-be-saved',
            tokenType: 'Bearer',
            expiresIn: 900,
            user: { id: 'u-2', email: 'cust@x.com', fullName: 'Cust', role: 'CUSTOMER' },
        };
        server.use(
            http.post(`${API_BASE}/auth/login`, () => ok(fake))
        );

        // /auth/me mặc định trả 401 — không cần override.
        let captured = null;
        const { getByTestId } = renderWithProviders(
            <AuthProvider>
                <LoginProbe
                    email="cust@x.com"
                    password="pw"
                    onResult={(r) => (captured = r)}
                />
            </AuthProvider>
        );
        await waitFor(() => {
            // Bootstrap xong: tokenStorage đã bị clear
            expect(tokenStorage.getAccess()).toBeNull();
        });
        getByTestId('login-btn').click();

        await vi.waitFor(() => {
            expect(captured?.ok).toBe(false);
        });

        // Critically: token KHÔNG được persist khi role bị FE loại.
        expect(tokenStorage.getAccess()).toBeNull();
        expect(tokenStorage.getRefresh()).toBeNull();
        expect(tokenStorage.getUser()).toBeNull();
        expect(captured.error.message).toMatch(/quyền|admin|nhân viên/i);
    });

    it('TC-FE-AUTH-03b: login 401 → không lưu token, error.message bubble lên UI', async () => {
        server.use(
            http.post(`${API_BASE}/auth/login`, () =>
                HttpResponse.json(
                    { success: false, message: 'Sai thông tin đăng nhập', data: null, errors: null, timestamp: '' },
                    { status: 401 }
                )
            )
        );

        let captured = null;
        const { getByTestId } = renderWithProviders(
            <AuthProvider>
                <LoginProbe
                    email="x@x.com"
                    password="bad"
                    onResult={(r) => (captured = r)}
                />
            </AuthProvider>
        );
        await waitFor(() => expect(tokenStorage.getAccess()).toBeNull());
        getByTestId('login-btn').click();

        await vi.waitFor(() => expect(captured?.ok).toBe(false));

        expect(tokenStorage.getAccess()).toBeNull();
        expect(captured.error.message).toMatch(/sai|đăng nhập/i);
    });
});
