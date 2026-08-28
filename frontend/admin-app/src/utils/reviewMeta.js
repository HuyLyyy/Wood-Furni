/**
 * Review status metadata for badges / filters.
 * Backend enum: com.woodfurni.review.enums.ReviewStatus (PUBLISHED, HIDDEN).
 */

export const REVIEW_STATUS = [
    { value: 'PUBLISHED', label: 'Đang hiển thị', tone: 'ok' },
    { value: 'HIDDEN',    label: 'Đã ẩn',        tone: 'danger' },
];

export function reviewStatusLabel(value) {
    return REVIEW_STATUS.find((s) => s.value === value)?.label ?? value;
}

export function reviewStatusTone(value) {
    return REVIEW_STATUS.find((s) => s.value === value)?.tone ?? 'mute';
}

export const REVIEW_RATINGS = [1, 2, 3, 4, 5];

export function reviewRatingLabel(rating) {
    if (rating == null) return '—';
    return `${'★'.repeat(rating)}${'☆'.repeat(5 - rating)}`;
}
