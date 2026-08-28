import apiClient from './apiClient';

/**
 * Cart API (Mục 4 — Cart module).
 *
 *   GET    /cart                    → CartResponse
 *   POST   /cart/items              → CartResponse  (body: { productId, quantity })
 *   PUT    /cart/items/{productId}  → CartResponse  (body: { quantity } — 0 to remove)
 *   DELETE /cart/items/{productId}  → CartResponse
 *   DELETE /cart                    → CartResponse
 */
const unwrap = (r) => r.data.data;

export const cartApi = {
    getCart: () => apiClient.get('/cart').then(unwrap),

    addItem: (productId, quantity) =>
        apiClient.post('/cart/items', { productId, quantity }).then(unwrap),

    updateItemQuantity: (productId, quantity) =>
        apiClient
            .put(`/cart/items/${productId}`, { quantity })
            .then(unwrap),

    removeItem: (productId) =>
        apiClient.delete(`/cart/items/${productId}`).then(unwrap),

    clearCart: () => apiClient.delete('/cart').then(unwrap),
};