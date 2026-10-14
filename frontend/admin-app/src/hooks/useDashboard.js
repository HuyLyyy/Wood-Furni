import { useCallback, useEffect, useState } from 'react';
import { reportsApi } from '../services/apiReports.js';

/**
 * useDashboard — loads all dashboard panels in parallel.
 *
 * Returns:
 *   summary              { revenueToday, ordersToday, newCustomersToday, lowStockCount } or null
 *   revenue              [{ month | date, revenue }, ...]
 *                        - When `revenuePeriod` is null: 12 months (month buckets)
 *                        - When `revenuePeriod` is {year, month}: that month (daily buckets)
 *   byStatus             [{ status, count }, ...]
 *   topProducts          [{ productId, productName, totalQuantitySold }, ...]
 *   environmentRevenue   [{ environment, label, revenue, orderCount }, ...]
 *                        Exactly two rows: INDOOR and OUTDOOR (BOTH products
 *                        are counted as INDOOR by the backend). Zero-revenue
 *                        buckets are still emitted so the pie chart can show
 *                        both slices.
 *   loading              true during the initial fetch
 *   error                string | null
 *   refresh()            manual re-fetch (uses current filters)
 *   setRevenuePeriod({year, month} | null)   switch chart between monthly/daily
 *
 * Revenue chart supports two modes:
 *   1. Default (no period) → 12-month line chart.
 *   2. Single month → daily bar chart (1..daysInMonth).
 */
export default function useDashboard() {
    const [state, setState] = useState({
        summary: null,
        revenue: [],
        byStatus: [],
        topProducts: [],
        environmentRevenue: [],
        loading: true,
        error: null,
        revenuePeriod: null, // null = "last 12 months", else {year, month}
    });

    const fetchAll = useCallback(async (period) => {
        const currentPeriod = period !== undefined ? period : state.revenuePeriod;
        setState((prev) => ({ ...prev, loading: true, error: null }));
        try {
            const [summary, revenue, byStatus, topProducts, environmentRevenue] = await Promise.all([
                reportsApi.getSummary(),
                reportsApi.getMonthlyRevenue(currentPeriod || undefined),
                reportsApi.getOrdersByStatus(),
                reportsApi.getTopProducts(10),
                reportsApi.getRevenueByEnvironment(),
            ]);
            setState((prev) => ({
                summary: summary || null,
                revenue: revenue || [],
                byStatus: byStatus || [],
                topProducts: topProducts || [],
                environmentRevenue: environmentRevenue || [],
                loading: false,
                error: null,
                revenuePeriod: currentPeriod,
            }));
        } catch (err) {
            setState((prev) => ({
                ...prev,
                loading: false,
                error: err?.message || 'Không thể tải dữ liệu dashboard',
            }));
        }
    }, [state.revenuePeriod]);

    // Initial fetch — default to current month so the chart is focused
    // on the period the user is in. Backend will return daily data for it.
    useEffect(() => {
        const now = new Date();
        const initialPeriod = { year: now.getFullYear(), month: now.getMonth() + 1 };
        fetchAll(initialPeriod);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    /**
     * Change the revenue chart period. Pass `null` to go back to 12-month view.
     * @param {{year:number, month:number} | null} period
     */
    const setRevenuePeriod = useCallback((period) => {
        fetchAll(period);
    }, [fetchAll]);

    return {
        ...state,
        refresh: () => fetchAll(state.revenuePeriod),
        setRevenuePeriod,
    };
}
