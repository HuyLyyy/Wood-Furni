import apiClient from './apiClient';

/**
 * Admin-app inventory API.
 *
 *   GET  /inventory?page=&size=                           → PageResponse<InventoryResponse>
 *   GET  /inventory/low-stock?page=&size=                  → PageResponse<InventoryResponse>
 *   GET  /inventory/{productId}                            → InventoryResponse
 *   PATCH /inventory/{productId}/adjust                     body: FormData { reasonCode, delta, note?, evidence }
 *   GET  /inventory/{productId}/history?page=&size=        → PageResponse<InventoryHistoryResponse>
 *
 * The adjust endpoint requires multipart/form-data so that a binary Excel
 * file can be uploaded alongside the structured fields.
 *
 * Backend pre-authorizes WAREHOUSE/ADMIN at controller level.
 */
const unwrap = (r) => r.data.data;

export const adminInventoryApi = {
    list: (params) => apiClient.get('/inventory', { params }).then(unwrap),
    listLowStock: (params) => apiClient.get('/inventory/low-stock', { params }).then(unwrap),
    getByProductId: (productId) =>
        apiClient.get(`/inventory/${productId}`).then(unwrap),
    /**
     * Adjust stock with mandatory Excel evidence file.
     *
     * @param {string} productId
     * @param {{ reasonCode: string, delta: number, note?: string }} request
     * @param {File} evidence  — Excel file (.xlsx/.xls), max 10 MB
     */
    adjust: (productId, { reasonCode, delta, note }, evidence) => {
        const body = new FormData();
        body.append('reasonCode', reasonCode);
        body.append('delta', String(delta));
        if (note && note.trim()) body.append('note', note.trim());
        body.append('evidence', evidence); // filename + content-type inferred by browser
        return apiClient.patch(`/inventory/${productId}/adjust`, body, {
            headers: { 'Content-Type': 'multipart/form-data' },
        }).then(unwrap);
    },
    getHistory: (productId, params) =>
        apiClient.get(`/inventory/${productId}/history`, { params }).then(unwrap),
};