import { useCallback } from 'react';
import {
    ResponsiveContainer, LineChart, Line, XAxis, YAxis, Tooltip,
    CartesianGrid, BarChart, Bar, PieChart, Pie, Cell, Legend,
} from 'recharts';
import toast from 'react-hot-toast';
import usePageTitle from '../../hooks/usePageTitle.js';
import useDashboard from '../../hooks/useDashboard.js';
import { useRealtimeEvent } from '../../hooks/useRealtime.js';
import { useAuth } from '../../contexts/AuthContext.jsx';
import { formatCurrency, formatNumber, formatMonth } from '../../utils/format.js';
import { statusLabel } from '../../utils/orderMeta.js';
import './DashboardPage.css';

/**
 * DashboardPage — 4 KPI cards + 3 charts.
 *
 * Panels:
 *   1. Revenue Today      → GET /admin/dashboard/summary.revenueToday
 *   2. Orders Today       → GET /admin/dashboard/summary.ordersToday
 *   3. New Customers      → GET /admin/dashboard/summary.newCustomersToday
 *   4. Low-stock Products → GET /admin/dashboard/summary.lowStockCount
 *
 * Charts:
 *   A. Revenue by Month (LineChart)         ← /admin/dashboard/revenue
 *   B. Orders by Status (PieChart)          ← /admin/dashboard/orders-by-status
 *   C. Top 10 Products (BarChart horizontal  ← /admin/dashboard/top-products
 *
 * Realtime:
 *   - On `order.created` → toast "Có đơn hàng mới #ORD-xxx" and refresh
 *     the dashboard so "Orders Today" increments.
 */
export default function DashboardPage() {
    usePageTitle('Dashboard');

    const { isAuthenticated } = useAuth();
    const {
        summary, revenue, byStatus, topProducts,
        loading, error, refresh,
    } = useDashboard();

    // Re-fetch on order.created so the Orders Today counter ticks up
    const handleOrderCreated = useCallback(
        (payload) => {
            const num = payload?.orderNumber || payload?.id || '';
            toast.success(`Có đơn hàng mới #${num}`);
            refresh();
        },
        [refresh]
    );
    useRealtimeEvent('order.created', handleOrderCreated, isAuthenticated);

    return (
        <div className="dashboard">
            <header className="dashboard__header">
                <div>
                    <h1>Dashboard</h1>
                    <p className="dashboard__sub">
                        Tổng quan kinh doanh — hôm nay, {new Date().toLocaleDateString('vi-VN')}
                    </p>
                </div>
                <button
                    type="button"
                    className="dashboard__refresh"
                    onClick={refresh}
                    disabled={loading}
                >
                    {loading ? 'Đang tải...' : '↻ Làm mới'}
                </button>
            </header>

            {error && !loading && (
                <div className="dashboard__error">
                    <strong>Không thể tải dữ liệu.</strong> {error}
                    <button type="button" onClick={refresh}>Thử lại</button>
                </div>
            )}

            {/* ===== KPI cards ===== */}
            <section className="dashboard__stats">
                <StatCard
                    icon="💰"
                    label="Doanh thu hôm nay"
                    value={summary ? formatCurrency(summary.revenueToday) : '—'}
                    tone="primary"
                    loading={loading}
                />
                <StatCard
                    icon="🧾"
                    label="Đơn hàng hôm nay"
                    value={summary ? formatNumber(summary.ordersToday) : '—'}
                    tone="info"
                    loading={loading}
                />
                <StatCard
                    icon="👥"
                    label="Khách mới hôm nay"
                    value={summary ? formatNumber(summary.newCustomersToday) : '—'}
                    tone="success"
                    loading={loading}
                />
                <StatCard
                    icon="⚠️"
                    label="Sản phẩm sắp hết hàng"
                    value={summary ? formatNumber(summary.lowStockCount) : '—'}
                    tone="warning"
                    loading={loading}
                />
            </section>

            {/* ===== Charts row ===== */}
            <section className="dashboard__charts">
                <div className="dashboard__chart-card dashboard__chart-card--wide">
                    <header className="dashboard__chart-head">
                        <h2>Doanh thu theo tháng</h2>
                        <span className="dashboard__chart-sub">12 tháng gần nhất</span>
                    </header>
                    <div className="dashboard__chart-body">
                        {loading ? (
                            <ChartSkeleton />
                        ) : revenue.length === 0 ? (
                            <ChartEmpty text="Chưa có dữ liệu doanh thu" />
                        ) : (
                            <ResponsiveContainer width="100%" height={280}>
                                <LineChart data={revenue.map((r) => ({
                                    ...r,
                                    label: formatMonth(r.month),
                                    revenue: Number(r.revenue) || 0,
                                }))} margin={{ top: 10, right: 16, bottom: 0, left: 0 }}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
                                    <XAxis dataKey="label" tick={{ fontSize: 12 }} />
                                    <YAxis tick={{ fontSize: 12 }} tickFormatter={(v) => formatNumber(v)} />
                                    <Tooltip
                                        formatter={(v) => formatCurrency(v)}
                                        contentStyle={{ borderRadius: 8, fontSize: 13 }}
                                    />
                                    <Line
                                        type="monotone"
                                        dataKey="revenue"
                                        name="Doanh thu"
                                        stroke="#6b4f2a"
                                        strokeWidth={2.5}
                                        dot={{ r: 4 }}
                                        activeDot={{ r: 6 }}
                                    />
                                </LineChart>
                            </ResponsiveContainer>
                        )}
                    </div>
                </div>

                <div className="dashboard__chart-card">
                    <header className="dashboard__chart-head">
                        <h2>Đơn hàng theo trạng thái</h2>
                    </header>
                    <div className="dashboard__chart-body">
                        {loading ? (
                            <ChartSkeleton />
                        ) : byStatus.length === 0 ? (
                            <ChartEmpty text="Chưa có đơn hàng" />
                        ) : (
                            <ResponsiveContainer width="100%" height={280}>
                                <PieChart>
                                    <Pie
                                        data={byStatus}
                                        dataKey="count"
                                        nameKey="status"
                                        cx="50%" cy="50%"
                                        outerRadius={90}
                                        innerRadius={50}
                                        paddingAngle={2}
                                    >
                                        {byStatus.map((entry, i) => (
                                            <Cell key={i} fill={STATUS_COLORS[entry.status] || PIE_FALLBACK[i % PIE_FALLBACK.length]} />
                                        ))}
                                    </Pie>
                                    <Tooltip
                                        formatter={(value, name) => [value, statusLabel(name)]}
                                        contentStyle={{ borderRadius: 8, fontSize: 13 }}
                                    />
                                    <Legend
                                        formatter={(value) => statusLabel(value)}
                                        iconSize={10}
                                        wrapperStyle={{ fontSize: 12 }}
                                    />
                                </PieChart>
                            </ResponsiveContainer>
                        )}
                    </div>
                </div>

                <div className="dashboard__chart-card dashboard__chart-card--wide">
                    <header className="dashboard__chart-head">
                        <h2>Top 10 sản phẩm bán chạy</h2>
                        <span className="dashboard__chart-sub">Số lượng bán ra</span>
                    </header>
                    <div className="dashboard__chart-body">
                        {loading ? (
                            <ChartSkeleton />
                        ) : topProducts.length === 0 ? (
                            <ChartEmpty text="Chưa có dữ liệu bán hàng" />
                        ) : (
                            <ResponsiveContainer width="100%" height={Math.max(280, topProducts.length * 28 + 60)}>
                                <BarChart
                                    data={topProducts.map((p) => ({
                                        name: truncate(p.productName, 28),
                                        value: Number(p.totalQuantitySold) || 0,
                                    }))}
                                    layout="vertical"
                                    margin={{ top: 8, right: 24, bottom: 8, left: 12 }}
                                >
                                    <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
                                    <XAxis type="number" tick={{ fontSize: 12 }} tickFormatter={(v) => formatNumber(v)} />
                                    <YAxis type="category" dataKey="name" tick={{ fontSize: 12 }} width={180} />
                                    <Tooltip
                                        formatter={(v) => formatNumber(v)}
                                        contentStyle={{ borderRadius: 8, fontSize: 13 }}
                                    />
                                    <Bar dataKey="value" name="Số lượng" fill="#c79a4d" radius={[0, 4, 4, 0]} />
                                </BarChart>
                            </ResponsiveContainer>
                        )}
                    </div>
                </div>
            </section>
        </div>
    );
}

// =============================================================
// Sub-components
// =============================================================

function StatCard({ icon, label, value, tone, loading }) {
    return (
        <div className={`stat-card stat-card--${tone}`}>
            <div className="stat-card__icon">{icon}</div>
            <div className="stat-card__body">
                <p className="stat-card__label">{label}</p>
                <p className={`stat-card__value ${loading ? 'is-loading' : ''}`}>{value}</p>
            </div>
        </div>
    );
}

function ChartSkeleton() {
    return <div className="dashboard__chart-skeleton" />;
}

function ChartEmpty({ text }) {
    return <div className="dashboard__chart-empty">{text}</div>;
}

function truncate(s, n) {
    if (!s) return '';
    return s.length > n ? `${s.slice(0, n - 1)}…` : s;
}

const STATUS_COLORS = {
    PENDING:    '#f59e0b',
    CONFIRMED:  '#2563eb',
    PROCESSING: '#6366f1',
    SHIPPING:   '#0891b2',
    DELIVERED:  '#16a34a',
    CANCELLED:  '#dc2626',
    RETURNED:   '#6b7280',
};
const PIE_FALLBACK = ['#6b4f2a', '#c79a4d', '#2563eb', '#16a34a', '#dc2626', '#6366f1', '#0891b2'];