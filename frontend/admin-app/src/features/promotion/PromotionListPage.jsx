import { useCallback, useEffect, useMemo, useState } from 'react';
import toast from 'react-hot-toast';
import usePageTitle from '../../hooks/usePageTitle.js';
import { adminPromotionsApi } from '../../services/apiAdminPromotions.js';
import {
    Button, DataTable, Modal, FormField, ProgressBar, useConfirmDialog,
} from '../../components/index.js';
import {
    PROMOTION_TYPE, PROMOTION_STATUS,
    promotionTypeLabel, promotionStatusLabel, promotionStatusTone,
} from '../../utils/promotionMeta.js';
import { formatCurrency, formatDate, toDateTimeLocalValue } from '../../utils/format.js';
import './PromotionListPage.css';

/**
 * PromotionListPage
 *
 *   - Top bar: "Tạo voucher" button → opens create modal.
 *   - Table columns: Code, Type/Value, Used/UsageLimit (ProgressBar),
 *     Date range, Status badge, Actions (Sửa / Xoá).
 */
export default function PromotionListPage() {
    usePageTitle('Khuyến mãi');
    const { confirm, dialog } = useConfirmDialog();

    const [items, setItems] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [editing, setEditing] = useState(null); // null | 'new' | promo object

    const load = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await adminPromotionsApi.list();
            setItems(data || []);
        } catch (err) {
            setError(err);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { load(); }, [load]);

    const handleDelete = async (promo) => {
        const ok = await confirm({
            title: 'Xoá voucher?',
            message: `Bạn có chắc muốn xoá mã "${promo.code}"? Nếu mã đã được dùng thì lịch sử đơn hàng vẫn giữ code, nhưng sẽ không dùng được cho đơn mới.`,
            confirmLabel: 'Xoá',
            danger: true,
        });
        if (!ok) return;
        try {
            await adminPromotionsApi.delete(promo.id);
            toast.success('Đã xoá voucher');
            load();
        } catch { /* toast by interceptor */ }
    };

    const columns = useMemo(() => ([
        {
            key: 'code', header: 'Mã', width: 160,
            render: (p) => <span className="promo-row__code">{p.code}</span>,
        },
        {
            key: 'typeValue', header: 'Loại / Giá trị',
            render: (p) => (
                <span className="promo-row__discount">
                    {promotionTypeLabel(p.type)}
                    {p.type === 'PERCENTAGE'
                        ? <> · <strong>{Number(p.value)}%</strong></>
                        : <> · <strong>{formatCurrency(p.value)}</strong></>}
                </span>
            ),
        },
        {
            key: 'minOrder', header: 'Đơn tối thiểu', width: 150, align: 'right',
            render: (p) => (p.minOrderAmount ? formatCurrency(p.minOrderAmount) : '—'),
        },
        {
            key: 'usage', header: 'Sử dụng', width: 200,
            render: (p) => (
                <ProgressBar
                    value={p.usedCount ?? 0}
                    max={p.usageLimit}
                    label={`${p.usedCount ?? 0}${p.usageLimit ? ` / ${p.usageLimit}` : ''}`}
                    tone={(p.usageLimit != null && p.usedCount >= p.usageLimit) ? 'danger' : 'primary'}
                />
            ),
        },
        {
            key: 'dates', header: 'Hiệu lực', width: 220,
            render: (p) => (
                <span className="promo-row__dates">
                    {formatDate(p.startDate)} → {formatDate(p.endDate)}
                </span>
            ),
        },
        {
            key: 'status', header: 'Trạng thái', width: 130,
            render: (p) => {
                const tone = promotionStatusTone(p.status);
                return <span className={`status-badge status-badge--${tone}`}>{promotionStatusLabel(p.status)}</span>;
            },
        },
        {
            key: 'actions', header: '', width: 180, align: 'right',
            render: (p) => (
                <div className="promo-row__actions" onClick={(e) => e.stopPropagation()}>
                    <button type="button" onClick={() => setEditing(p)}>✎ Sửa</button>
                    <button type="button" className="danger" onClick={() => handleDelete(p)}>🗑</button>
                </div>
            ),
        },
    ]), []);

    return (
        <div className="admin-page promotion-list-page">
            <header className="admin-page__header">
                <div>
                    <h1>Khuyến mãi</h1>
                    <p className="admin-page__sub">{items.length} voucher</p>
                </div>
                <Button variant="primary" onClick={() => setEditing('new')}>+ Tạo voucher</Button>
            </header>

            {error && (
                <div className="promotion-list-page__error">
                    <strong>Không thể tải danh sách.</strong> {error?.message}
                    <Button variant="ghost" onClick={load}>Thử lại</Button>
                </div>
            )}

            <DataTable
                columns={columns}
                rows={items}
                loading={loading}
                rowKey="id"
                emptyText="Chưa có voucher nào."
            />

            {editing && (
                <PromotionFormModal
                    initial={editing === 'new' ? null : editing}
                    onClose={() => setEditing(null)}
                    onSaved={() => { setEditing(null); load(); }}
                />
            )}

            {dialog}
        </div>
    );
}

// =============================================================
// PromotionFormModal
// =============================================================

const emptyForm = () => ({
    code: '',
    type: 'PERCENTAGE',
    value: '',
    minOrderAmount: '0',
    maxDiscountAmount: '',
    startDate: '',
    endDate: '',
    usageLimit: '',
    status: 'ACTIVE',
});

function promotionToForm(p) {
    if (!p) return emptyForm();
    return {
        code: p.code || '',
        type: p.type || 'PERCENTAGE',
        value: p.value != null ? String(p.value) : '',
        minOrderAmount: p.minOrderAmount != null ? String(p.minOrderAmount) : '0',
        maxDiscountAmount: p.maxDiscountAmount != null ? String(p.maxDiscountAmount) : '',
        startDate: toDateTimeLocalValue(p.startDate),
        endDate: toDateTimeLocalValue(p.endDate),
        usageLimit: p.usageLimit != null ? String(p.usageLimit) : '',
        status: p.status || 'ACTIVE',
    };
}

function formToPayload(f) {
    const payload = {
        code: (f.code || '').trim().toUpperCase(),
        type: f.type,
        value: parseFloat(f.value),
        status: f.status,
        startDate: f.startDate ? new Date(f.startDate).toISOString() : null,
        endDate: f.endDate ? new Date(f.endDate).toISOString() : null,
    };
    if (f.minOrderAmount !== '' && f.minOrderAmount !== null) {
        payload.minOrderAmount = parseFloat(f.minOrderAmount);
    }
    if (f.maxDiscountAmount !== '' && f.maxDiscountAmount !== null) {
        payload.maxDiscountAmount = parseFloat(f.maxDiscountAmount);
    }
    if (f.usageLimit !== '' && f.usageLimit !== null) {
        payload.usageLimit = parseInt(f.usageLimit, 10);
    }
    return payload;
}

function PromotionFormModal({ initial, onClose, onSaved }) {
    const isEdit = !!initial;
    const [form, setForm] = useState(() => promotionToForm(initial));
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState(null);

    const update = (patch) => setForm((prev) => ({ ...prev, ...patch }));

    const submit = async (e) => {
        e.preventDefault();
        setError(null);

        // Light client-side validation mirroring backend @NotBlank etc.
        if (!form.code || form.code.trim().length < 3) {
            setError('Mã phải có ít nhất 3 ký tự.');
            return;
        }
        if (!form.value || isNaN(parseFloat(form.value)) || parseFloat(form.value) <= 0) {
            setError('Giá trị phải là số dương.');
            return;
        }
        if (!form.startDate || !form.endDate) {
            setError('Vui lòng chọn ngày bắt đầu và kết thúc.');
            return;
        }
        if (new Date(form.startDate) >= new Date(form.endDate)) {
            setError('Ngày kết thúc phải sau ngày bắt đầu.');
            return;
        }
        if (form.type === 'PERCENTAGE' && parseFloat(form.value) > 100) {
            setError('Phần trăm giảm không được vượt quá 100%.');
            return;
        }

        const payload = formToPayload(form);
        setSaving(true);
        try {
            if (isEdit) {
                await adminPromotionsApi.update(initial.id, payload);
                toast.success('Đã cập nhật voucher');
            } else {
                await adminPromotionsApi.create(payload);
                toast.success('Đã tạo voucher');
            }
            onSaved();
        } catch (err) {
            setError(err?.message || 'Lỗi không xác định');
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal
            title={isEdit ? `Sửa voucher: ${initial.code}` : 'Tạo voucher mới'}
            onClose={onClose}
            width={620}
        >
            <form onSubmit={submit} className="promo-form">
                <div className="promo-form__grid">
                    <FormField label="Mã voucher" required htmlFor="promo-code">
                        <input
                            id="promo-code"
                            type="text"
                            value={form.code}
                            onChange={(e) => update({ code: e.target.value.toUpperCase() })}
                            placeholder="VD: SUMMER2026"
                            disabled={isEdit}
                            style={isEdit ? { background: '#f1f5f9' } : undefined}
                        />
                    </FormField>

                    <FormField label="Trạng thái" htmlFor="promo-status">
                        <select
                            id="promo-status"
                            value={form.status}
                            onChange={(e) => update({ status: e.target.value })}
                        >
                            {PROMOTION_STATUS.map((s) => (
                                <option key={s.value} value={s.value}>{s.label}</option>
                            ))}
                        </select>
                    </FormField>

                    <FormField label="Loại" htmlFor="promo-type">
                        <select
                            id="promo-type"
                            value={form.type}
                            onChange={(e) => update({ type: e.target.value })}
                        >
                            {PROMOTION_TYPE.map((t) => (
                                <option key={t.value} value={t.value}>{t.label}</option>
                            ))}
                        </select>
                    </FormField>

                    <FormField
                        label={form.type === 'PERCENTAGE' ? 'Giá trị (% 1-100)' : 'Giá trị (VND)'}
                        required
                        htmlFor="promo-value"
                    >
                        <input
                            id="promo-value"
                            type="number"
                            step={form.type === 'PERCENTAGE' ? '1' : '1000'}
                            min="0"
                            value={form.value}
                            onChange={(e) => update({ value: e.target.value })}
                            placeholder={form.type === 'PERCENTAGE' ? 'VD: 10' : 'VD: 50000'}
                        />
                    </FormField>

                    <FormField label="Đơn tối thiểu (VND)" htmlFor="promo-min">
                        <input
                            id="promo-min"
                            type="number"
                            step="1000"
                            min="0"
                            value={form.minOrderAmount}
                            onChange={(e) => update({ minOrderAmount: e.target.value })}
                            placeholder="0 = không yêu cầu"
                        />
                    </FormField>

                    {form.type === 'PERCENTAGE' && (
                        <FormField label="Giảm tối đa (VND)" htmlFor="promo-max">
                            <input
                                id="promo-max"
                                type="number"
                                step="1000"
                                min="0"
                                value={form.maxDiscountAmount}
                                onChange={(e) => update({ maxDiscountAmount: e.target.value })}
                                placeholder="Bỏ trống = không giới hạn"
                            />
                        </FormField>
                    )}

                    <FormField label="Ngày bắt đầu" required htmlFor="promo-start">
                        <input
                            id="promo-start"
                            type="datetime-local"
                            value={form.startDate}
                            onChange={(e) => update({ startDate: e.target.value })}
                        />
                    </FormField>

                    <FormField label="Ngày kết thúc" required htmlFor="promo-end">
                        <input
                            id="promo-end"
                            type="datetime-local"
                            value={form.endDate}
                            onChange={(e) => update({ endDate: e.target.value })}
                        />
                    </FormField>

                    <FormField label="Giới hạn lượt dùng" htmlFor="promo-limit"
                        hint="Bỏ trống = không giới hạn">
                        <input
                            id="promo-limit"
                            type="number"
                            min="0"
                            value={form.usageLimit}
                            onChange={(e) => update({ usageLimit: e.target.value })}
                            placeholder="VD: 100"
                        />
                    </FormField>
                </div>

                {error && <div className="promo-form__error">{error}</div>}

                <div className="promo-form__actions">
                    <Button variant="ghost" onClick={onClose} disabled={saving}>Huỷ</Button>
                    <Button variant="primary" type="submit" loading={saving}>
                        {isEdit ? 'Lưu thay đổi' : 'Tạo voucher'}
                    </Button>
                </div>
            </form>
        </Modal>
    );
}
