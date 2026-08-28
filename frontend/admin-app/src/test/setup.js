import '@testing-library/jest-dom/vitest';
import { afterEach, beforeAll, afterAll, vi } from 'vitest';
import { server } from './server.js';
import apiClient from '../services/apiClient.js';

const TEST_BASE_URL = 'http://localhost:3001/api/v1';

beforeAll(() => {
    server.listen({ onUnhandledRequest: 'error' });
    apiClient.defaults.baseURL = TEST_BASE_URL;
});

afterEach(() => {
    server.resetHandlers();
    window.localStorage.clear();
    window.sessionStorage.clear();
});

afterAll(() => server.close());

// jsdom doesn't navigate. Patch location.assign / replace so that the 401
// recovery code in apiClient (which calls these) doesn't throw under jsdom.
// We must NOT replace window.location itself — axios captures
// window.location.href at module-load time and uses it as the URL base for
// `new URL(...)`. Replacing it with an object that has an empty `href`
// triggers whatwg-url's "Invalid base URL" error and breaks every request.
if (window.location && typeof window.location.assign === 'function') {
    try {
        vi.spyOn(window.location, 'assign').mockImplementation(() => {});
        vi.spyOn(window.location, 'replace').mockImplementation(() => {});
    } catch {
        // best-effort: some jsdom builds don't allow spying — ignore
    }
}