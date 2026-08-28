import apiClient from './apiClient';

/**
 * Reporting / Dashboard API.
 *
 *   GET /admin/dashboard/summary            → DashboardSummaryResponse
 *   GET /admin/dashboard/revenue           → MonthlyRevenueResponse[]
 *   GET /admin/dashboard/orders-by-status  → OrdersByStatusResponse[]
 *   GET /admin/dashboard/top-products      → TopProductResponse[]
 *   GET /admin/dashboard/category-breakdown→ CategoryBreakdownResponse[]
 *
 * Backend: @PreAuthorize("hasRole('ADMIN')") at controller level.
 * Non-ADMIN users will get 403 — we surface that as a normal error.
 */
const unwrap = (r) => r.data.data;

export const reportsApi = {
    getSummary: () => apiClient.get('/admin/dashboard/summary').then(unwrap),
    getMonthlyRevenue: () => apiClient.get('/admin/dashboard/revenue').then(unwrap),
    getOrdersByStatus: () => apiClient.get('/admin/dashboard/orders-by-status').then(unwrap),
    getTopProducts: (limit = 10) =>
        apiClient.get('/admin/dashboard/top-products', { params: { limit } }).then(unwrap),
    getCategoryBreakdown: () => apiClient.get('/admin/dashboard/category-breakdown').then(unwrap),
};