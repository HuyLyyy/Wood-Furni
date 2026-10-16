import { useCallback, useEffect, useMemo, useState } from 'react';
import toast from 'react-hot-toast';
import { Modal, Button } from '../../components/index.js';
import { deliveryApi, userApi } from '../../services/apiDelivery.js';
import { formatNumber, formatDateTime } from '../../utils/format.js';
import './CreateTripModal.css';

const STEPS = [
    { id: 1, label: '1. Chọn đơn hoãn' },
    { id: 2, label: '2. Chọn xe & kiểm tra' },
    { id: 3, label: '3. Chọn tài xế & tạo' },
];

const VEHICLE_ICONS = {
    VAN_SMALL:    '🚐',
    TRUCK_LIGHT:   '🚚',
    TRUCK_MEDIUM: '🚛',
};

const REASON_LABELS = {
    BUSY:         'Khách có việc bận đột xuất',
    NOT_READY:    'Khách chưa chuẩn bị không gian',
    REQUEST_TIME: 'Khách yêu cầu giao giờ khác',
    INACCESSIBLE: 'Địa chỉ không tiếp cận được',
    UNREACHABLE:  'Không liên lạc được',
    OTHER:        'Khác',
};

function VehicleCard({ vehicle, selected, onSelect }) {
    if (!vehicle) return null;
    const icon = VEHICLE_ICONS[vehicle.code] || '🚗';
    return (
        <label className={`vehicle-card ${selected ? 'is-selected' : ''}`}>
            <input
                type="radio"
                name="vehicle"
                value={vehicle.code}
                checked={selected}
                onChange={() => onSelect(vehicle.code)}
            />
            <div className="vehicle-card__inner">
                <div className="vehicle-card__icon">{icon}</div>
                <div className="vehicle-card__name">{vehicle.displayName}</div>
                <div className="vehicle-card__specs">
                    <span>🚛 {vehicle.minWeightKg.toFixed(0)}–{vehicle.maxWeightKg.toFixed(0)} kg</span>
                    <span>📦 {vehicle.maxVolumeM3.toFixed(1)} m³</span>
                    <span>📏 {vehicle.maxLengthCm.toFixed(0)}×{vehicle.maxWidthCm.toFixed(0)}×{vehicle.maxHeightCm.toFixed(0)} cm</span>
                </div>
            </div>
        </label>
    );
}

function CapacityResult({ result }) {
    if (!result) return null;
    const { fits, reasons = [] } = result;
    const warnReasons = reasons.filter(r => r.startsWith('⚠️'));
    const blockReasons = reasons.filter(r => !r.startsWith('⚠️'));

    return (
        <div className={`capacity-result ${fits ? 'capacity-result--fits' : 'capacity-result--no-fit'}`}>
            <div className="capacity-result__header">
                {fits
                    ? <span className="capacity-result__ok">✅ Lọt xe — có thể tạo chuyến</span>
                    : <span className="capacity-result__fail">❌ Không lọt xe — vui lòng điều chỉnh</span>
                }
            </div>
            {blockReasons.length > 0 && (
                <ul className="capacity-result__reasons capacity-result__reasons--block">
                    {blockReasons.map((r, i) => <li key={i}>{r}</li>)}
                </ul>
            )}
            {warnReasons.length > 0 && (
                <ul className="capacity-result__reasons capacity-result__reasons--warn">
                    {warnReasons.map((r, i) => <li key={i}>{r}</li>)}
                </ul>
            )}
            {fits && reasons.length === 0 && (
                <p className="capacity-result__msg">Mọi tiêu chí đều đạt. Sẵn sàng tạo chuyến!</p>
            )}
        </div>
    );
}

/**
 * Modal tạo chuyến xe giao lại từ các đơn đang RESCHEDULED.
 * Reuse logic từ CreateTripModal nhưng:
 *  - Danh sách đơn lấy từ API mới /delivery/reschedule-orders
 *    (chỉ đơn RESCHEDULED, có thêm thông tin hoãn).
 *  - Submit gọi /delivery/trips/reschedule.
 *
 * Props:
 *   onClose - đóng modal
 *   onDone  - gọi khi tạo thành công (nhận newTripId để parent navigate nếu muốn)
 */
export default function RescheduleTripModal({ onClose, onDone }) {
    const [step, setStep] = useState(1);

    // ── Step 1: reschedule orders ────────────────────────────────────────
    const [orders, setOrders] = useState([]);
    const [loadingOrders, setLoadingOrders] = useState(false);
    const [selectedOrderIds, setSelectedOrderIds] = useState(new Set());

    const loadOrders = useCallback(async () => {
        setLoadingOrders(true);
        try {
            const data = await deliveryApi.listRescheduleOrders();
            setOrders(Array.isArray(data) ? data : []);
        } catch (err) {
            toast.error(err?.message || 'Không thể tải danh sách đơn hoãn giao');
        } finally {
            setLoadingOrders(false);
        }
    }, []);

    useEffect(() => { loadOrders(); }, [loadOrders]);

    const toggleOrder = (id) => {
        setSelectedOrderIds(prev => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    };

    const selectAll = () => {
        if (selectedOrderIds.size === orders.length) {
            setSelectedOrderIds(new Set());
        } else {
            setSelectedOrderIds(new Set(orders.map(o => o.orderId)));
        }
    };

    const selectedOrders = useMemo(
        () => orders.filter(o => selectedOrderIds.has(o.orderId)),
        [orders, selectedOrderIds]
    );

    const totals = useMemo(() => {
        let w = 0, v = 0, maxL = 0, maxW = 0, maxH = 0;
        selectedOrders.forEach(o => {
            w += o.weightKg || 0;
            v += o.volumeM3 || 0;
            maxL = Math.max(maxL, o.lengthCm || 0);
            maxW = Math.max(maxW, o.widthCm || 0);
            maxH = Math.max(maxH, o.heightCm || 0);
        });
        return { totalWeightKg: w, totalVolumeM3: v, maxL, maxW, maxH };
    }, [selectedOrders]);

    // ── Step 2: vehicle type ─────────────────────────────────────────────
    const [vehicleTypes, setVehicleTypes] = useState([]);
    const [selectedVehicle, setSelectedVehicle] = useState(null);
    const [capacityResult, setCapacityResult] = useState(null);
    const [loadingCapacity, setLoadingCapacity] = useState(false);
    const [loadingVehicles, setLoadingVehicles] = useState(true);

    useEffect(() => {
        deliveryApi.listVehicleTypes()
            .then(setVehicleTypes)
            .catch(() => toast.error('Không thể tải danh sách xe'))
            .finally(() => setLoadingVehicles(false));
    }, []);

    useEffect(() => {
        if (!selectedVehicle || selectedOrderIds.size === 0) {
            setCapacityResult(null);
            return;
        }
        setLoadingCapacity(true);
        deliveryApi.previewCapacity({
            orderIds: Array.from(selectedOrderIds),
            vehicleTypeCode: selectedVehicle,
        })
            .then(setCapacityResult)
            .catch(err => toast.error(err?.message || 'Lỗi kiểm tra capacity'))
            .finally(() => setLoadingCapacity(false));
    }, [selectedVehicle, selectedOrderIds]);

    const canProceed2 = selectedVehicle && capacityResult?.fits === true;

    // ── Step 3: driver ───────────────────────────────────────────────────
    const [drivers, setDrivers] = useState([]);
    const [selectedDriverId, setSelectedDriverId] = useState('');
    const [loadingDrivers, setLoadingDrivers] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState(null);
    const [note, setNote] = useState('');

    useEffect(() => {
        userApi.listDrivers()
            .then(setDrivers)
            .catch(() => toast.error('Không thể tải danh sách tài xế'))
            .finally(() => setLoadingDrivers(false));
    }, []);

    const handleCreate = async () => {
        if (!selectedDriverId) {
            setError('Vui lòng chọn tài xế.');
            return;
        }
        setSaving(true);
        setError(null);
        try {
            const result = await deliveryApi.createRescheduleTrip({
                orderIds: Array.from(selectedOrderIds),
                vehicleTypeCode: selectedVehicle,
                driverId: selectedDriverId,
                note: note.trim() || null,
            });
            toast.success('Đã tạo chuyến giao lại thành công!');
            onDone?.(result?.id);
        } catch (err) {
            setError(err?.message || 'Tạo chuyến giao lại thất bại. Vui lòng thử lại.');
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal title="Tạo chuyến giao lại" onClose={onClose} width={820}>
            <div className="create-trip-steps">
                {STEPS.map(s => (
                    <div
                        key={s.id}
                        className={`step-item ${step === s.id ? 'is-active' : step > s.id ? 'is-done' : ''}`}
                    >
                        <div className="step-num">{step > s.id ? '✓' : s.id}</div>
                        <div className="step-label">{s.label}</div>
                    </div>
                ))}
            </div>

            {/* ── Step 1 ── */}
            {step === 1 && (
                <div className="step-content">
                    <p className="step-desc">
                        Chọn các đơn <strong>đang hoãn giao (RESCHEDULED)</strong> cần giao lại.
                        Có thể chọn từ nhiều chuyến xe cũ.
                    </p>

                    {loadingOrders && <p className="step-loading">Đang tải danh sách đơn hoãn…</p>}
                    {!loadingOrders && orders.length === 0 && (
                        <p className="step-empty">
                            Hiện không có đơn nào đang chờ giao lại.
                        </p>
                    )}

                    {!loadingOrders && orders.length > 0 && (
                        <>
                            <div className="step1-toolbar">
                                <button
                                    type="button"
                                    className="btn-search"
                                    onClick={selectAll}
                                >
                                    {selectedOrderIds.size === orders.length
                                        ? '☐ Bỏ chọn tất cả'
                                        : '☑ Chọn tất cả'}
                                </button>
                                <span style={{ marginLeft: 12, fontSize: 13, color: '#64748b' }}>
                                    Tổng cộng: <strong>{orders.length}</strong> đơn đang chờ giao lại
                                </span>
                            </div>

                            <div className="order-select-list">
                                <table className="order-select-table">
                                    <thead>
                                        <tr>
                                            <th style={{ width: 36 }}></th>
                                            <th>Mã đơn</th>
                                            <th>Khách / Địa chỉ</th>
                                            <th>Lý do hoãn</th>
                                            <th style={{ textAlign: 'center' }}>Lần</th>
                                            <th>Giao lại dự kiến</th>
                                            <th>Trip cũ</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {orders.map(o => (
                                            <tr
                                                key={o.orderId}
                                                className={selectedOrderIds.has(o.orderId) ? 'is-selected' : ''}
                                                onClick={() => toggleOrder(o.orderId)}
                                            >
                                                <td>
                                                    <input
                                                        type="checkbox"
                                                        checked={selectedOrderIds.has(o.orderId)}
                                                        onChange={() => toggleOrder(o.orderId)}
                                                        onClick={e => e.stopPropagation()}
                                                    />
                                                </td>
                                                <td>
                                                    <strong>{o.orderNumber}</strong>
                                                </td>
                                                <td>
                                                    {o.customerName || '—'}<br />
                                                    <small className="text-muted">
                                                        {o.shippingDistrict || '—'}, {o.shippingCity || '—'}
                                                    </small>
                                                </td>
                                                <td style={{ fontSize: 12 }}>
                                                    <div>{REASON_LABELS[o.rescheduleReason] || o.rescheduleReason || '—'}</div>
                                                    {o.deliveryNote && (
                                                        <small className="text-muted" title={o.deliveryNote}>
                                                            📝 {o.deliveryNote.length > 30
                                                                ? o.deliveryNote.slice(0, 30) + '…'
                                                                : o.deliveryNote}
                                                        </small>
                                                    )}
                                                </td>
                                                <td style={{ textAlign: 'center' }}>
                                                    {o.rescheduleCount >= 3
                                                        ? <span style={{ color: '#dc2626', fontWeight: 700 }}>⚠ {o.rescheduleCount}</span>
                                                        : <span>{o.rescheduleCount || 1}</span>}
                                                </td>
                                                <td style={{ fontSize: 12 }}>
                                                    {o.nextDeliveryDate
                                                        ? formatDateTime(o.nextDeliveryDate)
                                                        : '—'}
                                                </td>
                                                <td style={{ fontSize: 12, color: '#64748b' }}>
                                                    {o.sourceTripNumber || '—'}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </>
                    )}

                    {selectedOrders.length > 0 && (
                        <div className="step1-summary">
                            <span>Đã chọn: <strong>{selectedOrders.length}</strong> đơn</span>
                            <span>Tổng tải: <strong>{(totals.totalWeightKg).toFixed(1)} kg</strong></span>
                            <span>Tổng thể tích: <strong>{(totals.totalVolumeM3).toFixed(3)} m³</strong></span>
                        </div>
                    )}

                    <div className="modal-footer">
                        <button type="button" className="btn-cancel" onClick={onClose}>Huỷ</button>
                        <Button
                            variant="primary"
                            disabled={selectedOrderIds.size === 0}
                            onClick={() => setStep(2)}
                        >
                            Tiếp tục →
                        </Button>
                    </div>
                </div>
            )}

            {/* ── Step 2 ── */}
            {step === 2 && (
                <div className="step-content">
                    <p className="step-desc">
                        Đã chọn <strong>{selectedOrders.length}</strong> đơn hoãn — tổng{' '}
                        <strong>{(totals.totalWeightKg).toFixed(1)} kg</strong> /{' '}
                        <strong>{(totals.totalVolumeM3).toFixed(3)} m³</strong>.
                        Chọn loại xe phù hợp:
                    </p>

                    {loadingVehicles && <p className="step-loading">Đang tải loại xe…</p>}
                    {!loadingVehicles && (
                        <div className="vehicle-grid">
                            {vehicleTypes.map(v => (
                                <VehicleCard
                                    key={v.code}
                                    vehicle={v}
                                    selected={selectedVehicle === v.code}
                                    onSelect={setSelectedVehicle}
                                />
                            ))}
                        </div>
                    )}

                    {selectedVehicle && (
                        <div className="step2-totals">
                            <span>Tổng tải: <strong>{(totals.totalWeightKg).toFixed(1)} kg</strong></span>
                            <span>Tổng thể tích: <strong>{(totals.totalVolumeM3).toFixed(3)} m³</strong></span>
                            <span>Kiện lớn nhất: <strong>{(totals.maxL).toFixed(0)}×{(totals.maxW).toFixed(0)}×{(totals.maxH).toFixed(0)} cm</strong></span>
                        </div>
                    )}

                    {loadingCapacity && <p className="step-loading">Đang kiểm tra capacity…</p>}
                    {!loadingCapacity && capacityResult && (
                        <CapacityResult result={capacityResult} />
                    )}

                    <div className="modal-footer">
                        <button type="button" className="btn-cancel" onClick={() => setStep(1)}>← Quay lại</button>
                        <Button
                            variant="primary"
                            disabled={!canProceed2}
                            onClick={() => setStep(3)}
                        >
                            Tiếp tục →
                        </Button>
                    </div>
                </div>
            )}

            {/* ── Step 3 ── */}
            {step === 3 && (
                <div className="step-content">
                    <h3 className="step3-title">Xác nhận thông tin chuyến giao lại</h3>

                    <div className="step3-summary">
                        <div className="summary-row">
                            <span className="summary-label">Số đơn giao lại</span>
                            <span><strong>{selectedOrders.length}</strong></span>
                        </div>
                        <div className="summary-row">
                            <span className="summary-label">Tổng tải</span>
                            <span><strong>{(totals.totalWeightKg).toFixed(1)} kg</strong></span>
                        </div>
                        <div className="summary-row">
                            <span className="summary-label">Tổng thể tích</span>
                            <span><strong>{(totals.totalVolumeM3).toFixed(3)} m³</strong></span>
                        </div>
                        <div className="summary-row">
                            <span className="summary-label">Loại xe</span>
                            <span><strong>{vehicleTypes.find(v => v.code === selectedVehicle)?.displayName}</strong></span>
                        </div>
                    </div>

                    <div className="step3-section">
                        <div className="step3-section-label">
                            Ghi chú (tùy chọn)
                        </div>
                        <textarea
                            rows={2}
                            value={note}
                            onChange={e => setNote(e.target.value)}
                            placeholder="VD: Giao lại cho khách theo lịch hẹn…"
                            style={{ width: '100%', padding: 8, borderRadius: 4, border: '1px solid #cbd5e1' }}
                        />
                    </div>

                    <div className="step3-section">
                        <div className="step3-section-label">
                            Chọn tài xế <span className="required-star">*</span>
                        </div>
                        {loadingDrivers && <p className="step-loading">Đang tải danh sách tài xế…</p>}
                        {!loadingDrivers && drivers.length === 0 && (
                            <p className="step-empty">
                                Chưa có tài xế nào. Vui lòng tạo tài khoản tài xế (role: DRIVER) trước.
                            </p>
                        )}
                        {!loadingDrivers && drivers.length > 0 && (
                            <div className="driver-list">
                                {drivers.map(d => (
                                    <label
                                        key={d.id}
                                        className={`driver-item ${selectedDriverId === d.id ? 'is-selected' : ''}`}
                                    >
                                        <input
                                            type="radio"
                                            name="driver"
                                            value={d.id}
                                            checked={selectedDriverId === d.id}
                                            onChange={() => setSelectedDriverId(d.id)}
                                        />
                                        <div className="driver-item__inner">
                                            <strong>{d.fullName}</strong>
                                            <span>{d.phone || d.email}</span>
                                        </div>
                                    </label>
                                ))}
                            </div>
                        )}
                    </div>

                    {error && <p className="adjust-modal__error">{error}</p>}

                    <div className="modal-footer">
                        <button type="button" className="btn-cancel" onClick={() => setStep(2)}>← Quay lại</button>
                        <Button
                            variant="primary"
                            loading={saving}
                            disabled={!selectedDriverId}
                            onClick={handleCreate}
                        >
                            🚚 Tạo chuyến giao lại
                        </Button>
                    </div>
                </div>
            )}
        </Modal>
    );
}
