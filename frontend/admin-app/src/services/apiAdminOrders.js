import apiClient from './apiClient';

/**
 * Admin-app order API.
 *
 *   GET    /orders?status=&customerId=&page=&size=    → PageResponse<OrderResponse>
 *   GET    /orders/{id}                              → OrderResponse
 *   PATCH  /orders/{id}/status  body: { status }     → OrderResponse
 *   POST   /orders/{id}/cancel                       → OrderResponse
 *   POST   /orders/{id}/receive-return               → OrderResponse
 *   POST   /orders/{id}/force-cancel-promo           → OrderResponse (ADMIN only, SHIPPING/DELIVERED + promo)
 *
 * Backend authorization rules (from OrderController):
 *   - LIST/GET : CUSTOMER (own only), SALES, ADMIN
 *   - STATUS   : SALES, WAREHOUSE, ADMIN
 *   - CANCEL   : CUSTOMER (own when PENDING/CONFIRMED), ADMIN
 *   - FORCE-CANCEL-PROMO: ADMIN (only when SHIPPING/DELIVERED + has promotion code)
 *   - RECEIVE-RETURN: SALES, ADMIN (for SHIPPING orders)
 *
 * Note: in the admin app we always send as a staff user (CUSTOMER role is
 * rejected at AuthContext level), so list returns all orders.
 *
 * Dates: backend accepts ISO-8601 timestamps (Instant).  We pre/post-pad
 * the day boundaries server-side via the controller, but for filter ranges
 * the FE sends `createdFrom` / `createdTo` as full ISO strings.
 */
const unwrap = (r) => r.data.data;

export const adminOrdersApi = {
    list: (params) => apiClient.get('/orders', { params }).then(unwrap),
    getById: (id) => apiClient.get(`/orders/${id}`).then(unwrap),
    updateStatus: (id, status) =>
        apiClient.patch(`/orders/${id}/status`, { status }).then(unwrap),
    sendToWarehouse: (id) =>
        apiClient.post(`/orders/${id}/send-to-warehouse`).then(unwrap),
    markPrepared: (id) =>
        apiClient.post(`/orders/${id}/mark-prepared`).then(unwrap),
    cancel: (id) => apiClient.post(`/orders/${id}/cancel`).then(unwrap),
    /**
     * Nhận lại hàng từ NVGH.
     * @param {string} id - order ID
     * @param {Array<{itemIndex: number, receivedQuantity: number}>} items - received quantities per item
     * @param {string} [note] - optional note
     */
    receiveReturn: (id, items, note) =>
        apiClient.post(`/orders/${id}/receive-return`, { items, note }).then(unwrap),
    /**
     * Admin force-cancel a SHIPPING/DELIVERED order that has a promotion code.
     * Use this when staff couldn't (or shouldn't) record partial product returns
     * — the only legitimate outcomes for a promo order are "delivered in full"
     * or "cancelled". This endpoint implements the second one.
     *
     * @param {string} id - order ID
     * @param {string} [reason] - optional cancellation reason (recorded in status history)
     */
    forceCancelPromo: (id, reason) =>
        apiClient.post(`/orders/${id}/force-cancel-promo`, { reason: reason || null }).then(unwrap),
};
