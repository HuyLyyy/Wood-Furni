import apiClient from './apiClient';

/**
 * Reporting / Dashboard API.
 *
 *   GET /admin/dashboard/summary             → DashboardSummaryResponse
 *   GET /admin/dashboard/revenue             → MonthlyRevenueResponse[] (last 12 months)
 *      or with ?year=YYYY&month=MM           → DailyRevenueResponse[] (single month)
 *   GET /admin/dashboard/orders-by-status    → OrdersByStatusResponse[]
 *   GET /admin/dashboard/top-products        → TopProductResponse[]
 *   GET /admin/dashboard/category-breakdown  → CategoryBreakdownResponse[]
 *
 * Backend: @PreAuthorize("hasRole('ADMIN')") at controller level.
 * Non-ADMIN users will get 403 — we surface that as a normal error.
 */
const unwrap = (r) => r.data.data;

export const reportsApi = {
    getSummary: () => apiClient.get('/admin/dashboard/summary').then(unwrap),
    /**
     * Revenue series for the dashboard chart.
     *
     * @param {{year?: number, month?: number}} [opts]
     *   - omitted → last 12 months (monthly buckets)
     *   - {year, month} → daily revenue for that single month
     * @returns {Promise<Array<{month:string, revenue:number} | {date:string, revenue:number}>>}
     */
    getMonthlyRevenue: (opts = {}) => {
        const params = {};
        if (opts.year != null && opts.month != null) {
            params.year = opts.year;
            params.month = opts.month;
        }
        return apiClient.get('/admin/dashboard/revenue', { params }).then(unwrap);
    },
    getOrdersByStatus: () => apiClient.get('/admin/dashboard/orders-by-status').then(unwrap),
    getTopProducts: (limit = 10) =>
        apiClient.get('/admin/dashboard/top-products', { params: { limit } }).then(unwrap),
    getCategoryBreakdown: () => apiClient.get('/admin/dashboard/category-breakdown').then(unwrap),
};