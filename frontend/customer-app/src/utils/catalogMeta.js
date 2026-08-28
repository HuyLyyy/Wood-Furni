/**
 * Enums matching backend exactly (case-sensitive). Keep these in sync with
 * com.woodfurni.catalog.product.enums.ProductEnvironment / ProductRoom.
 */

export const ENVIRONMENTS = [
    { value: 'INDOOR', label: 'Nội thất' },
    { value: 'OUTDOOR', label: 'Ngoại thất' },
    { value: 'BOTH', label: 'Cả hai' },
];

export const ROOMS = [
    { value: 'LIVING_ROOM', label: 'Phòng khách' },
    { value: 'BEDROOM', label: 'Phòng ngủ' },
    { value: 'DINING_ROOM', label: 'Phòng ăn' },
    { value: 'OFFICE', label: 'Văn phòng' },
    { value: 'GARDEN', label: 'Sân vườn' },
    { value: 'BALCONY', label: 'Ban công' },
    { value: 'PATIO', label: 'Sân hiên' },
];

/**
 * woodType codes are matched against Material.code on the backend
 * (uppercase: OAK, WALNUT, PINE, TEAK, …).
 */
export const WOOD_TYPES = [
    { value: 'OAK', label: 'Sồi (Oak)' },
    { value: 'WALNUT', label: 'Óc chó (Walnut)' },
    { value: 'PINE', label: 'Thông (Pine)' },
    { value: 'ACACIA', label: 'Tràm (Acacia)' },
    { value: 'TEAK', label: 'Tếch (Teak)' },
];

export const SORT_OPTIONS = [
    { value: '-createdAt', label: 'Mới nhất' },
    { value: 'price,asc', label: 'Giá tăng dần' },
    { value: '-price', label: 'Giá giảm dần' },
    { value: 'ratingAverage', label: 'Đánh giá cao nhất' },
    { value: '-ratingAverage', label: 'Đánh giá nổi bật' },
];

/**
 * Lookup helpers for displaying enum values as Vietnamese labels.
 */
export function envLabel(value) {
    return ENVIRONMENTS.find((e) => e.value === value)?.label ?? value;
}
export function roomLabel(value) {
    return ROOMS.find((r) => r.value === value)?.label ?? value;
}
export function woodLabel(value) {
    return WOOD_TYPES.find((w) => w.value === value)?.label ?? value;
}