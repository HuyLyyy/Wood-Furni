import apiClient from './apiClient';

/**
 * Admin-app promotion API.
 *
 *   GET    /promotions           → List<PromotionResponse>
 *   GET    /promotions/{id}      → PromotionResponse
 *   POST   /promotions           body: PromotionRequest
 *   PUT    /promotions/{id}      body: PromotionRequest
 *   DELETE /promotions/{id}
 *
 * Backend allows only ADMIN. Note: GET /promotions returns a flat list
 * (NOT paginated) — see PromotionController.getAll().
 */
const unwrap = (r) => r.data.data;

export const adminPromotionsApi = {
    list: () => apiClient.get('/promotions').then(unwrap),
    getById: (id) => apiClient.get(`/promotions/${id}`).then(unwrap),
    create: (payload) => apiClient.post('/promotions', payload).then(unwrap),
    update: (id, payload) => apiClient.put(`/promotions/${id}`, payload).then(unwrap),
    delete: (id) => apiClient.delete(`/promotions/${id}`).then(unwrap),
};
