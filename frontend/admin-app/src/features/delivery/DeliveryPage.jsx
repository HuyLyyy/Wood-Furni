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
        } catch (err) {
            setError(err?.message || 'Không thể tải danh sách chuyến xe');
        } finally {
            setLoading(false);
        }
    }, [tab, search]);

    useEffect(() => { loadPage(0); }, [loadPage]);

    const columns = useMemo(() => [
        {
            key: 'tripNumber', header: 'Số chuyến', width: 160,
            render: (r) => <strong>{r.tripNumber}</strong>,
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
    ], []);

    const handleSearch = (e) => {
        e.preventDefault();
        loadPage(0);
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
                    placeholder="Tìm số chuyến (VD: TRIP-20260923-0001)…"
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

            {!loading && !error && trips.length > 0 && (
                <div className="delivery-table-wrap">
                    <table className="delivery-table">
                        <thead>
                            <tr>
                                {columns.map((c) => (
                                    <th key={c.key} style={{ width: c.width, textAlign: c.align || 'left' }}>
                                        {c.header}
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
        </div>
    );
}
