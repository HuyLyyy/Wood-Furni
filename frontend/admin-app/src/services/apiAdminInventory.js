import apiClient from './apiClient';

/**
 * Admin-app inventory API.
 *
 *   GET /inventory?page=&size=                     → PageResponse<InventoryResponse>
 *   GET /inventory/low-stock?page=&size=          → PageResponse<InventoryResponse>
 *   GET /inventory/{productId}                     → InventoryResponse
 *   PATCH /inventory/{productId}/adjust             body: { delta, reason }
 *   GET  /inventory/{productId}/history?page=&size= → PageResponse<InventoryHistoryResponse>
 *
 * Backend pre-authorizes WAREHOUSE/ADMIN at controller level.
 */
const unwrap = (r) => r.data.data;

export const adminInventoryApi = {
    list: (params) => apiClient.get('/inventory', { params }).then(unwrap),
    listLowStock: (params) => apiClient.get('/inventory/low-stock', { params }).then(unwrap),
    getByProductId: (productId) =>
        apiClient.get(`/inventory/${productId}`).then(unwrap),
    adjust: (productId, delta, reason) =>
        apiClient.patch(`/inventory/${productId}/adjust`, { delta, reason }).then(unwrap),
    getHistory: (productId, params) =>
        apiClient.get(`/inventory/${productId}/history`, { params }).then(unwrap),
};