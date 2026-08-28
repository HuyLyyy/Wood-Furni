import { useEffect, useState, useCallback } from 'react';
import { shippingApi } from '../services/apiShipping.js';
import { Input } from '../components/index.js';
import './AddressPicker.css';

/**
 * AddressPicker
 *
 * Guided address entry with three steps:
 *
 *   Step 1 — Pick a district from the HCM checklist
 *   Step 2 — Pick a ward from the checklist filtered to the selected district
 *   Step 3 — Type the street address (house number + street name)
 *
 * Props:
 *   value          {{ line1, ward, district, city }}   current value
 *   onChange       (value) => void                    called whenever value changes
 *   errors         {{ [field]: string }}              field-level errors from parent
 *   onErrorsChange (errors => void)                   update field-level errors in parent
 */
export default function AddressPicker({ value, onChange, errors = {}, onErrorsChange }) {
    const [districts, setDistricts] = useState([]);
    const [districtsLoading, setDistrictsLoading] = useState(true);
    const [districtsError, setDistrictsError] = useState(null);

    // Step 1: selected district (single select)
    const [selectedDistrict, setSelectedDistrict] = useState(value?.district || null);
    // Step 2: selected ward (single select)
    const [selectedWard, setSelectedWard] = useState(value?.ward || null);
    // Step 3: typed street address
    const [line1, setLine1] = useState(value?.line1 || '');

    // Bootstrap: load districts
    useEffect(() => {
        setDistrictsLoading(true);
        shippingApi.getDistricts()
            .then((data) => {
                setDistricts(data?.districts || []);
                setDistrictsError(null);
            })
            .catch(() => setDistrictsError('Không tải được danh sách quận'))
            .finally(() => setDistrictsLoading(false));
    }, []);

    // When a district is picked → clear ward selection
    const handleDistrictSelect = useCallback((districtName) => {
        setSelectedDistrict(districtName);
        setSelectedWard(null);
        // Notify parent immediately
        onChange?.({
            ...(value || {}),
            district: districtName,
            ward: null,
            city: 'Hồ Chí Minh',
        });
    }, [value, onChange]);

    // When a ward is picked → update parent
    const handleWardSelect = useCallback((wardName) => {
        setSelectedWard(wardName);
        onChange?.({
            ...(value || {}),
            ward: wardName,
            district: selectedDistrict,
            city: 'Hồ Chí Minh',
        });
    }, [value, selectedDistrict, onChange]);

    // When street address changes → update parent
    const handleLine1Change = useCallback((e) => {
        const v = e.target.value;
        setLine1(v);
        if (errors.line1) onErrorsChange?.({ ...errors, line1: null });
        if (v.trim()) {
            onChange?.({
                ...(value || {}),
                line1: v.trim(),
                ward: selectedWard,
                district: selectedDistrict,
                city: 'Hồ Chí Minh',
            });
        }
    }, [value, selectedWard, selectedDistrict, errors, onChange, onErrorsChange]);

    // Find wards for the selected district
    const selectedDistrictData = districts.find((d) => d.name === selectedDistrict);
    const wards = selectedDistrictData?.wards || [];

    const isComplete = !!(selectedDistrict && selectedWard && line1.trim());

    return (
        <div className="address-picker">
            {/* ── Step 1: Pick district ─────────────────────────────────────────── */}
            <div className="address-picker__step">
                <h3 className="address-picker__step-title">
                    Bước 1: Chọn Quận / Huyện
                    {selectedDistrict && (
                        <span className="address-picker__step-badge">✓</span>
                    )}
                </h3>

                {districtsLoading && (
                    <p className="address-picker__loading">Đang tải danh sách quận…</p>
                )}
                {districtsError && (
                    <p className="address-picker__error">{districtsError}</p>
                )}

                {!districtsLoading && !districtsError && (
                    <div className="address-picker__checklist address-picker__checklist--districts">
                        {districts.map((d) => (
                            <label
                                key={d.name}
                                className={`address-picker__check-item ${
                                    selectedDistrict === d.name ? 'is-selected' : ''
                                }`}
                            >
                                <input
                                    type="radio"
                                    name="district"
                                    value={d.name}
                                    checked={selectedDistrict === d.name}
                                    onChange={() => handleDistrictSelect(d.name)}
                                />
                                <span>{d.name}</span>
                            </label>
                        ))}
                    </div>
                )}
                {errors.district && (
                    <p className="address-picker__field-error">{errors.district}</p>
                )}
            </div>

            {/* ── Step 2: Pick ward ─────────────────────────────────────────────── */}
            {selectedDistrict && (
                <div className="address-picker__step">
                    <h3 className="address-picker__step-title">
                        Bước 2: Chọn Phường / Xã
                        {selectedWard && (
                            <span className="address-picker__step-badge">✓</span>
                        )}
                    </h3>

                    {wards.length === 0 ? (
                        <p className="address-picker__loading">Đang tải danh sách phường…</p>
                    ) : (
                        <div className="address-picker__checklist address-picker__checklist--wards">
                            {wards.map((w) => (
                                <label
                                    key={w}
                                    className={`address-picker__check-item ${
                                        selectedWard === w ? 'is-selected' : ''
                                    }`}
                                >
                                    <input
                                        type="radio"
                                        name="ward"
                                        value={w}
                                        checked={selectedWard === w}
                                        onChange={() => handleWardSelect(w)}
                                    />
                                    <span>{w}</span>
                                </label>
                            ))}
                        </div>
                    )}
                    {errors.ward && (
                        <p className="address-picker__field-error">{errors.ward}</p>
                    )}
                </div>
            )}

            {/* ── Step 3: Street address ──────────────────────────────────────── */}
            {selectedWard && (
                <div className="address-picker__step">
                    <h3 className="address-picker__step-title">
                        Bước 3: Nhập số nhà, tên đường
                        {isComplete && (
                            <span className="address-picker__step-badge address-picker__step-badge--green">
                                ✓ Hoàn tất
                            </span>
                        )}
                    </h3>

                    <div className="address-picker__line1-row">
                        <Input
                            id="addr-line1"
                            label="Số nhà, tên đường"
                            placeholder="VD: 123 Nguyễn Huệ"
                            value={line1}
                            onChange={handleLine1Change}
                            error={errors.line1}
                            required
                        />
                    </div>
                </div>
            )}

            {/* ── Preview ──────────────────────────────────────────────────────── */}
            {isComplete && (
                <div className="address-picker__preview">
                    <span className="address-picker__preview-label">Địa chỉ:</span>
                    <span>
                        {line1.trim()}, {selectedWard}, {selectedDistrict}, Hồ Chí Minh
                    </span>
                </div>
            )}
        </div>
    );
}
