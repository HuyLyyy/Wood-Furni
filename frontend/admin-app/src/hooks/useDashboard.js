import { useCallback, useEffect, useState } from 'react';
import { reportsApi } from '../services/apiReports.js';

/**
 * useDashboard — loads all dashboard panels in parallel.
 *
 * Returns:
 *   summary     { revenueToday, ordersToday, newCustomersToday, lowStockCount } or null
 *   revenue     [{ month: '2026-01', revenue: 12345 }, ...]  (last 12 months)
 *   byStatus    [{ status, count }, ...]
 *   topProducts [{ productId, productName, totalQuantitySold }, ...]
 *   loading     true during the initial fetch
 *   error       string | null
 *   refresh()   manual re-fetch — used after realtime events
 */
export default function useDashboard() {
    const [state, setState] = useState({
        summary: null,
        revenue: [],
        byStatus: [],
        topProducts: [],
        loading: true,
        error: null,
    });

    const fetchAll = useCallback(async () => {
        setState((prev) => ({ ...prev, loading: true, error: null }));
        try {
            const [summary, revenue, byStatus, topProducts] = await Promise.all([
                reportsApi.getSummary(),
                reportsApi.getMonthlyRevenue(),
                reportsApi.getOrdersByStatus(),
                reportsApi.getTopProducts(10),
            ]);
            setState({
                summary: summary || null,
                revenue: revenue || [],
                byStatus: byStatus || [],
                topProducts: topProducts || [],
                loading: false,
                error: null,
            });
        } catch (err) {
            setState((prev) => ({
                ...prev,
                loading: false,
                error: err?.message || 'Không thể tải dữ liệu dashboard',
            }));
        }
    }, []);

    useEffect(() => {
        fetchAll();
    }, [fetchAll]);

    return { ...state, refresh: fetchAll };
}