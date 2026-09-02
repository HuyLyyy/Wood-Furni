import apiClient from './apiClient';

/**
 * Promotions API.
 *
 *   POST /promotions/validate  → ValidatePromotionResponse
 *     body: { code, cartTotal }
 *
 * Read-only preview — does NOT increment usage. The actual discount is
 * applied at /orders/checkout time.
 */
export const promotionsApi = {
    validate: (code, cartTotal, config = {}) =>
        apiClient
            .post('/promotions/validate', { code, cartTotal }, config)
            .then((r) => r.data.data),
};