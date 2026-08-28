import apiClient from './apiClient';

/**
 * Reviews API.
 *
 *   POST /products/{productId}/reviews  → ReviewResponse
 *     body: { orderId, rating, comment }
 *
 * Requires CUSTOMER/ADMIN and a DELIVERED order containing the product.
 */
export const reviewsApi = {
    create: (productId, payload) =>
        apiClient.post(`/products/${productId}/reviews`, payload).then((r) => r.data.data),
};