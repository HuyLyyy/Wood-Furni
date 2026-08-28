import { Routes, Route, Navigate } from 'react-router-dom';
import AdminLayout from '../layouts/AdminLayout.jsx';
import { ProtectedRoute } from '../components/index.js';
import { LoginPage } from '../features/auth/index.js';
import { DashboardPage } from '../features/dashboard/index.js';
import ProductListPage from '../features/catalog/ProductListPage.jsx';
import ProductFormPage from '../features/catalog/ProductFormPage.jsx';
import CategoryPage from '../features/catalog/CategoryPage.jsx';
import InventoryPage from '../features/inventory/InventoryPage.jsx';
import PrepareOrderPage from '../features/warehouse/PrepareOrderPage.jsx';
import OrderListPage from '../features/order/OrderListPage.jsx';
import OrderDetailPage from '../features/order/OrderDetailPage.jsx';
import CustomerListPage from '../features/customer/CustomerListPage.jsx';
import PromotionListPage from '../features/promotion/PromotionListPage.jsx';
import ReviewListPage from '../features/review/ReviewListPage.jsx';
import NotFoundPage from '../pages/NotFoundPage.jsx';

/**
 * AppRouter — admin-app.
 *
 * Public:
 *   /login
 *
 * Authenticated (staff only — CUSTOMER role rejected at AuthContext level):
 *   /                       → Dashboard
 *   /products, /products/new, /products/:id/edit
 *   /categories
 *   /inventory
 *   /orders, /orders/:id
 *   /customers
 *   /promotions
 *   /reviews
 *
 * Per-role redirect is handled by ProtectedRoute, but the sidebar hides
 * menu items the user can't see.
 */
export default function AppRouter() {
    return (
        <Routes>
            <Route path="/login" element={<LoginPage />} />

            <Route element={
                <ProtectedRoute>
                    <AdminLayout />
                </ProtectedRoute>
            }>
                <Route path="/" element={<DashboardPage />} />

                {/* Catalog — built in Task 12.2 */}
                <Route path="/products" element={<ProductListPage />} />
                <Route path="/products/new" element={<ProductFormPage />} />
                <Route path="/products/:id/edit" element={<ProductFormPage />} />
                <Route path="/categories" element={<CategoryPage />} />

                {/* Inventory — built in Task 12.4 */}
                <Route path="/inventory" element={<InventoryPage />} />

                {/* Warehouse prepare — Task 12.6 */}
                <Route path="/prepare-orders" element={<PrepareOrderPage />} />

                {/* Orders / Customers / Promotions / Reviews — Task 12.5 */}
                <Route path="/orders" element={<OrderListPage />} />
                <Route path="/orders/:id" element={<OrderDetailPage />} />
                <Route path="/customers" element={<CustomerListPage />} />
                <Route path="/promotions" element={<PromotionListPage />} />
                <Route path="/reviews" element={<ReviewListPage />} />

                <Route path="*" element={<Navigate to="/404" replace />} />
            </Route>
        </Routes>
    );
}
