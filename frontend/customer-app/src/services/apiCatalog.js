import apiClient from './apiClient';

/**
 * Catalog API. Mirrors WOODFURNI Mục 4 contract:
 *
 *   GET /products          → PageResponse<ProductResponse>
 *   GET /products/{id}     → ProductResponse
 *   GET /products/slug/{slug}
 *   GET /categories        → CategoryTreeResponse[] (already nested)
 *   GET /materials         → MaterialResponse[]
 *
 * Note: the backend's PageResponse is wrapped in ApiResponse, so each call
 * unwraps .data.data to return the inner payload directly.
 */

function unwrap(response) {
    return response.data.data;
}

function unwrapList(response) {
    return response.data.data;
}

export const catalogApi = {
    // -------- products --------
    searchProducts: (params) =>
        apiClient.get('/products', { params }).then(unwrap),

    getProductById: (id) =>
        apiClient.get(`/products/${id}`).then(unwrap),

    getProductBySlug: (slug) =>
        apiClient.get(`/products/slug/${slug}`).then(unwrap),

    /**
     * Get related products for a given product id.
     * Returns up to `limit` items (default 8), ranked by category / material /
     * environment / room similarity then rating.
     */
    getRelatedProducts: (id, limit = 8) =>
        apiClient.get(`/products/${id}/related`, { params: { limit } }).then(unwrap),

    // -------- categories (tree) --------
    getCategories: () =>
        apiClient.get('/categories').then(unwrapList),

    // -------- materials --------
    getMaterials: () =>
        apiClient.get('/materials').then(unwrapList),
};