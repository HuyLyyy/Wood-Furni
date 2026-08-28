import axios from 'axios';
import toast from 'react-hot-toast';

/**
 * Admin-app API client.
 *
 * NOTE: This is intentionally a duplicate of customer-app's apiClient.js,
 * not a shared import. Each app keeps its own localStorage namespace
 * (woodfurni_admin_*) so a user can be logged into both apps simultaneously
 * without them colliding.
 *
 * Token refresh + 401 redirect logic is identical to customer-app; we keep
 * the implementation duplicated so the admin app stays self-contained and
 * can evolve independently (e.g. different logout cleanup, different WS URL).
 */

const ACCESS_TOKEN_KEY = 'woodfurni_admin_access_token';
const REFRESH_TOKEN_KEY = 'woodfurni_admin_refresh_token';
const USER_KEY = 'woodfurni_admin_user';

export const tokenStorage = {
    getAccess: () => localStorage.getItem(ACCESS_TOKEN_KEY),
    getRefresh: () => localStorage.getItem(REFRESH_TOKEN_KEY),
    getUser: () => {
        try {
            const raw = localStorage.getItem(USER_KEY);
            return raw ? JSON.parse(raw) : null;
        } catch {
            return null;
        }
    },
    setAccess: (token) => localStorage.setItem(ACCESS_TOKEN_KEY, token),
    setRefresh: (token) => localStorage.setItem(REFRESH_TOKEN_KEY, token),
    setUser: (user) => localStorage.setItem(USER_KEY, JSON.stringify(user)),
    clear: () => {
        localStorage.removeItem(ACCESS_TOKEN_KEY);
        localStorage.removeItem(REFRESH_TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
    },
};

// Relative URL để nginx proxy /api/ → gateway:3000
// Khi dev local ngoài Docker: dùng VITE_API_BASE_URL env
const baseURL =
    import.meta.env?.VITE_API_BASE_URL ||
    (typeof process !== 'undefined' && process.env?.VITE_API_BASE_URL) ||
    '/api/v1';

const apiClient = axios.create({
    baseURL,
    timeout: 15000,
    headers: { 'Content-Type': 'application/json' },
});

apiClient.interceptors.request.use((config) => {
    const token = tokenStorage.getAccess();
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

let isRefreshing = false;
let pendingRequests = [];

apiClient.interceptors.response.use(
    (response) => response,
    async (error) => {
        const original = error.config;

        if (
            error.response?.status === 401 &&
            tokenStorage.getRefresh() &&
            !original._retry &&
            !original.url?.endsWith('/auth/refresh')
        ) {
            if (isRefreshing) {
                return new Promise((resolve, reject) => {
                    pendingRequests.push({ resolve, reject });
                }).then((newToken) => {
                    original.headers.Authorization = `Bearer ${newToken}`;
                    return apiClient(original);
                });
            }

            original._retry = true;
            isRefreshing = true;

            try {
                const { data } = await axios.post(`${baseURL}/auth/refresh`, {
                    refreshToken: tokenStorage.getRefresh(),
                });
                if (data?.success && data.data?.accessToken) {
                    tokenStorage.setAccess(data.data.accessToken);
                    if (data.data.refreshToken) {
                        tokenStorage.setRefresh(data.data.refreshToken);
                    }
                    pendingRequests.forEach((p) => p.resolve(data.data.accessToken));
                    pendingRequests = [];
                    original.headers.Authorization = `Bearer ${data.data.accessToken}`;
                    return apiClient(original);
                }
                throw new Error('Refresh failed');
            } catch (refreshErr) {
                pendingRequests.forEach((p) => p.reject(refreshErr));
                pendingRequests = [];
                forceLogout();
                return Promise.reject(refreshErr);
            } finally {
                isRefreshing = false;
            }
        }

        // === ApiResponse error envelope unwrapping ===
        if (error.response?.data) {
            const body = error.response.data;
            if (body.success === false) {
                const message = body.message || 'Đã xảy ra lỗi';
                if (error.response.status !== 401) {
                    toast.error(message);
                }
                return Promise.reject({
                    status: error.response.status,
                    message,
                    errors: body.errors || null,
                    data: body.data || null,
                });
            }
        }

        if (!error.response) {
            toast.error('Không thể kết nối tới máy chủ');
        }
        return Promise.reject(error);
    }
);

function forceLogout() {
    tokenStorage.clear();
    const path = window.location.pathname;
    if (!path.startsWith('/login')) {
        toast.error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
        window.location.href = '/login';
    }
}

export default apiClient;