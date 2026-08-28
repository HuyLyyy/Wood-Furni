import { Link } from 'react-router-dom';
import usePageTitle from '../../hooks/usePageTitle.js';
import useAdminOrders from '../../hooks/useAdminOrders.js';
import { useAuth } from '../../contexts/AuthContext.jsx';
import {
    Button, DataTable, AdminPagination, FormField,
} from '../../components/index.js';
import {
    ORDER_STATUS, statusLabel, statusTone,
    paymentStatusLabel,
} from '../../utils/orderMeta.js';
import { formatCurrency, formatDateTime } from '../../utils/format.js';
import { can } from '../../utils/permissions.js';
import './OrderListPage.css';

/**
 * OrderListPage — admin order browse.
 *
 * Filters (URL-synced):
 *   - status       (PENDING|CONFIRMED|PROCESSING|SHIPPING|DELIVERED|CANCELLED|RETURNED)
 *   - customerId   (ObjectId or email — matches backend as-is)
 *   - from / to    (yyyy-MM-dd — FE pads to start/end of day)
 *   - page
 *
 * Click a row → /orders/{id} (detail page).
 */
export default function OrderListPage() {
    usePageTitle('Đơn hàng');
    const { user } = useAuth();
    const role = user?.role;

    const {
        items, totalElements, totalPages, page, loading, error,
        filters, setFilters, setPage, refresh,
    } = useAdminOrders();

    const columns = [
        {
            key: 'orderNumber', header: 'Mã đơn', width: 170,
            render: (o) => (
                <Link to={`/orders/${o.id}`} className="order-row__number">
                    {o.orderNumber}
                </Link>
            ),
        },
        {
            key: 'customer', header: 'Khách hàng',
            render: (o) => (
                <div className="order-row__customer">
                    <span>#{o.customerId?.slice(-6) || '—'}</span>
                    <small>{formatDateTime(o.createdAt)}</small>
                </div>
            ),
        },
        {
            key: 'items', header: 'SP', width: 60, align: 'right',
            render: (o) => (o.items ? o.items.length : 0),
        },
        {
            key: 'totalAmount', header: 'Tổng tiền', width: 140, align: 'right',
            render: (o) => (
                <span className="order-row__total">{formatCurrency(o.totalAmount)}</span>
            ),
        },
        {
            key: 'paymentStatus', header: 'Thanh toán', width: 130,
            render: (o) => paymentStatusLabel(o.paymentStatus),
        },
        {
            key: 'status', header: 'Trạng thái', width: 150,
            render: (o) => {
                const tone = statusTone(o.status);
                return <span className={`status-badge status-badge--${tone}`}>{statusLabel(o.status)}</span>;
            },
        },
    ];

    return (
        <div className="admin-page order-list-page">
            <header className="admin-page__header">
                <div>
                    <h1>Đơn hàng</h1>
                    <p className="admin-page__sub">{totalElements} đơn</p>
                </div>
                <Button variant="ghost" onClick={refresh}>Làm mới</Button>
            </header>

            <section className="order-list-page__filters">
                <FormField label="Mã đơn hàng" htmlFor="filter-order">
                    <input
                        id="filter-order"
                        type="text"
                        value={filters.orderNumber || ''}
                        onChange={(e) => setFilters({ orderNumber: e.target.value })}
                        placeholder="VD: ORD-001, ORD-002, ORD-003"
                    />
                </FormField>

                <FormField label="Trạng thái" htmlFor="filter-status">
                    <select
                        id="filter-status"
                        value={filters.status || ''}
                        onChange={(e) => setFilters({ status: e.target.value })}
                    >
                        <option value="">Tất cả</option>
                        {ORDER_STATUS.map((s) => (
                            <option key={s.value} value={s.value}>{s.label}</option>
                        ))}
                    </select>
                </FormField>

                <FormField label="Mã khách hàng (ObjectId)" htmlFor="filter-customer">
                    <input
                        id="filter-customer"
                        type="text"
                        value={filters.customerId || ''}
                        onChange={(e) => setFilters({ customerId: e.target.value })}
                        placeholder="ObjectId đầy đủ…"
                    />
                </FormField>

                <FormField label="Từ ngày" htmlFor="filter-from">
                    <input
                        id="filter-from"
                        type="date"
                        value={filters.createdFrom || ''}
                        onChange={(e) => setFilters({ from: e.target.value })}
                    />
                </FormField>

                <FormField label="Đến ngày" htmlFor="filter-to">
                    <input
                        id="filter-to"
                        type="date"
                        value={filters.createdTo || ''}
                        onChange={(e) => setFilters({ to: e.target.value })}
                    />
                </FormField>
            </section>

            {error ? (
                <div className="order-list-page__error">
                    <strong>Không thể tải danh sách đơn hàng.</strong>
                    <span>{error?.message}</span>
                    <button type="button" onClick={refresh}>Thử lại</button>
                </div>
            ) : (
                <>
                    <DataTable
                        columns={columns}
                        rows={items}
                        loading={loading}
                        rowKey="id"
                        onRowClick={(row) => {
                            if (row?.id) window.location.href = `/orders/${row.id}`;
                        }}
                        emptyText="Không có đơn hàng nào khớp bộ lọc."
                    />
                    <AdminPagination
                        page={page}
                        totalPages={totalPages}
                        onChange={setPage}
                    />
                </>
            )}
        </div>
    );
}
