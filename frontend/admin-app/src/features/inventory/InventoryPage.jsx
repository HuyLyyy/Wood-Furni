import { useCallback, useEffect, useMemo, useState } from 'react';
import toast from 'react-hot-toast';
import usePageTitle from '../../hooks/usePageTitle.js';
import useInventory from '../../hooks/useInventory.js';
import { adminInventoryApi } from '../../services/apiAdminInventory.js';
import { useAuth } from '../../contexts/AuthContext.jsx';
import {
    Button, DataTable, AdminPagination, Modal, FormField,
} from '../../components/index.js';
import { formatDate, formatDateTime, formatNumber } from '../../utils/format.js';
import { can } from '../../utils/permissions.js';
import './InventoryPage.css';

/**
 * InventoryPage
 *
 * Tabs:
 *   - "Tất cả"     → GET /inventory?page=&size=
 *   - "Low Stock"  → GET /inventory/low-stock?page=&size=
 *
 * Each table row has a 3-dot menu (⋮) with:
 *   - "Điều chỉnh tồn kho"  → AdjustModal
 *   - "Lịch sử thay đổi"     → HistoryModal
 *   - "Chi tiết sản phẩm"     → DetailModal
 *
 * Permission: WAREHOUSE/ADMIN only (matches backend @PreAuthorize).
 */
export default function InventoryPage() {
    usePageTitle('Tồn kho');
    const { user } = useAuth();
    const role = user?.role;
    const canAdjust = can(role, 'inventory:adjust');

    const [tab, setTab] = useState('all'); // 'all' | 'low-stock'
    const lowStock = tab === 'low-stock';

    const { items, pagination, loading, error, loadPage, refresh } = useInventory({ pageSize: 20, lowStock });

    const [adjustTarget, setAdjustTarget] = useState(null);
    const [historyTarget, setHistoryTarget] = useState(null);
    const [detailTarget, setDetailTarget] = useState(null);

    // Close all dot menus when clicking outside
    useEffect(() => {
        const handler = (e) => {
            if (!e.target.closest('.inv-dot-btn') && !e.target.closest('.inv-dot-menu')) {
                document.querySelectorAll('.inv-dot-menu').forEach(el => { el.style.display = 'none'; });
            }
        };
        document.addEventListener('click', handler);
        return () => document.removeEventListener('click', handler);
    }, []);

    const columns = useMemo(() => ([
        {
            key: 'productName', header: 'Sản phẩm',
            render: (r) => (
                <div>
                    <div className="inventory-row__name">{r.productName || '—'}</div>
                    <div className="inventory-row__sku">SKU: {r.productSku || '—'}</div>
                </div>
            ),
        },
        {
            key: 'quantityOnHand', header: 'Tồn kho', width: 100, align: 'right',
            render: (r) => formatNumber(r.quantityOnHand),
        },
        {
            key: 'quantityReserved', header: 'Đã giữ', width: 100, align: 'right',
            render: (r) => formatNumber(r.quantityReserved),
        },
        {
            key: 'quantityAvailable', header: 'Khả dụng', width: 100, align: 'right',
            render: (r) => {
                const cls = r.quantityAvailable <= 0 ? 'inv-pill--danger' : r.quantityAvailable <= r.lowStockThreshold ? 'inv-pill--warn' : 'inv-pill--ok';
                return <span className={`inv-pill ${cls}`}>{formatNumber(r.quantityAvailable)}</span>;
            },
        },
        {
            key: 'lowStockThreshold', header: 'Ngưỡng', width: 90, align: 'right',
            render: (r) => r.lowStockThreshold,
        },
        {
            key: 'isLowStock', header: 'Trạng thái', width: 130,
            render: (r) => r.isLowStock
                ? <span className="status-badge status-badge--oos">Low stock</span>
                : <span className="status-badge status-badge--active">OK</span>,
        },
        {
            key: 'updatedAt', header: 'Cập nhật', width: 130,
            render: (r) => formatDate(r.updatedAt),
        },
        {
            key: 'actions', header: '', width: 50, align: 'right',
            render: (r) => (
                <div className="inv-actions-cell">
                    {canAdjust && (
                        <button
                            type="button"
                            className="inv-dot-btn"
                            title="Tùy chọn"
                            onClick={(e) => {
                                e.stopPropagation();
                                const menuId = `menu-${r.productId}`;
                                document.querySelectorAll('.inv-dot-menu').forEach(el => {
                                    if (el.id !== menuId) el.style.display = 'none';
                                });
                                const menu = document.getElementById(menuId);
                                if (menu) menu.style.display = menu.style.display === 'block' ? 'none' : 'block';
                            }}
                        >⋮</button>
                    )}
                    <DotsMenu
                        product={r}
                        onAdjust={() => setAdjustTarget(r)}
                        onHistory={() => setHistoryTarget(r)}
                        onDetail={() => setDetailTarget(r)}
                        canAdjust={canAdjust}
                    />
                </div>
            ),
        },
    ]), [canAdjust]);

    return (
        <div className="admin-page inventory-page">
            <header className="admin-page__header">
                <div>
                    <h1>Tồn kho</h1>
                    <p className="admin-page__sub">{pagination.totalElements} bản ghi</p>
                </div>
            </header>

            <div className="inv-tabs">
                <button
                    type="button"
                    className={`inv-tab ${tab === 'all' ? 'is-active' : ''}`}
                    onClick={() => setTab('all')}
                >Tất cả</button>
                <button
                    type="button"
                    className={`inv-tab ${tab === 'low-stock' ? 'is-active' : ''}`}
                    onClick={() => setTab('low-stock')}
                >Low Stock</button>
            </div>

            {error && (
                <div className="inventory-page__error">
                    <strong>Không thể tải tồn kho.</strong> {error?.message}
                    <button type="button" onClick={refresh}>Thử lại</button>
                </div>
            )}

            <DataTable
                columns={columns}
                rows={items}
                loading={loading}
                rowKey="id"
                emptyText={lowStock ? 'Không có sản phẩm nào sắp hết hàng.' : 'Chưa có dữ liệu tồn kho.'}
            />
            <AdminPagination
                page={pagination.page}
                totalPages={pagination.totalPages}
                onChange={loadPage}
            />

            {adjustTarget && (
                <AdjustModal
                    target={adjustTarget}
                    onClose={() => setAdjustTarget(null)}
                    onDone={() => { setAdjustTarget(null); refresh(); }}
                />
            )}

            {historyTarget && (
                <HistoryModal
                    target={historyTarget}
                    onClose={() => setHistoryTarget(null)}
                    onDone={() => { setHistoryTarget(null); refresh(); }}
                />
            )}

            {detailTarget && (
                <DetailModal
                    target={detailTarget}
                    onClose={() => setDetailTarget(null)}
                />
            )}
        </div>
    );
}

// =============================================================
// DotsMenu — dropdown attached to each row
// =============================================================

function DotsMenu({ product, onAdjust, onHistory, onDetail, canAdjust }) {
    const menuId = `menu-${product.productId}`;

    const handleClick = (action) => {
        document.getElementById(menuId).style.display = 'none';
        action();
    };

    return (
        <div id={menuId} className="inv-dot-menu" style={{ display: 'none' }}>
            {canAdjust && (
                <button type="button" className="inv-dot-menu__item" onClick={() => handleClick(onAdjust)}>
                    <span className="inv-dot-menu__icon">📦</span>
                    Điều chỉnh tồn kho
                </button>
            )}
            <button type="button" className="inv-dot-menu__item" onClick={() => handleClick(onHistory)}>
                <span className="inv-dot-menu__icon">📋</span>
                Lịch sử thay đổi
            </button>
            <button type="button" className="inv-dot-menu__item" onClick={() => handleClick(onDetail)}>
                <span className="inv-dot-menu__icon">🔍</span>
                Chi tiết sản phẩm
            </button>
        </div>
    );
}

// =============================================================
// AdjustModal — unchanged logic, same UI
// =============================================================

function AdjustModal({ target, onClose, onDone }) {
    const [delta, setDelta] = useState('');
    const [reason, setReason] = useState('');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState(null);

    const submit = async (e) => {
        e.preventDefault();
        setError(null);
        const n = parseInt(delta, 10);
        if (!Number.isFinite(n) || n === 0) {
            setError('Delta phải là số nguyên khác 0');
            return;
        }
        if (!reason.trim()) {
            setError('Lý do là bắt buộc');
            return;
        }
        setSaving(true);
        try {
            await adminInventoryApi.adjust(target.productId, n, reason.trim());
            toast.success('Đã điều chỉnh tồn kho');
            onDone();
        } catch (err) {
            const msg = err?.message
                || (typeof err === 'string' ? err : null)
                || (typeof err?.data?.message === 'string' ? err.data.message : null)
                || 'Điều chỉnh thất bại';
            setError(msg);
        } finally {
            setSaving(false);
        }
    };

    const preview = Number.isFinite(parseInt(delta, 10))
        ? `Tồn kho mới ≈ ${formatNumber((target.quantityOnHand || 0) + parseInt(delta, 10))}`
        : null;

    return (
        <Modal title="Điều chỉnh tồn kho" onClose={onClose} width={460}>
            <form onSubmit={submit} className="adjust-modal">
                <div className="adjust-modal__product">
                    <div className="adjust-modal__name">{target.productName}</div>
                    <div className="adjust-modal__sku">SKU: {target.productSku}</div>
                    <div className="adjust-modal__current">
                        Tồn hiện tại: <strong>{formatNumber(target.quantityOnHand)}</strong>
                        {' '}· Khả dụng: <strong>{formatNumber(target.quantityAvailable)}</strong>
                    </div>
                </div>

                <FormField label="Delta (số nguyên)" required htmlFor="adj-delta" hint="Dương = nhập kho, âm = xuất kho / hư hỏng">
                    <input
                        id="adj-delta"
                        type="number"
                        step="1"
                        value={delta}
                        onChange={(e) => setDelta(e.target.value)}
                        placeholder="VD: 10 hoặc -3"
                    />
                </FormField>
                {preview && <p className="adjust-modal__preview">{preview}</p>}

                <FormField label="Lý do" required htmlFor="adj-reason">
                    <textarea
                        id="adj-reason"
                        rows={3}
                        value={reason}
                        onChange={(e) => setReason(e.target.value)}
                        placeholder="VD: Nhập kho đợt 2 / hàng hỏng trong vận chuyển"
                    />
                </FormField>

                {error && <p className="adjust-modal__error">{error?.message || 'Điều chỉnh thất bại'}</p>}

                <div className="adjust-modal__actions">
                    <button type="button" className="btn-cancel" onClick={onClose}>Huỷ</button>
                    <Button type="submit" variant="primary" size="md" loading={saving}>
                        Xác nhận
                    </Button>
                </div>
            </form>
        </Modal>
    );
}

// =============================================================
// HistoryModal — inventory change audit trail
// =============================================================

function HistoryModal({ target, onClose, onDone }) {
    const [entries, setEntries] = useState([]);
    const [pagination, setPagination] = useState({ page: 0, totalPages: 0, totalElements: 0 });
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const loadPage = useCallback(async (page) => {
        setLoading(true);
        setError(null);
        try {
            const data = await adminInventoryApi.getHistory(target.productId, { page, size: 20 });
            setEntries(data.items || []);
            setPagination({
                page: data.page ?? page,
                totalPages: data.totalPages ?? 0,
                totalElements: data.totalElements ?? 0,
            });
        } catch (err) {
            setError(err?.message || 'Không thể tải lịch sử');
        } finally {
            setLoading(false);
        }
    }, [target.productId]);

    useMemo(() => { loadPage(0); }, [loadPage]);

    const operationLabel = (type) => {
        const map = {
            MANUAL_ADJUST: 'Điều chỉnh tay',
            RESERVE: 'Giữ hàng (checkout)',
            RELEASE: 'Giải phóng giữ hàng',
            COMMIT: 'Xác nhận giao (trừ kho)',
        };
        return map[type] || type || '—';
    };

    const operationBadge = (type) => {
        const cls = {
            MANUAL_ADJUST: 'hist-badge--adjust',
            RESERVE: 'hist-badge--reserve',
            RELEASE: 'hist-badge--release',
            COMMIT: 'hist-badge--commit',
        }[type] || '';
        return <span className={`hist-badge ${cls}`}>{operationLabel(type)}</span>;
    };

    return (
        <Modal title={`Lịch sử tồn kho — ${target.productName}`} onClose={onClose} width={680}>
            <div className="history-modal">
                {loading && <p className="history-modal__loading">Đang tải...</p>}
                {error && <p className="history-modal__error">{error}</p>}

                {!loading && !error && entries.length === 0 && (
                    <p className="history-modal__empty">Chưa có lịch sử điều chỉnh nào.</p>
                )}

                {!loading && !error && entries.length > 0 && (
                    <>
                        <table className="history-table">
                            <thead>
                                <tr>
                                    <th>Thời gian</th>
                                    <th>Người thực hiện</th>
                                    <th>Loại</th>
                                    <th className="numeric">Thay đổi</th>
                                    <th className="numeric">Trước</th>
                                    <th className="numeric">Sau</th>
                                    <th>Lý do</th>
                                </tr>
                            </thead>
                            <tbody>
                                {entries.map((e) => (
                                    <tr key={e.id}>
                                        <td className="hist-time">{formatDateTime(e.createdAt)}</td>
                                        <td className="hist-actor">{e.actorName || '—'}</td>
                                        <td>{operationBadge(e.operationType)}</td>
                                        <td className={`numeric ${e.delta > 0 ? 'delta-pos' : e.delta < 0 ? 'delta-neg' : ''}`}>
                                            {e.delta > 0 ? `+${e.delta}` : e.delta}
                                        </td>
                                        <td className="numeric">{formatNumber(e.previousQuantity)}</td>
                                        <td className="numeric">{formatNumber(e.newQuantity)}</td>
                                        <td className="hist-reason">{e.reason || (e.operationType !== 'MANUAL_ADJUST' ? '—' : '')}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>

                        <div className="history-modal__pagination">
                            <button
                                type="button"
                                className="inv-tab"
                                disabled={pagination.page === 0}
                                onClick={() => loadPage(pagination.page - 1)}
                            >← Trang trước</button>
                            <span className="history-modal__page-info">
                                Trang {pagination.page + 1} / {pagination.totalPages || 1}
                                {' '}({pagination.totalElements} bản ghi)
                            </span>
                            <button
                                type="button"
                                className="inv-tab"
                                disabled={pagination.page >= pagination.totalPages - 1}
                                onClick={() => loadPage(pagination.page + 1)}
                            >Trang sau →</button>
                        </div>
                    </>
                )}
            </div>
        </Modal>
    );
}

// =============================================================
// DetailModal — product detail info
// =============================================================

function DetailModal({ target, onClose }) {
    const [detail, setDetail] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useMemo(() => {
        adminInventoryApi.getByProductId(target.productId)
            .then(setDetail)
            .catch((err) => setError(err?.message || 'Không thể tải chi tiết'))
            .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [target.productId]);

    return (
        <Modal title={`Chi tiết sản phẩm — ${target.productName}`} onClose={onClose} width={500}>
            <div className="detail-modal">
                {loading && <p>Đang tải...</p>}
                {error && <p className="history-modal__error">{error}</p>}
                {detail && (
                    <dl className="detail-list">
                        <dt>Tên sản phẩm</dt>
                        <dd>{detail.productName || '—'}</dd>

                        <dt>SKU</dt>
                        <dd>{detail.productSku || '—'}</dd>

                        <dt>Product ID</dt>
                        <dd><code>{detail.productId || '—'}</code></dd>

                        <dt>Tồn kho (On Hand)</dt>
                        <dd><strong>{formatNumber(detail.quantityOnHand)}</strong></dd>

                        <dt>Đã giữ (Reserved)</dt>
                        <dd>{formatNumber(detail.quantityReserved)}</dd>

                        <dt>Khả dụng</dt>
                        <dd>
                            <span className={`inv-pill ${detail.quantityAvailable <= 0 ? 'inv-pill--danger' : detail.quantityAvailable <= detail.lowStockThreshold ? 'inv-pill--warn' : 'inv-pill--ok'}`}>
                                {formatNumber(detail.quantityAvailable)}
                            </span>
                        </dd>

                        <dt>Ngưỡng Low Stock</dt>
                        <dd>{detail.lowStockThreshold}</dd>

                        <dt>Trạng thái</dt>
                        <dd>
                            {detail.isLowStock
                                ? <span className="status-badge status-badge--oos">Low stock</span>
                                : <span className="status-badge status-badge--active">OK</span>}
                        </dd>

                        <dt>Cập nhật lần cuối</dt>
                        <dd>{formatDateTime(detail.updatedAt)}</dd>
                    </dl>
                )}
            </div>
        </Modal>
    );
}
