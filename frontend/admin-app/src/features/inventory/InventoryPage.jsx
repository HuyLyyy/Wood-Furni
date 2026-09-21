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

// ─────────────────────────────────────────────────────────────────────────────
// Fixed adjustment reasons (matches backend AdjustmentReason enum).
// Order: damage → loss → return → stocktake → liquidation (increasing severity).
// ─────────────────────────────────────────────────────────────────────────────
const ADJUSTMENT_REASONS = [
    { code: 'DAMAGE_STOCK',       label: 'Hàng hư hỏng tồn kho',           hint: 'Mối mọt, ẩm mốc, vỡ khi lưu kho' },
    { code: 'LOSS_THEFT',          label: 'Hàng mất mát',                  hint: 'Thất lạc, trộm trong kho / vận chuyển' },
    { code: 'CUSTOMER_RETURN',     label: 'Hàng trả lại từ khách',          hint: 'Khách đổi / trả — nhập lại kho hoặc loại bỏ' },
    { code: 'STOCKTAKE_VARIANCE',  label: 'Kiểm kê phát hiện chênh lệch',  hint: 'Sổ sách ≠ thực tế khi kiểm kê định kỳ' },
    { code: 'LIQUIDATION',         label: 'Thanh lý hàng tồn kho',          hint: 'Hết mẫu, hết vòng đời sản phẩm, bán thanh lý' },
];

const MAX_FILE_BYTES = 10 * 1024 * 1024; // 10 MB
const ALLOWED_EXTENSIONS = ['.xlsx', '.xls'];

function validateFile(file) {
    if (!file) return 'Vui lòng chọn file minh chứng.';
    if (file.size > MAX_FILE_BYTES) return 'File vượt quá 10 MB.';
    const ext = '.' + file.name.split('.').pop().toLowerCase();
    if (!ALLOWED_EXTENSIONS.includes(ext)) return 'Chỉ chấp nhận file Excel (.xlsx, .xls).';
    return null;
}

// ─────────────────────────────────────────────────────────────────────────────
// AdjustModal
// ─────────────────────────────────────────────────────────────────────────────

function AdjustModal({ target, onClose, onDone }) {
    const [delta, setDelta] = useState('');
    const [selectedReason, setSelectedReason] = useState(''); // reasonCode string
    const [note, setNote] = useState('');
    const [evidence, setEvidence] = useState(null); // File | null
    const [fileError, setFileError] = useState(null);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState(null);

    // Drag-over highlight
    const [dragging, setDragging] = useState(false);

    const handleFileChange = (file) => {
        if (!file) return;
        const err = validateFile(file);
        setFileError(err);
        setEvidence(err ? null : file);
    };

    const submit = async (e) => {
        e.preventDefault();
        setError(null);

        const n = parseInt(delta, 10);
        if (!Number.isFinite(n) || n === 0) {
            setError('Delta phải là số nguyên khác 0.');
            return;
        }
        if (!selectedReason) {
            setError('Vui lòng chọn lý do điều chỉnh.');
            return;
        }
        if (!evidence) {
            setError('File minh chứng (.xlsx / .xls) là bắt buộc.');
            return;
        }

        setSaving(true);
        try {
            await adminInventoryApi.adjust(target.productId, {
                reasonCode: selectedReason,
                delta: n,
                note: note.trim() || undefined,
            }, evidence);
            toast.success('Đã điều chỉnh tồn kho');
            onDone();
        } catch (err) {
            // Extract backend error message
            const msg = err?.message
                || (typeof err?.data?.message === 'string' ? err.data.message : null)
                || 'Điều chỉnh thất bại. Vui lòng thử lại.';
            setError(msg);
        } finally {
            setSaving(false);
        }
    };

    const preview = Number.isFinite(parseInt(delta, 10))
        ? `Tồn kho mới ≈ ${formatNumber((target.quantityOnHand || 0) + parseInt(delta, 10))}`
        : null;

    const selectedReasonObj = ADJUSTMENT_REASONS.find(r => r.code === selectedReason);

    return (
        <Modal title="Điều chỉnh tồn kho" onClose={onClose} width={500}>
            <form onSubmit={submit} className="adjust-modal">
                {/* Product info */}
                <div className="adjust-modal__product">
                    <div className="adjust-modal__name">{target.productName}</div>
                    <div className="adjust-modal__sku">SKU: {target.productSku}</div>
                    <div className="adjust-modal__current">
                        Tồn hiện tại: <strong>{formatNumber(target.quantityOnHand)}</strong>
                        {' '}· Khả dụng: <strong>{formatNumber(target.quantityAvailable)}</strong>
                    </div>
                </div>

                {/* Delta */}
                <FormField label="Thay đổi số lượng" required htmlFor="adj-delta"
                    hint="Dương = nhập kho, Âm = xuất kho (hư hỏng, mất, thanh lý…)">
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

                {/* Reason checklist */}
                <div className="adj-reason-section">
                    <div className="adj-section-label">
                        Lý do điều chỉnh <span className="required-star">*</span>
                    </div>
                    <div className="adj-reason-list" role="radiogroup" aria-label="Lý do điều chỉnh tồn kho">
                        {ADJUSTMENT_REASONS.map((r) => (
                            <label
                                key={r.code}
                                className={`adj-reason-item ${selectedReason === r.code ? 'is-selected' : ''}`}
                                title={r.hint}
                            >
                                <input
                                    type="radio"
                                    name="adjust-reason"
                                    value={r.code}
                                    checked={selectedReason === r.code}
                                    onChange={() => setSelectedReason(r.code)}
                                    className="adj-reason-radio"
                                />
                                <div className="adj-reason-content">
                                    <div className="adj-reason-label">{r.label}</div>
                                    {selectedReason === r.code && (
                                        <div className="adj-reason-hint">{r.hint}</div>
                                    )}
                                </div>
                            </label>
                        ))}
                    </div>
                </div>

                {/* Note (optional) */}
                <FormField label="Ghi chú thêm" htmlFor="adj-note"
                    hint="Tùy chọn — bổ sung thông tin nếu cần">
                    <textarea
                        id="adj-note"
                        rows={2}
                        value={note}
                        onChange={(e) => setNote(e.target.value)}
                        placeholder="VD: 5 cái bàn gỗ bị mối ăn trong kho tháng 6…"
                        maxLength={500}
                    />
                </FormField>

                {/* Evidence file upload */}
                <div className="adj-evidence-section">
                    <div className="adj-section-label">
                        File minh chứng <span className="required-star">*</span>
                        <span className="adj-section-sub">.xlsx, .xls · tối đa 10 MB</span>
                    </div>

                    {!evidence ? (
                        <div
                            className={`adj-drop-zone ${dragging ? 'is-dragging' : ''} ${fileError ? 'has-error' : ''}`}
                            onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
                            onDragLeave={() => setDragging(false)}
                            onDrop={(e) => {
                                e.preventDefault();
                                setDragging(false);
                                handleFileChange(e.dataTransfer.files[0]);
                            }}
                            onClick={() => document.getElementById('adj-evidence-input').click()}
                        >
                            <div className="adj-drop-icon">📎</div>
                            <div className="adj-drop-text">
                                Kéo thả file Excel vào đây<br />
                                hoặc <span className="adj-drop-link">bấm để chọn file</span>
                            </div>
                            <div className="adj-drop-sub">.xlsx · .xls · ≤ 10 MB</div>
                        </div>
                    ) : (
                        <div className="adj-file-info">
                            <div className="adj-file-icon">📊</div>
                            <div className="adj-file-details">
                                <div className="adj-file-name">{evidence.name}</div>
                                <div className="adj-file-size">{(evidence.size / 1024).toFixed(1)} KB</div>
                            </div>
                            <button
                                type="button"
                                className="adj-file-remove"
                                onClick={() => { setEvidence(null); setFileError(null); }}
                                title="Gỡ file"
                            >✕</button>
                        </div>
                    )}
                    {fileError && <p className="adj-upload-error">{fileError}</p>}
                    <input
                        id="adj-evidence-input"
                        type="file"
                        accept=".xlsx,.xls,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel"
                        style={{ display: 'none' }}
                        onChange={(e) => handleFileChange(e.target.files[0])}
                    />
                </div>

                {error && <p className="adjust-modal__error">{error}</p>}

                <div className="adjust-modal__actions">
                    <button type="button" className="btn-cancel" onClick={onClose}>Huỷ</button>
                    <Button type="submit" variant="primary" size="md" loading={saving}>
                        Xác nhận điều chỉnh
                    </Button>
                </div>
            </form>
        </Modal>
    );
}

// ─────────────────────────────────────────────────────────────────────────────
// Reason code → display label (mirrors backend AdjustmentReason + describeReason).
// ─────────────────────────────────────────────────────────────────────────────
const REASON_LABELS = {
    DAMAGE_STOCK:      { label: 'Hàng hư hỏng tồn kho',           icon: '⚠️' },
    LOSS_THEFT:        { label: 'Hàng mất mát',                   icon: '🔎' },
    CUSTOMER_RETURN:   { label: 'Hàng trả lại từ khách',           icon: '↩️' },
    STOCKTAKE_VARIANCE:{ label: 'Kiểm kê phát hiện chênh lệch',  icon: '📋' },
    LIQUIDATION:       { label: 'Thanh lý hàng tồn kho',          icon: '🗑️' },
};

function reasonChip(code) {
    if (!code) return null;
    const info = REASON_LABELS[code];
    if (!info) return <span className="hist-reason-badge hist-reason-badge--other">{code}</span>;
    return (
        <span className="hist-reason-badge" title={code}>
            {info.icon} {info.label}
        </span>
    );
}

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
                                    <th>Minh chứng</th>
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
                                        <td className="hist-reason">
                                            {e.reasonCode && reasonChip(e.reasonCode)}
                                            {e.reason && (
                                                <span className="hist-note">{e.reason}</span>
                                            )}
                                            {!e.reasonCode && !e.reason && e.operationType === 'MANUAL_ADJUST' && '—'}
                                            {!e.reasonCode && !e.reason && e.operationType !== 'MANUAL_ADJUST' && '—'}
                                        </td>
                                        <td className="hist-evidence">
                                            {e.evidenceUrl ? (
                                                <a
                                                    href={e.evidenceUrl}
                                                    target="_blank"
                                                    rel="noopener noreferrer"
                                                    className="hist-evidence-link"
                                                    title={e.evidenceOriginalName}
                                                    download={e.evidenceOriginalName}
                                                >
                                                    📥 Tải (.xlsx)
                                                </a>
                                            ) : '—'}
                                        </td>
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
