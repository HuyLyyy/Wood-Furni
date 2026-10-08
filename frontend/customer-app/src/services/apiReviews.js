import apiClient from './apiClient';

/**
 * Reviews API.
 *
 *   GET    /products/{productId}/reviews?page=0&size=10
 *     → ProductReviewsResponse { productId, productName, ratingAverage, ratingCount,
 *                                reviews: ReviewResponse[], page, totalPages, totalElements }
 *   POST   /products/{productId}/reviews
 *     → ReviewResponse (auth: CUSTOMER / ADMIN)
 *   PUT    /reviews/{id}
 *     → ReviewResponse (auth: CUSTOMER / ADMIN; only owner or ADMIN can edit)
 *   DELETE /reviews/{id}
 *     → 200 OK (auth: CUSTOMER / ADMIN; only owner or ADMIN can delete)
 *
 * Public. listForProduct does not require a token.
 */
export const reviewsApi = {
    listForProduct: (productId, { page = 0, size = 10 } = {}) =>
        apiClient
            .get(`/products/${productId}/reviews`, { params: { page, size } })
            .then((r) => r.data.data),

    /** Update an existing review (only the author or an ADMIN). */
    updateReview: (reviewId, payload) =>
        apiClient.put(`/reviews/${reviewId}`, payload).then((r) => r.data.data),

    /** Delete an existing review (only the author or an ADMIN). */
    deleteReview: (reviewId) =>
        apiClient.delete(`/reviews/${reviewId}`).then((r) => r.data.data),
};