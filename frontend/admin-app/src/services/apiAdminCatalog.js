import apiClient from './apiClient';

/**
 * Admin-app catalog APIs. Mirrors backend controllers in
 * com.woodfurni.catalog.{product,category,material}.
 *
 *   GET    /products?keyword&category&environment&room&woodType&minPrice&maxPrice&sort&page&size
 *   GET    /products/{id}
 *   POST   /products           body: ProductRequest
 *   PUT    /products/{id}      body: ProductRequest
 *   PATCH  /products/{id}/status  body: { status }
 *   DELETE /products/{id}
 *
 *   GET    /categories       → CategoryTreeResponse[] (tree)
 *   GET    /categories/flat  → CategoryResponse[]  (flat, has parentId)
 *   GET    /categories/{id}
 *   POST   /categories       body: CategoryRequest
 *   PUT    /categories/{id}  body: CategoryRequest
 *   DELETE /categories/{id}  (ADMIN only)
 *
 *   GET    /materials
 *   POST   /materials        (CONTENT/ADMIN)
 *   PUT    /materials/{id}
 *   DELETE /materials/{id}
 *
 * IMPORTANT:
 *   - On the admin app, products of any status are returned (the backend
 *     sets isStaff=true when the caller has ROLE_ADMIN or ROLE_CONTENT,
 *     so DRAFT rows show up). When filtering by status in the UI we pass
 *     the enum string verbatim.
 */
const unwrap = (r) => r.data.data;

export const adminCatalogApi = {
    // -------- products --------
    searchProducts: (params) => apiClient.get('/products', { params }).then(unwrap),
    getProductById: (id) => apiClient.get(`/products/${id}`).then(unwrap),
    createProduct: (payload) => apiClient.post('/products', payload).then(unwrap),
    updateProduct: (id, payload) => apiClient.put(`/products/${id}`, payload).then(unwrap),
    changeProductStatus: (id, status) =>
        apiClient.patch(`/products/${id}/status`, { status }).then(unwrap),
    deleteProduct: (id) => apiClient.delete(`/products/${id}`).then(unwrap),

    // -------- categories --------
    getCategoriesTree: () => apiClient.get('/categories').then(unwrap),
    getCategoriesFlat: () => apiClient.get('/categories/flat').then(unwrap),
    getCategory: (id) => apiClient.get(`/categories/${id}`).then(unwrap),
    createCategory: (payload) => apiClient.post('/categories', payload).then(unwrap),
    updateCategory: (id, payload) => apiClient.put(`/categories/${id}`, payload).then(unwrap),
    deleteCategory: (id) => apiClient.delete(`/categories/${id}`).then(unwrap),

    // -------- materials --------
    getMaterials: () => apiClient.get('/materials').then(unwrap),
};