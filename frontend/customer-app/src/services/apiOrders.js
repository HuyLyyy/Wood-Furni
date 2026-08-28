import apiClient from './apiClient';

/**
 * Orders API (Mục 4 — Order module).
 *
 *   POST /orders/checkout           → OrderResponse  (body: CheckoutRequest)
 *   GET  /orders?status=&page=&size= → PageResponse<OrderResponse>
 *   GET  /orders/{id}               → OrderResponse
 *   POST /orders/{id}/cancel        → OrderResponse
 *   PATCH /orders/{id}/status       → OrderResponse  (SALES/WAREHOUSE/ADMIN only)
 */
const unwrap = (r) => r.data.data;

export const ordersApi = {
    checkout: (payload) =>
        apiClient.post('/orders/checkout', payload).then(unwrap),

    getOrders: (params) =>
        apiClient.get('/orders', { params }).then(unwrap),

    getOrderById: (id) => apiClient.get(`/orders/${id}`).then(unwrap),

    cancelOrder: (id) => apiClient.post(`/orders/${id}/cancel`).then(unwrap),
};