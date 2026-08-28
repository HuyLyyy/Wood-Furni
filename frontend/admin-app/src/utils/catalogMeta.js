/**
 * Admin-side catalog enums + label lookups.
 * Mirrors backend exactly: ProductEnvironment, ProductRoom, ProductStatus,
 * CategoryEnvironment, CategoryStatus, WOOD_TYPES.
 */

export const ENVIRONMENTS = [
    { value: 'INDOOR',  label: 'Nội thất' },
    { value: 'OUTDOOR', label: 'Ngoại thất' },
    { value: 'BOTH',    label: 'Cả hai' },
];

export const ROOMS = [
    { value: 'LIVING_ROOM', label: 'Phòng khách' },
    { value: 'BEDROOM',     label: 'Phòng ngủ' },
    { value: 'DINING_ROOM', label: 'Phòng ăn' },
    { value: 'OFFICE',      label: 'Văn phòng' },
    { value: 'GARDEN',      label: 'Sân vườn' },
    { value: 'BALCONY',     label: 'Ban công' },
    { value: 'PATIO',       label: 'Sân hiên' },
];

export const PRODUCT_STATUSES = [
    { value: 'DRAFT',         label: 'Bản nháp',      color: 'draft' },
    { value: 'ACTIVE',        label: 'Đang bán',      color: 'active' },
    { value: 'OUT_OF_STOCK',  label: 'Hết hàng',      color: 'oos' },
    { value: 'DISCONTINUED',  label: 'Ngừng bán',     color: 'discontinued' },
];

export const CATEGORY_STATUSES = [
    { value: 'ACTIVE', label: 'Hoạt động' },
    { value: 'HIDDEN', label: 'Đã ẩn' },
];

export const CATEGORY_ENVIRONMENTS = [
    { value: 'INDOOR',  label: 'Nội thất' },
    { value: 'OUTDOOR', label: 'Ngoại thất' },
    { value: 'BOTH',    label: 'Cả hai' },
];

export function envLabel(v)         { return ENVIRONMENTS.find((e) => e.value === v)?.label ?? v; }
export function roomLabel(v)        { return ROOMS.find((r) => r.value === v)?.label ?? v; }
export function productStatus(v)    { return PRODUCT_STATUSES.find((s) => s.value === v) ?? { label: v, color: 'draft' }; }
export function categoryStatus(v)   { return CATEGORY_STATUSES.find((s) => s.value === v)?.label ?? v; }
export function categoryEnvLabel(v) { return CATEGORY_ENVIRONMENTS.find((c) => c.value === v)?.label ?? v; }