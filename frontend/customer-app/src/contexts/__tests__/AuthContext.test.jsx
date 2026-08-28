/**
 * TC-FE-AUTH-01 — login thành công: token + user phải được persist vào
 * localStorage và context state được cập nhật.
 *
 * TC-FE-AUTH-02 — login thất bại: không được persist bất kỳ token nào;
 * context state vẫn ở trạng thái chưa xác thực; lỗi phải bubble lên caller.
 */
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { http } from 'msw';
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

describe('AuthContext.login (customer-app)', () => {
    beforeEach(() => {
        // Default: /auth/me 401 (no session). Each test overrides handlers explicitly.
        server.resetHandlers();
    });

    it('TC-FE-AUTH-01: login thành công lưu access/refresh token + user', async () => {
        const fakeAuthResponse = {
            accessToken: 'fake.access.token',
            refreshToken: 'fake.refresh.token',
            tokenType: 'Bearer',
            expiresIn: 900,
            user: {
                id: 'u-1',
                email: 'alice@example.com',
                fullName: 'Alice',
                role: 'CUSTOMER',
            },
        };

        server.use(
            http.post(`${API_BASE}/auth/login`, () => ok(fakeAuthResponse))
        );

        let captured = null;
        const { getByTestId } = renderWithProviders(
            <AuthProvider>
                <LoginProbe
                    email="alice@example.com"
                    password="correct-password"
                    onResult={(r) => (captured = r)}
                />
            </AuthProvider>
        );

        // Đợi AuthProvider bootstrap settle (accessToken null → skip
        // /auth/me, no race), sau đó click.
        await waitFor(() => {
            expect(tokenStorage.getAccess()).toBeNull();
        });
        getByTestId('login-btn').click();

        await vi.waitFor(() => {
            expect(captured).not.toBeNull();
            expect(captured.ok).toBe(true);
        });

        // localStorage phải chứa đầy đủ token + user (theo tokenStorage contract).
        expect(tokenStorage.getAccess()).toBe('fake.access.token');
        expect(tokenStorage.getRefresh()).toBe('fake.refresh.token');
        expect(tokenStorage.getUser()).toMatchObject({
            id: 'u-1',
            email: 'alice@example.com',
        });

        // Context state phải phản ánh user vừa login
        const ctx = captured.user;
        expect(ctx).toMatchObject({ id: 'u-1', email: 'alice@example.com' });
    });

    it('TC-FE-AUTH-02: login sai mật khẩu KHÔNG lưu token, reject promise', async () => {
        server.use(
            http.post(`${API_BASE}/auth/login`, () =>
                err(401, 'Email hoặc mật khẩu không đúng')
            )
        );

        let captured = null;
        const { getByTestId } = renderWithProviders(
            <AuthProvider>
                <LoginProbe
                    email="alice@example.com"
                    password="wrong-password"
                    onResult={(r) => (captured = r)}
                />
            </AuthProvider>
        );

        getByTestId('login-btn').click();

        await vi.waitFor(() => {
            expect(captured).not.toBeNull();
            expect(captured.ok).toBe(false);
        });

        // Quan trọng: KHÔNG lưu gì vào storage khi login fail.
        expect(tokenStorage.getAccess()).toBeNull();
        expect(tokenStorage.getRefresh()).toBeNull();
        expect(tokenStorage.getUser()).toBeNull();

        // Phải có message từ backend để UI có thể hiển thị.
        expect(captured.error.message).toMatch(/email|mật khẩu/i);
    });
});
