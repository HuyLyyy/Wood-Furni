import { useEffect, useState, useMemo } from 'react';
import toast from 'react-hot-toast';
import { deliveryApi } from '../../services/apiDelivery.js';
import { formatDateTime, toIsoVnDateTimeLocal } from '../../utils/format.js';
import { Button } from '../../components/index.js';
import './DeliveryPage.css';
import RescheduleTripModal from './RescheduleTripModal.jsx';

const TRIP_STATUS_LABELS = {
    PLANNING:    { label: 'Lên kế hoạch',  cls: 'status--planning' },
    SHIPPING:    { label: 'Đang giao',      cls: 'status--shipping' },
    ARRIVED:     { label: 'Đã đến nơi',     cls: 'status--arrived' },
    RESCHEDULED: { label: 'Hoãn giao',      cls: 'status--rescheduled' },
    COMPLETED:   { label: 'Hoàn thành',     cls: 'status--completed' },
    CANCELLED:   { label: 'Đã hủy',         cls: 'status--cancelled' },
};

const ORDER_STATE_LABELS = {
    PENDING:      { label: 'Chờ giao',   cls: 'order-state--pending' },
    DELIVERED:    { label: 'Đã giao',    cls: 'order-state--delivered' },
    RESCHEDULED:  { label: 'Hoãn giao',  cls: 'order-state--rescheduled' },
    FAILED:       { label: 'Thất bại',   cls: 'order-state--failed' },
    UNREACHABLE:  { label: 'Không liên lạc', cls: 'order-state--unreachable' },
};

const REASON_LABELS = {
    BUSY:         'Khách có việc bận đột xuất',
    NOT_READY:    'Khách chưa chuẩn bị không gian',
    REQUEST_TIME: 'Khách yêu cầu giao giờ khác',
    INACCESSIBLE: 'Địa chỉ không tiếp cận được',
    UNREACHABLE:  'Không liên lạc được',
    OTHER:        'Khác (ghi rõ bên dưới)',
};

function TripStatusBadge({ status }) {
    const info = TRIP_STATUS_LABELS[status] || { label: status, cls: '' };
    return <span className={`trip-status-badge ${info.cls}`}>{info.label}</span>;
}

function OrderStateBadge({ state }) {
    const info = ORDER_STATE_LABELS[state] || { label: state || '—', cls: '' };
    return <span className={`order-state-badge ${info.cls}`}>{info.label}</span>;
}

function todayPlus(days) {
    const d = new Date();
    d.setDate(d.getDate() + days);
    d.setHours(10, 0, 0, 0);
    return d.toISOString().slice(0, 16);
}

/**
 * Modal xem chi tiết chuyến xe + xử lý từng đơn theo luồng hoãn giao hàng.
 *
 * Props:
 *   tripId   - id chuyến xe cần xem
 *   onClose  - đóng modal
 *   onChange - gọi lại sau khi xử lý xong 1 đơn (parent refresh danh sách)
 */
export default function TripDetailModal({ tripId, onClose, onChange }) {
    const [trip, setTrip] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [busyAction, setBusyAction] = useState(false);
    const [activeOrder, setActiveOrder] = useState(null); // đơn đang mở form
    const [actionType, setActionType] = useState(null);   // 'deliver' | 'unreachable' | 'reschedule' | 'fail'
    const [form, setForm] = useState({});
    const [rescheduleModalOpen, setRescheduleModalOpen] = useState(false);

    const load = async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await deliveryApi.getTripDetail(tripId);
            setTrip(data);
        } catch (e) {
            setError(e?.message || 'Không thể tải chi tiết chuyến xe');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { load(); /* eslint-disable-next-line */ }, [tripId]);

    // ── Action: tài xế đến nơi (SHIPPING → ARRIVED) ────────────────────
    const handleArrive = async () => {
        setBusyAction(true);
        try {
            await deliveryApi.arriveAtLocation(tripId);
            toast.success('Đã đánh dấu đến nơi.');
            await load();
            onChange?.();
        } catch (e) {
            toast.error(e?.message || 'Thất bại');
        } finally {
            setBusyAction(false);
        }
    };

    // ── Mở form xử lý 1 đơn ────────────────────────────────────────────
    const openOrderAction = (order, type) => {
        setActiveOrder(order);
        setActionType(type);
        if (type === 'reschedule') {
            setForm({
                reason: 'BUSY',
                customReason: '',
                nextDeliveryDate: todayPlus(1),
                preferredTimeWindow: '',
                note: '',
            });
        } else if (type === 'fail') {
            setForm({ reason: 'INACCESSIBLE', customReason: '', note: '' });
        } else if (type === 'unreachable') {
            setForm({ note: '', proofPhotoUrl: '' });
        } else if (type === 'deliver') {
            setForm({ codCollected: true, receiverName: '', note: '', proofPhotoUrl: '' });
        }
    };

    const closeForm = () => {
        setActiveOrder(null);
        setActionType(null);
        setForm({});
    };

    // ── Submit form xử lý đơn ───────────────────────────────────────────
    const submitOrderAction = async () => {
        if (!activeOrder) return;
        setBusyAction(true);
        try {
            let result;
            if (actionType === 'deliver') {
                result = await deliveryApi.markOrderDelivered(tripId, activeOrder.orderId, {
                    codCollected: form.codCollected,
                    receiverName: form.receiverName || null,
                    note: form.note || null,
                    proofPhotoUrl: form.proofPhotoUrl || null,
                });
                toast.success(`Đã giao đơn ${activeOrder.orderNumber}.`);
            } else if (actionType === 'unreachable') {
                result = await deliveryApi.markOrderUnreachable(tripId, activeOrder.orderId, {
                    note: form.note || null,
                    proofPhotoUrl: form.proofPhotoUrl || null,
                });
                toast.success('Đã đánh dấu không liên lạc được.');
            } else if (actionType === 'reschedule') {
                if (form.reason === 'OTHER' && !form.customReason?.trim()) {
                    toast.error('Vui lòng nhập mô tả cụ thể.');
                    setBusyAction(false);
                    return;
                }
                result = await deliveryApi.rescheduleOrder(tripId, activeOrder.orderId, {
                    reason: form.reason,
                    customReason: form.customReason || null,
                    // Dùng helper đã có sẵn — convert datetime-local (VN wall-clock)
                    // sang ISO với offset +07:00. Tránh bug "1970" do browser parse
                    // sai timezone.
                    nextDeliveryDate: toIsoVnDateTimeLocal(form.nextDeliveryDate),
                    preferredTimeWindow: form.preferredTimeWindow || null,
                    note: form.note || null,
                    proofPhotoUrl: null,
                });
                if (result?.warning) {
                    toast(result.warningMessage || 'Khách đã hoãn nhiều lần — CSKH can thiệp.', {
                        icon: '⚠️', duration: 6000,
                    });
                } else {
                    toast.success(`Đã hoãn đơn ${activeOrder.orderNumber}.`);
                }
            } else if (actionType === 'fail') {
                if (form.reason === 'OTHER' && !form.customReason?.trim()) {
                    toast.error('Vui lòng nhập mô tả cụ thể.');
                    setBusyAction(false);
                    return;
                }
                result = await deliveryApi.markOrderFailed(tripId, activeOrder.orderId, {
                    reason: form.reason,
                    customReason: form.customReason || null,
                    note: form.note || null,
                    proofPhotoUrl: null,
                });
                toast.success(`Đã đánh dấu giao thất bại ${activeOrder.orderNumber}.`);
            }
            await load();
            onChange?.();
            closeForm();
        } catch (e) {
            toast.error(e?.message || 'Thất bại');
        } finally {
            setBusyAction(false);
        }
    };

    const tripStatus = trip?.status;
    const canArrive = tripStatus === 'SHIPPING';
    const isActive = tripStatus === 'SHIPPING' || tripStatus === 'ARRIVED' || tripStatus === 'RESCHEDULED';
    // Trip RESCHEDULED mà còn đơn RESCHEDULED → cho phép tạo trip giao lại.
    const hasRescheduleOrders = (trip?.rescheduledCount || 0) > 0;
    const canCreateRescheduleTrip = tripStatus === 'RESCHEDULED' && hasRescheduleOrders;

    const tripActions = useMemo(() => {
        if (!trip) return null;
        return (
            <div className="trip-detail-actions">
                {canArrive && (
                    <Button variant="primary" size="sm" disabled={busyAction} onClick={handleArrive}>
                        Đã đến địa điểm giao
                    </Button>
                )}
                {canCreateRescheduleTrip && (
                    <Button
                        variant="primary"
                        size="sm"
                        disabled={busyAction}
                        onClick={() => setRescheduleModalOpen(true)}
                        title="Tạo chuyến xe mới để giao lại các đơn đang hoãn"
                    >
                        🚚 Tạo trip giao lại
                    </Button>
                )}
            </div>
        );
    }, [trip, busyAction, canArrive, canCreateRescheduleTrip]);

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-card modal-card--wide" onClick={(e) => e.stopPropagation()}>
                {loading && <p style={{ padding: 24 }}>Đang tải…</p>}
                {error && <p style={{ padding: 24, color: '#b91c1c' }}>{error}</p>}

                {trip && (
                    <>
                        <div className="trip-detail-header">
                            <div>
                                <h3>{trip.tripNumber}</h3>
                                <div style={{ marginTop: 6 }}>
                                    <TripStatusBadge status={tripStatus} />
                                    {trip.arrivedAt && (
                                        <span style={{ marginLeft: 8, fontSize: 12, color: '#64748b' }}>
                                            • Đến nơi: {formatDateTime(trip.arrivedAt)}
                                        </span>
                                    )}
                                </div>
                            </div>
                            <button className="modal-close-btn" onClick={onClose} aria-label="Đóng">×</button>
                        </div>

                        <div className="trip-detail-meta">
                            <div><strong>Tài xế:</strong> {trip.driverName || '—'}</div>
                            <div><strong>Loại xe:</strong> {trip.vehicle?.displayName || trip.vehicleTypeName || '—'}</div>
                            <div><strong>Tổng đơn:</strong> {trip.totalOrders}</div>
                            {trip.rescheduleCount > 0 && (
                                <div style={{ color: '#b45309' }}>
                                    <strong>Đã hoãn:</strong> {trip.rescheduleCount} lần
                                </div>
                            )}
                        </div>

                        {trip.deliveredCount > 0 && (
                            <div className="trip-detail-counters">
                                <span className="counter counter--delivered">Đã giao: {trip.deliveredCount}</span>
                                <span className="counter counter--rescheduled">Hoãn: {trip.rescheduledCount}</span>
                                <span className="counter counter--failed">Thất bại: {trip.failedCount}</span>
                                <span className="counter counter--pending">Chờ: {trip.pendingCount}</span>
                            </div>
                        )}

                        {tripActions}

                        <h4 style={{ marginTop: 18, marginBottom: 8 }}>Danh sách đơn ({trip.orders?.length || 0})</h4>
                        <div className="trip-orders-wrap">
                            <table className="trip-orders-table">
                                <thead>
                                    <tr>
                                        <th style={{ width: 30 }}>#</th>
                                        <th>Số đơn</th>
                                        <th>Khách</th>
                                        <th>Trạng thái giao</th>
                                        <th>Lý do / Ghi chú</th>
                                        {isActive && <th style={{ width: 280 }}>Hành động</th>}
                                    </tr>
                                </thead>
                                <tbody>
                                    {trip.orders?.map((o) => (
                                        <tr key={o.orderId}>
                                            <td>{o.sequence}</td>
                                            <td><strong>{o.orderNumber}</strong></td>
                                            <td>
                                                <div>{o.customerName || '—'}</div>
                                                <div style={{ fontSize: 11, color: '#64748b' }}>{o.customerPhone || ''}</div>
                                            </td>
                                            <td><OrderStateBadge state={o.deliveryState} /></td>
                                            <td style={{ fontSize: 12 }}>
                                                {o.rescheduleReason && <div><strong>Lý do:</strong> {REASON_LABELS[o.rescheduleReason] || o.rescheduleReason}</div>}
                                                {o.deliveryNote && <div><strong>Note:</strong> {o.deliveryNote}</div>}
                                                {o.rescheduleCount > 0 && <div style={{ color: '#b45309' }}>Đã hoãn {o.rescheduleCount} lần</div>}
                                                {o.nextDeliveryDate && <div>Giao lại: {formatDateTime(o.nextDeliveryDate)}</div>}
                                            </td>
                                            {isActive && (
                                                <td>
                                                    {o.deliveryState === 'PENDING' || o.deliveryState === 'UNREACHABLE' ? (
                                                        <div className="row-actions" style={{ flexWrap: 'wrap' }}>
                                                            <button
                                                                className="btn-row btn-row--success"
                                                                disabled={busyAction}
                                                                onClick={() => openOrderAction(o, 'deliver')}
                                                            >Giao thành công</button>
                                                            <button
                                                                className="btn-row btn-row--warning"
                                                                disabled={busyAction}
                                                                onClick={() => openOrderAction(o, 'reschedule')}
                                                            >Hoãn giao</button>
                                                            <button
                                                                className="btn-row btn-row--secondary"
                                                                disabled={busyAction}
                                                                onClick={() => openOrderAction(o, 'unreachable')}
                                                            >Không liên lạc</button>
                                                            <button
                                                                className="btn-row btn-row--danger"
                                                                disabled={busyAction}
                                                                onClick={() => openOrderAction(o, 'fail')}
                                                            >Thất bại</button>
                                                        </div>
                                                    ) : (
                                                        <span style={{ fontSize: 12, color: '#94a3b8' }}>
                                                            {o.deliveryState === 'DELIVERED' ? '✓ Đã giao' :
                                                             o.deliveryState === 'RESCHEDULED' ? '⏳ Chờ giao lại' :
                                                             o.deliveryState === 'FAILED' ? '✗ Thất bại' : '—'}
                                                        </span>
                                                    )}
                                                </td>
                                            )}
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </>
                )}

                {/* Form xử lý 1 đơn */}
                {activeOrder && actionType && (
                    <div className="modal-overlay modal-overlay--inner" onClick={closeForm}>
                        <div className="modal-card modal-card--inner" onClick={(e) => e.stopPropagation()}>
                            <h3 style={{ marginTop: 0 }}>
                                {actionType === 'deliver' && `Giao thành công ${activeOrder.orderNumber}`}
                                {actionType === 'unreachable' && `Không liên lạc được — ${activeOrder.orderNumber}`}
                                {actionType === 'reschedule' && `Hoãn giao đơn ${activeOrder.orderNumber}`}
                                {actionType === 'fail' && `Giao thất bại — ${activeOrder.orderNumber}`}
                            </h3>

                            {actionType === 'deliver' && (
                                <>
                                    <label className="modal-label">Người nhận thực tế (tùy chọn)
                                        <input type="text" value={form.receiverName || ''}
                                               onChange={(e) => setForm({ ...form, receiverName: e.target.value })}
                                               style={{ width: '100%', padding: 8, marginTop: 4 }} />
                                    </label>
                                    <label className="modal-label">Đã thu COD?
                                        <select value={String(form.codCollected)}
                                                onChange={(e) => setForm({ ...form, codCollected: e.target.value === 'true' })}
                                                style={{ width: '100%', padding: 8, marginTop: 4 }}>
                                            <option value="true">Có (đã thu tiền)</option>
                                            <option value="false">Không</option>
                                        </select>
                                    </label>
                                    <label className="modal-label">Ghi chú
                                        <textarea rows={2} value={form.note || ''}
                                                  onChange={(e) => setForm({ ...form, note: e.target.value })}
                                                  style={{ width: '100%', padding: 8, marginTop: 4 }} />
                                    </label>
                                </>
                            )}

                            {actionType === 'unreachable' && (
                                <>
                                    <label className="modal-label">Ghi chú (VD: đã gọi 3 lần không nghe)
                                        <textarea rows={3} value={form.note || ''}
                                                  onChange={(e) => setForm({ ...form, note: e.target.value })}
                                                  style={{ width: '100%', padding: 8, marginTop: 4 }} />
                                    </label>
                                    <p style={{ fontSize: 12, color: '#64748b' }}>
                                        Đơn sẽ được đánh dấu tạm thời. Bạn có thể tiếp tục gọi lại hoặc đánh dấu
                                        <strong> Hoãn giao</strong> / <strong>Thất bại</strong> sau.
                                    </p>
                                </>
                            )}

                            {(actionType === 'reschedule' || actionType === 'fail') && (
                                <>
                                    <label className="modal-label">Lý do
                                        <select value={form.reason}
                                                onChange={(e) => setForm({ ...form, reason: e.target.value })}
                                                style={{ width: '100%', padding: 8, marginTop: 4 }}>
                                            {Object.entries(REASON_LABELS).map(([k, v]) => (
                                                <option key={k} value={k}>{v}</option>
                                            ))}
                                        </select>
                                    </label>
                                    {form.reason === 'OTHER' && (
                                        <label className="modal-label">Mô tả cụ thể
                                            <input type="text" value={form.customReason || ''}
                                                   onChange={(e) => setForm({ ...form, customReason: e.target.value })}
                                                   style={{ width: '100%', padding: 8, marginTop: 4 }} />
                                        </label>
                                    )}
                                    {actionType === 'reschedule' && (
                                        <>
                                            <label className="modal-label">Thời gian giao lại
                                                <input type="datetime-local" value={form.nextDeliveryDate}
                                                       onChange={(e) => setForm({ ...form, nextDeliveryDate: e.target.value })}
                                                       style={{ width: '100%', padding: 8, marginTop: 4 }} />
                                            </label>
                                            <label className="modal-label">Khung giờ mong muốn (tùy chọn)
                                                <input type="text" placeholder="VD: 09:00 - 12:00"
                                                       value={form.preferredTimeWindow || ''}
                                                       onChange={(e) => setForm({ ...form, preferredTimeWindow: e.target.value })}
                                                       style={{ width: '100%', padding: 8, marginTop: 4 }} />
                                            </label>
                                        </>
                                    )}
                                    <label className="modal-label">Ghi chú
                                        <textarea rows={2} value={form.note || ''}
                                                  onChange={(e) => setForm({ ...form, note: e.target.value })}
                                                  style={{ width: '100%', padding: 8, marginTop: 4 }} />
                                    </label>
                                </>
                            )}

                            <div className="modal-actions">
                                <Button variant="outline" size="sm" onClick={closeForm} disabled={busyAction}>Hủy</Button>
                                <Button variant="primary" size="sm" onClick={submitOrderAction} disabled={busyAction}>
                                    {busyAction ? 'Đang xử lý…' : 'Xác nhận'}
                                </Button>
                            </div>
                        </div>
                    </div>
                )}

                {/* Modal tạo trip giao lại (chỉ mở khi trip RESCHEDULED có đơn hoãn) */}
                {rescheduleModalOpen && (
                    <RescheduleTripModal
                        onClose={() => setRescheduleModalOpen(false)}
                        onDone={async (newTripId) => {
                            setRescheduleModalOpen(false);
                            await load();
                            onChange?.();
                            if (newTripId) {
                                toast.success('Đã tạo chuyến giao lại. Đang chuyển sang chuyến mới…');
                            }
                        }}
                    />
                )}
            </div>
        </div>
    );
}
