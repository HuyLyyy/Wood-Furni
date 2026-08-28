import apiClient from './apiClient';

/**
 * Admin-app review API.
 *
 *   GET /admin/reviews?rating&status&productId&page&size  → PageResponse<AdminReviewView>
 *   PATCH /reviews/{id}/status  body: { status: 'PUBLISHED'|'HIDDEN' }
 *
 * Backend allows ADMIN/CONTENT for both endpoints.
 *
 * The status toggle endpoint is the canonical "hide / show" used by the
 * product-detail review moderation too.
 */
const unwrap = (r) => r.data.data;

export const adminReviewsApi = {
    list: (params) => apiClient.get('/admin/reviews', { params }).then(unwrap),
    updateStatus: (id, status) =>
        apiClient.patch(`/reviews/${id}/status`, { status }).then(unwrap),
};
