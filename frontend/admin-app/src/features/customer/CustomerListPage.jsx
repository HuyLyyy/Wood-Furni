import { useCallback, useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import usePageTitle from '../../hooks/usePageTitle.js';
import { useAuth } from '../../hooks/useAuth.js';
import { adminCustomersApi } from '../../services/apiAdminCustomers.js';
import {
    DataTable, AdminPagination, Modal, Button, ConfirmDialog, useConfirmDialog,
} from '../../components/index.js';
import { can } from '../../utils/permissions.js';
import {
    statusLabel, statusTone,
} from '../../utils/orderMeta.js';
import { formatCurrency, formatDate, formatDateTime } from '../../utils/format.js';
import './CustomerListPage.css';

/**
 * CustomerListPage
 *
 *   Row click → open customer-detail drawer (Modal) showing the user's
 *   order history.
 */
export default function CustomerListPage() {
    usePageTitle('Khách hàng');
    const { user } = useAuth();
    const canDelete = can(user?.role, 'customers:delete');

    const [page, setPage] = useState(0);
    const [items, setItems] = useState([]);
    const [totalElements, setTotalElements] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [selected, setSelected] = useState(null);

    const load = useCallback(async (p = 0) => {
        setLoading(true);
        setError(null);
        try {
            const data = await adminCustomersApi.list({ page: p, size: 20 });
            setItems(data.items || []);
            setPage(data.page ?? p);
            setTotalElements(data.totalElements ?? 0);
            setTotalPages(data.totalPages ?? 0);
        } catch (err) {
            setError(err);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { load(0); }, [load]);

    const columns = [
        {
            key: 'fullName', header: 'Khách hàng',
            render: (c) => (
                <div>
                    <div className="customer-row__name">{c.fullName || '—'}</div>
                    <div className="customer-row__email">{c.email}</div>
                </div>
            ),
        },
        { key: 'phone', header: 'SĐT', width: 130, render: (c) => c.phone || '—' },
        {
            key: 'orderCount', header: 'Đơn', width: 100, align: 'right',
            render: (c) => <span className="count-pill">{c.orderCount ?? 0}</span>,
        },
        {
            key: 'totalSpent', header: 'Tổng chi', width: 150, align: 'right',
            render: (c) => <span className="spent-pill">{formatCurrency(c.totalSpent)}</span>,
        },
        {
            key: 'status', header: 'TT TK', width: 110,
            render: (c) => (
                <span className={`status-pill status-pill--${c.status}`}>
                    {c.status === 'ACTIVE' ? 'Hoạt động' : 'Vô hiệu'}
                </span>
            ),
        },
        {
            key: 'createdAt', header: 'Ngày tạo', width: 120,
            render: (c) => formatDate(c.createdAt),
        },
    ];

    return (
        <div className="admin-page customer-list-page">
            <header className="admin-page__header">
                <div>
                    <h1>Khách hàng</h1>
                    <p className="admin-page__sub">{totalElements} khách</p>
                </div>
                <Button variant="ghost" onClick={() => load(page)}>Làm mới</Button>
            </header>

            {error ? (
                <div className="customer-list-page__error">
                    <strong>Không thể tải danh sách.</strong> {error?.message}
                    <Button variant="primary" onClick={() => load(page)}>Thử lại</Button>
                </div>
            ) : (
                <>
                    <DataTable
                        columns={columns}
                        rows={items}
                        loading={loading}
                        rowKey="id"
                        onRowClick={(row) => setSelected(row)}
                        emptyText="Chưa có khách hàng nào."
                    />
                    <AdminPagination
                        page={page}
                        totalPages={totalPages}
                        onChange={load}
                    />
                </>
            )}

            {selected && (
                <CustomerDetailModal
                    customerId={selected.id}
                    fallback={selected}
                    canDelete={canDelete}
                    onClose={() => setSelected(null)}
                    onDeleted={() => { setSelected(null); load(page); }}
                />
            )}
        </div>
    );
}

// =============================================================
// CustomerDetailModal
// =============================================================

function CustomerDetailModal({ customerId, fallback, canDelete, onClose, onDeleted }) {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [deleting, setDeleting] = useState(false);
    const { confirm, dialog } = useConfirmDialog();

    useEffect(() => {
        let cancelled = false;
        adminCustomersApi.detail(customerId)
            .then((d) => { if (!cancelled) { setData(d); setLoading(false); } })
            .catch(() => { if (!cancelled) { setLoading(false); } });
        return () => { cancelled = true; };
    }, [customerId]);

    const view = data || fallback;

    const handleDelete = async () => {
        const ok = await confirm({
            title: 'Xóa tài khoản khách hàng?',
            message: `Bạn sắp xóa vĩnh viễn tài khoản "${view.fullName || view.email}". Hành động này không thể hoàn tác. Lịch sử đơn hàng sẽ được ẩn danh nhưng vẫn giữ cho báo cáo doanh thu.`,
            confirmLabel: 'Xóa vĩnh viễn',
            danger: true,
        });
        if (!ok) return;
        setDeleting(true);
        try {
            await adminCustomersApi.remove(customerId);
            toast.success('Đã xóa tài khoản khách hàng');
            onDeleted?.();
        } catch (err) {
            toast.error(err?.message || 'Không thể xóa tài khoản');
            setDeleting(false);
        }
    };

    return (
        <Modal title={`Khách hàng: ${view.fullName || view.email}`} onClose={onClose} width={760}>
            {dialog}
            {loading && <p style={{ color: '#94a3b8' }}>Đang tải chi tiết...</p>}
            {!loading && (
                <>
                    <div className="customer-drawer__row">
                        <span className="customer-drawer__label">Email</span>
                        <span className="customer-drawer__value">{view.email}</span>
                    </div>
                    <div className="customer-drawer__row">
                        <span className="customer-drawer__label">SĐT</span>
                        <span className="customer-drawer__value">{view.phone || '—'}</span>
                    </div>
                    <div className="customer-drawer__row">
                        <span className="customer-drawer__label">Ngày tạo</span>
                        <span className="customer-drawer__value">{formatDateTime(view.createdAt)}</span>
                    </div>
                    <div className="customer-drawer__row">
                        <span className="customer-drawer__label">Tổng quan</span>
                        <span className="customer-drawer__value">
                            {view.orderCount ?? 0} đơn · {formatCurrency(view.totalSpent)}
                        </span>
                    </div>

                    <h4 style={{ marginTop: 18, marginBottom: 8, fontSize: 13, color: '#64748b' }}>
                        LỊCH SỬ ĐƠN HÀNG ({view.orders?.length ?? 0})
                    </h4>

                    {!view.orders || view.orders.length === 0 ? (
                        <p className="customer-drawer__empty">Khách hàng chưa có đơn hàng nào.</p>
                    ) : (
                        <table className="customer-detail__orders">
                            <thead>
                                <tr>
                                    <th>Mã đơn</th>
                                    <th>Ngày</th>
                                    <th>SP</th>
                                    <th>Tổng</th>
                                    <th>TT</th>
                                    <th>Trạng thái</th>
                                </tr>
                            </thead>
                            <tbody>
                                {view.orders.map((o) => (
                                    <tr key={o.id}>
                                        <td><strong>{o.orderNumber}</strong></td>
                                        <td>{formatDate(o.createdAt)}</td>
                                        <td>{o.itemCount}</td>
                                        <td>{formatCurrency(o.totalAmount)}</td>
                                        <td>{o.paymentStatus}</td>
                                        <td>
                                            <span className={`status-badge status-badge--${statusTone(o.status)}`}>
                                                {statusLabel(o.status)}
                                            </span>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    )}

                    {canDelete && (
                        <div className="customer-drawer__footer">
                            <Button
                                variant="danger"
                                onClick={handleDelete}
                                loading={deleting}
                            >
                                Xóa tài khoản
                            </Button>
                        </div>
                    )}
                </>
            )}
        </Modal>
    );
}
