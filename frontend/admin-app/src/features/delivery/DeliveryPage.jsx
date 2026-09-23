import { useCallback, useEffect, useMemo, useState } from 'react';
import toast from 'react-hot-toast';
import usePageTitle from '../../hooks/usePageTitle.js';
import { deliveryApi } from '../../services/apiDelivery.js';
import { formatDateTime, formatNumber } from '../../utils/format.js';
import { Button } from '../../components/index.js';
import CreateTripModal from './CreateTripModal.jsx';
import './DeliveryPage.css';

const STATUS_LABELS = {
    PLANNING:   { label: 'Lên kế hoạch', cls: 'status--planning' },
    SHIPPING:   { label: 'Đang giao',   cls: 'status--shipping'  },
    COMPLETED:  { label: 'Hoàn thành',  cls: 'status--completed' },
    CANCELLED:  { label: 'Đã hủy',      cls: 'status--cancelled' },
};

function StatusBadge({ status }) {
    const info = STATUS_LABELS[status] || { label: status, cls: '' };
    return <span className={`trip-status-badge ${info.cls}`}>{info.label}</span>;
}

export default function DeliveryPage() {
    usePageTitle('Chuyến xe');
    const [tab, setTab] = useState('all');
    const [search, setSearch] = useState('');
    const [trips, setTrips] = useState([]);
    const [pagination, setPagination] = useState({ page: 0, totalPages: 0, totalElements: 0 });
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [showCreate, setShowCreate] = useState(false);
    const [selectedIds, setSelectedIds] = useState([]);
    const [bulkLoading, setBulkLoading] = useState(false);
    const [showCancelModal, setShowCancelModal] = useState(false);
    const [cancelReason, setCancelReason] = useState('');

    const loadPage = useCallback(async (page) => {
        setLoading(true);
        setError(null);
        try {
            const params = { page, size: 20 };
            if (search.trim()) params.search = search.trim();
            if (tab !== 'all') params.status = tab;
            const data = await deliveryApi.listTrips(params);
            setTrips(data.items || []);
            setPagination({
                page: data.page ?? page,
                totalPages: data.totalPages ?? 0,
                totalElements: data.totalElements ?? 0,
            });
            // Reset selection khi load lại
            setSelectedIds([]);
        } catch (err) {
            setError(err?.message || 'Không thể tải danh sách chuyến xe');
        } finally {
            setLoading(false);
        }
    }, [tab, search]);

    useEffect(() => { loadPage(0); }, [loadPage]);

    const columns = useMemo(() => [
        {
            key: '_select', header: '', width: 50,
            render: (r) => {
                if (r.status !== 'PLANNING') {
                    return <span className="text-muted">—</span>;
                }
                const checked = selectedIds.includes(r.id);
                return (
                    <input
                        type="checkbox"
                        checked={checked}
                        onChange={() => toggleSelect(r.id)}
                        aria-label={`Chọn chuyến ${r.tripNumber}`}
                    />
                );
            },
        },
        {
            key: 'tripNumber', header: 'Số chuyến', width: 160,
            render: (r) => <strong>{r.tripNumber}</strong>,
        },
        {
            key: '_actions', header: 'Hành động', width: 240,
            render: (r) => {
                if (r.status === 'PLANNING') {
                    return (
                        <div className="row-actions">
                            <button
                                type="button"
                                className="btn-row btn-row--primary"
                                disabled={bulkLoading}
                                onClick={async () => {
                                    try {
                                        await deliveryApi.startShipping(r.id);
                                        toast.success(`Đã bắt đầu giao ${r.tripNumber}`);
                                        loadPage(pagination.page);
                                    } catch (e) {
                                        toast.error(e?.message || 'Thất bại');
                                    }
                                }}
                            >Xác nhận giao</button>
                            <button
                                type="button"
                                className="btn-row btn-row--danger"
                                disabled={bulkLoading}
                                onClick={async () => {
                                    if (!window.confirm(`Hủy chuyến ${r.tripNumber}?`)) return;
                                    try {
                                        await deliveryApi.cancelTrip(r.id, '');
                                        toast.success(`Đã hủy ${r.tripNumber}`);
                                        loadPage(pagination.page);
                                    } catch (e) {
                                        toast.error(e?.message || 'Thất bại');
                                    }
                                }}
                            >Hủy chuyến</button>
                        </div>
                    );
                }
                if (r.status === 'SHIPPING') {
                    return (
                        <div className="row-actions">
                            <button
                                type="button"
                                className="btn-row btn-row--success"
                                disabled={bulkLoading}
                                onClick={async () => {
                                    if (!window.confirm(`Xác nhận hoàn thành chuyến ${r.tripNumber}?`)) return;
                                    try {
                                        await deliveryApi.completeTrip(r.id);
                                        toast.success(`Đã hoàn thành ${r.tripNumber}`);
                                        loadPage(pagination.page);
                                    } catch (e) {
                                        toast.error(e?.message || 'Thất bại');
                                    }
                                }}
                            >Hoàn thành</button>
                        </div>
                    );
                }
                return <span className="text-muted">—</span>;
            },
        },
        {
            key: 'status', header: 'Trạng thái', width: 130,
            render: (r) => <StatusBadge status={r.status} />,
        },
        {
            key: 'status', header: 'Trạng thái', width: 130,
            render: (r) => <StatusBadge status={r.status} />,
        },
        {
            key: 'vehicleTypeName', header: 'Loại xe', width: 140,
            render: (r) => r.vehicle?.displayName || r.vehicleTypeName || '—',
        },
        {
            key: 'driverName', header: 'Tài xế', width: 150,
            render: (r) => r.driverName
                ? <span>{r.driverName}<br /><small className="text-muted">{r.driverPhone || ''}</small></span>
                : '—',
        },
        {
            key: 'totalOrders', header: 'Số đơn', width: 80, align: 'right',
            render: (r) => formatNumber(r.totalOrders),
        },
        {
            key: 'totalWeightKg', header: 'Tải trọng (kg)', width: 110, align: 'right',
            render: (r) => r.totalWeightKg > 0 ? `${r.totalWeightKg.toFixed(1)} kg` : '—',
        },
        {
            key: 'totalVolumeM3', header: 'Thể tích (m³)', width: 110, align: 'right',
            render: (r) => r.totalVolumeM3 > 0 ? `${r.totalVolumeM3.toFixed(2)} m³` : '—',
        },
        {
            key: 'createdAt', header: 'Ngày tạo', width: 140,
            render: (r) => formatDateTime(r.createdAt),
        },
    ], [selectedIds, bulkLoading, pagination.page]);

    const handleSearch = (e) => {
        e.preventDefault();
        loadPage(0);
    };

    // Chỉ các chuyến PLANNING mới cho phép chọn để bulk action
    const selectableIds = useMemo(
        () => trips.filter((t) => t.status === 'PLANNING').map((t) => t.id),
        [trips]
    );

    const toggleSelect = (id) => {
        setSelectedIds((prev) =>
            prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
        );
    };

    const toggleSelectAll = () => {
        if (selectedIds.length === selectableIds.length) {
            setSelectedIds([]);
        } else {
            setSelectedIds([...selectableIds]);
        }
    };

    const handleBulkStart = async () => {
        if (selectedIds.length === 0) return;
        setBulkLoading(true);
        try {
            const res = await deliveryApi.bulkStartShipping(selectedIds);
            toast.success(
                `Đã bắt đầu giao ${res.successCount ?? selectedIds.length} chuyến` +
                (res.failureCount > 0 ? `, ${res.failureCount} thất bại` : '')
            );
            if (res.errors?.length > 0) {
                console.warn('Bulk start errors:', res.errors);
            }
            setSelectedIds([]);
            loadPage(pagination.page);
        } catch (err) {
            toast.error(err?.message || 'Không thể bắt đầu giao hàng');
        } finally {
            setBulkLoading(false);
        }
    };

    const handleBulkCancel = async () => {
        if (selectedIds.length === 0) return;
        setBulkLoading(true);
        try {
            const res = await deliveryApi.bulkCancelTrips(selectedIds, cancelReason);
            toast.success(
                `Đã hủy ${res.successCount ?? selectedIds.length} chuyến` +
                (res.failureCount > 0 ? `, ${res.failureCount} thất bại` : '')
            );
            setShowCancelModal(false);
            setCancelReason('');
            setSelectedIds([]);
            loadPage(pagination.page);
        } catch (err) {
            toast.error(err?.message || 'Không thể hủy chuyến xe');
        } finally {
            setBulkLoading(false);
        }
    };

    return (
        <div className="admin-page delivery-page">
            <header className="admin-page__header">
                <div>
                    <h1>Chuyến xe</h1>
                    <p className="admin-page__sub">{pagination.totalElements} chuyến xe</p>
                </div>
                <Button variant="primary" size="md" onClick={() => setShowCreate(true)}>
                    + Tạo chuyến xe
                </Button>
            </header>

            <div className="delivery-tabs">
                {['all', 'PLANNING', 'SHIPPING', 'COMPLETED'].map((t) => (
                    <button
                        key={t}
                        type="button"
                        className={`inv-tab ${tab === t ? 'is-active' : ''}`}
                        onClick={() => setTab(t)}
                    >
                        {t === 'all' ? 'Tất cả' : STATUS_LABELS[t]?.label || t}
                    </button>
                ))}
            </div>

            <form className="delivery-search" onSubmit={handleSearch}>
                <input
                    type="text"
                    placeholder="Tìm số chuyến (phân cách bằng dấu phẩy hoặc khoảng trắng nếu nhập nhiều)..."
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                />
                <button type="submit" className="btn-search">Tìm</button>
            </form>

            {error && (
                <div className="inventory-page__error">
                    <strong>Lỗi.</strong> {error}
                    <button type="button" onClick={() => loadPage(pagination.page)}>Thử lại</button>
                </div>
            )}

            {!loading && !error && trips.length === 0 && (
                <div className="delivery-empty">
                    <p>Chưa có chuyến xe nào.</p>
                    <Button variant="outline" size="sm" onClick={() => setShowCreate(true)}>
                        Tạo chuyến xe đầu tiên
                    </Button>
                </div>
            )}

            {selectedIds.length > 0 && (
                <div className="bulk-action-bar">
                    <span className="bulk-action-bar__count">
                        Đã chọn <strong>{selectedIds.length}</strong> chuyến xe
                    </span>
                    <div className="bulk-action-bar__buttons">
                        <Button
                            variant="primary"
                            size="sm"
                            disabled={bulkLoading}
                            onClick={handleBulkStart}
                        >
                            {bulkLoading ? 'Đang xử lý…' : `Bắt đầu giao ${selectedIds.length} chuyến`}
                        </Button>
                        <Button
                            variant="danger"
                            size="sm"
                            disabled={bulkLoading}
                            onClick={() => setShowCancelModal(true)}
                        >
                            Hủy {selectedIds.length} chuyến
                        </Button>
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={() => setSelectedIds([])}
                        >
                            Bỏ chọn
                        </Button>
                    </div>
                </div>
            )}

            {!loading && !error && trips.length > 0 && (
                <div className="delivery-table-wrap">
                    <table className="delivery-table">
                        <thead>
                            <tr>
                                {columns.map((c) => (
                                    <th key={c.key} style={{ width: c.width, textAlign: c.align || 'left' }}>
                                        {c.key === '_select' && selectableIds.length > 0 ? (
                                            <input
                                                type="checkbox"
                                                checked={selectedIds.length === selectableIds.length && selectableIds.length > 0}
                                                onChange={toggleSelectAll}
                                                aria-label="Chọn tất cả"
                                            />
                                        ) : c.header}
                                    </th>
                                ))}
                            </tr>
                        </thead>
                        <tbody>
                            {trips.map((t) => (
                                <tr key={t.id}>
                                    {columns.map((c) => (
                                        <td key={c.key} style={{ textAlign: c.align || 'left' }}>
                                            {c.render(t)}
                                        </td>
                                    ))}
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}

            {loading && <p className="delivery-loading">Đang tải…</p>}

            {pagination.totalPages > 1 && (
                <div className="delivery-pagination">
                    <button
                        type="button"
                        className="inv-tab"
                        disabled={pagination.page === 0}
                        onClick={() => loadPage(pagination.page - 1)}
                    >← Trang trước</button>
                    <span>Trang {pagination.page + 1} / {pagination.totalPages} ({pagination.totalElements} chuyến)</span>
                    <button
                        type="button"
                        className="inv-tab"
                        disabled={pagination.page >= pagination.totalPages - 1}
                        onClick={() => loadPage(pagination.page + 1)}
                    >Trang sau →</button>
                </div>
            )}

            {showCreate && (
                <CreateTripModal
                    onClose={() => setShowCreate(false)}
                    onDone={() => { setShowCreate(false); loadPage(0); }}
                />
            )}

            {showCancelModal && (
                <div className="modal-overlay" onClick={() => setShowCancelModal(false)}>
                    <div className="modal-card" onClick={(e) => e.stopPropagation()}>
                        <h3>Hủy {selectedIds.length} chuyến xe</h3>
                        <p className="text-muted">
                            Các chuyến xe đã chọn sẽ chuyển sang trạng thái <strong>Đã hủy</strong>.
                        </p>
                        <label className="modal-label">
                            Lý do hủy (tuỳ chọn)
                            <textarea
                                value={cancelReason}
                                onChange={(e) => setCancelReason(e.target.value)}
                                placeholder="Nhập lý do hủy..."
                                rows={3}
                                style={{ width: '100%', marginTop: 6 }}
                            />
                        </label>
                        <div className="modal-actions">
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={() => { setShowCancelModal(false); setCancelReason(''); }}
                            >Đóng</Button>
                            <Button
                                variant="danger"
                                size="sm"
                                disabled={bulkLoading}
                                onClick={handleBulkCancel}
                            >{bulkLoading ? 'Đang xử lý…' : 'Xác nhận hủy'}</Button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
