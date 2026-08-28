import apiClient from './apiClient';

/**
 * Reviews API.
 *
 *   GET /products/{productId}/reviews?page=0&size=10
 *     → ProductReviewsResponse { productId, productName, ratingAverage, ratingCount,
 *                                reviews: ReviewResponse[], page, totalPages, totalElements }
 *
 * Public. No token required.
 */
export const reviewsApi = {
    listForProduct: (productId, { page = 0, size = 10 } = {}) =>
        apiClient
            .get(`/products/${productId}/reviews`, { params: { page, size } })
            .then((r) => r.data.data),
};