import { useCallback, useEffect, useMemo, useState } from 'react';
import toast from 'react-hot-toast';
import { Modal, Button } from '../../components/index.js';
import { deliveryApi, userApi } from '../../services/apiDelivery.js';
import { formatNumber, formatDateTime } from '../../utils/format.js';
import './CreateTripModal.css';

const STEPS = [
    { id: 1, label: '1. Chọn đơn hàng' },
    { id: 2, label: '2. Chọn xe & kiểm tra' },
    { id: 3, label: '3. Chọn tài xế & tạo' },
];

const VEHICLE_ICONS = {
    VAN_SMALL:    '🚐',
    TRUCK_LIGHT:   '🚚',
    TRUCK_MEDIUM: '🚛',
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
    const { fits, reasons = [], vehicle } = result;
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
                    {blockReasons.map((r, i) => (
                        <li key={i}>{r}</li>
                    ))}
                </ul>
            )}
            {warnReasons.length > 0 && (
                <ul className="capacity-result__reasons capacity-result__reasons--warn">
                    {warnReasons.map((r, i) => (
                        <li key={i}>{r}</li>
                    ))}
                </ul>
            )}
            {fits && reasons.length === 0 && (
                <p className="capacity-result__msg">Mọi tiêu chí đều đạt. Sẵn sàng tạo chuyến!</p>
            )}
        </div>
    );
}

export default function CreateTripModal({ onClose, onDone }) {
    const [step, setStep] = useState(1);

    // ── Step 1: eligible orders ──────────────────────────────────────────────
    const [orders, setOrders] = useState([]);
    const [selectedOrderIds, setSelectedOrderIds] = useState(new Set());
    const [search, setSearch] = useState('');
    const [loadingOrders, setLoadingOrders] = useState(false);

    const loadOrders = useCallback(async () => {
        setLoadingOrders(true);
        try {
            const data = await deliveryApi.listEligibleOrders({ search: search.trim() || undefined, size: 100 });
            setOrders(data.items || []);
        } catch (err) {
            toast.error(err?.message || 'Không thể tải đơn hàng');
        } finally {
            setLoadingOrders(false);
        }
    }, [search]);

    useEffect(() => { loadOrders(); }, [loadOrders]);

    const toggleOrder = (id) => {
        setSelectedOrderIds(prev => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
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

    // ── Step 2: vehicle type ─────────────────────────────────────────────────
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

    // ── Step 3: driver ────────────────────────────────────────────────────────
    const [drivers, setDrivers] = useState([]);
    const [selectedDriverId, setSelectedDriverId] = useState('');
    const [loadingDrivers, setLoadingDrivers] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState(null);

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
            await deliveryApi.createTrip({
                orderIds: Array.from(selectedOrderIds),
                vehicleTypeCode: selectedVehicle,
                driverId: selectedDriverId,
            });
            toast.success('Đã tạo chuyến xe thành công!');
            onDone();
        } catch (err) {
            setError(err?.message || 'Tạo chuyến xe thất bại. Vui lòng thử lại.');
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal title="Tạo chuyến xe" onClose={onClose} width={760}>
            {/* Step indicator */}
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
                    <div className="step1-toolbar">
                        <input
                            type="text"
                            className="step1-search"
                            placeholder="Tìm mã đơn (VD: ORD-20260921)…"
                            value={search}
                            onChange={e => setSearch(e.target.value)}
                            onKeyDown={e => e.key === 'Enter' && loadOrders()}
                        />
                        <button type="button" className="btn-search" onClick={loadOrders}>Tìm</button>
                    </div>

                    {loadingOrders && <p className="step-loading">Đang tải đơn hàng…</p>}
                    {!loadingOrders && orders.length === 0 && (
                        <p className="step-empty">Không có đơn hàng nào phù hợp.</p>
                    )}
                    {!loadingOrders && orders.length > 0 && (
                        <div className="order-select-list">
                            <table className="order-select-table">
                                <thead>
                                    <tr>
                                        <th style={{ width: 36 }}></th>
                                        <th>Mã đơn</th>
                                        <th>Khách hàng</th>
                                        <th>Địa chỉ</th>
                                        <th style={{ textAlign: 'right' }}>SL</th>
                                        <th style={{ textAlign: 'right' }}>Tải (kg)</th>
                                        <th style={{ textAlign: 'right' }}>Thể tích (m³)</th>
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
                                            <td><strong>{o.orderNumber}</strong></td>
                                            <td>
                                                {o.customerName || '—'}<br />
                                                <small className="text-muted">{o.customerPhone || ''}</small>
                                            </td>
                                            <td>
                                                {o.shippingDistrict || '—'}, {o.shippingCity || '—'}
                                            </td>
                                            <td style={{ textAlign: 'right' }}>{formatNumber(o.itemCount)}</td>
                                            <td style={{ textAlign: 'right' }}>{(o.weightKg || 0).toFixed(1)}</td>
                                            <td style={{ textAlign: 'right' }}>{(o.volumeM3 || 0).toFixed(3)}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}

                    {/* Tổng hợp tạm */}
                    {selectedOrders.length > 0 && (
                        <div className="step1-summary">
                            <span>Đã chọn: <strong>{selectedOrders.length}</strong> đơn</span>
                            <span>Tổng tải: <strong>{(totals.totalWeightKg || 0).toFixed(1)} kg</strong></span>
                            <span>Tổng thể tích: <strong>{(totals.totalVolumeM3 || 0).toFixed(3)} m³</strong></span>
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
                        Đã chọn <strong>{selectedOrders.length}</strong> đơn — tổng{' '}
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
                    <h3 className="step3-title">Xác nhận thông tin chuyến xe</h3>

                    <div className="step3-summary">
                        <div className="summary-row">
                            <span className="summary-label">Số đơn</span>
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

                    {/* Driver selection */}
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
                            🚚 Tạo chuyến xe
                        </Button>
                    </div>
                </div>
            )}
        </Modal>
    );
}
