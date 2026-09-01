import axios from 'axios';
import toast from 'react-hot-toast';

const ACCESS_TOKEN_KEY = 'woodfurni_access_token';
const REFRESH_TOKEN_KEY = 'woodfurni_refresh_token';
const USER_KEY = 'woodfurni_user';

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

// The base URL for the customer-app REST API.
//
// Resolution order:
//   1. import.meta.env.VITE_API_BASE_URL  (Vite build)
//   2. process.env.VITE_API_BASE_URL       (Vitest sets this in vitest.config.js)
//   3. Hard-coded localhost fallback       (so axios.create() never receives
//      an empty baseURL, which would make `new URL(fullPath, urlBase)` in
//      axios's http adapter throw `Invalid base URL: ` under jsdom.)
const baseURL =
    import.meta.env?.VITE_API_BASE_URL ||
    (typeof process !== 'undefined' && process.env?.VITE_API_BASE_URL) ||
    'http://localhost:3001/api/v1';

const apiClient = axios.create({
    baseURL,
    timeout: 15000,
    headers: { 'Content-Type': 'application/json' },
});

// Attach access token to every request
apiClient.interceptors.request.use((config) => {
    const token = tokenStorage.getAccess();
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// 401 handler: try refresh once, then force logout
let isRefreshing = false;
let pendingRequests = [];

apiClient.interceptors.response.use(
    (response) => response,
    async (error) => {
        const original = error.config;

        // 401 + we have a refresh token + we haven't already retried +
        // this isn't itself the refresh call (avoids recursion and noisy
        // logs when MSW returns 401 in tests).
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
        // Backend wraps every response in { success, message, data, errors, timestamp }
        // We surface the backend's message to the UI via a consistent shape.
        if (error.response?.data) {
            const body = error.response.data;
            if (body.success === false) {
                const message = body.message || 'Đã xảy ra lỗi';
                // For 401 during login (no refresh token in storage), show toast
                // For other 401 cases, we handle redirect silently
                const isLoginAttempt = !tokenStorage.getAccess() && !original?.url?.endsWith('/auth/refresh');
                if (error.response.status !== 401 || isLoginAttempt) {
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

        // Network / unknown
        if (!error.response) {
            toast.error('Không thể kết nối tới máy chủ');
        }
        return Promise.reject(error);
    }
);

function forceLogout() {
    tokenStorage.clear();
    const path = window.location.pathname;
    if (!path.startsWith('/login') && !path.startsWith('/register')) {
        toast.error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
        window.location.href = '/login';
    }
}

export default apiClient;