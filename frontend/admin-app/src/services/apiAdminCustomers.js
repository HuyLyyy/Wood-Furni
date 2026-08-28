import apiClient from './apiClient';

/**
 * Admin-app customer API.
 *
 *   GET /admin/customers                       → PageResponse<CustomerAdminView>
 *   GET /admin/customers/{id}                  → CustomerDetailView (with order history)
 *
 * Backend enforces ADMIN/SALES at controller level.
 */
const unwrap = (r) => r.data.data;

export const adminCustomersApi = {
    list: (params) => apiClient.get('/admin/customers', { params }).then(unwrap),
    detail: (id) => apiClient.get(`/admin/customers/${id}`).then(unwrap),
};
