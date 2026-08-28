import { Routes, Route, Navigate } from 'react-router-dom';
import MainLayout from '../layouts/MainLayout.jsx';
import { ProtectedRoute } from '../components/index.js';

import { LoginPage, RegisterPage } from '../features/auth/index.js';
import { ProductListPage, ProductDetailPage } from '../features/catalog/index.js';
import { CartPage } from '../features/cart/index.js';
import { CheckoutPage } from '../features/checkout/index.js';
import { OrderListPage, OrderDetailPage } from '../features/order/index.js';
import HomePage from '../pages/HomePage.jsx';
import NotFoundPage from '../pages/NotFoundPage.jsx';

/**
 * AppRouter.
 *
 * Public routes (no auth):
 *   /login, /register
 *
 * Storefront (with MainLayout, no auth required):
 *   /, /products, /products/:id
 *
 * Authenticated routes (require login):
 *   /cart, /checkout, /orders, /orders/:id
 */
export default function AppRouter() {
    return (
        <Routes>
            {/* Auth — bare layout */}
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />

            {/* Storefront — with MainLayout */}
            <Route element={<MainLayout />}>
                <Route path="/" element={<HomePage />} />

                {/* Catalog */}
                <Route path="/products" element={<ProductListPage />} />
                <Route path="/products/:id" element={<ProductDetailPage />} />

                {/* Cart + Checkout */}
                <Route path="/cart" element={
                    <ProtectedRoute>
                        <CartPage />
                    </ProtectedRoute>
                } />
                <Route path="/checkout" element={
                    <ProtectedRoute>
                        <CheckoutPage />
                    </ProtectedRoute>
                } />

                {/* Orders */}
                <Route path="/orders" element={
                    <ProtectedRoute>
                        <OrderListPage />
                    </ProtectedRoute>
                } />
                <Route path="/orders/:id" element={
                    <ProtectedRoute>
                        <OrderDetailPage />
                    </ProtectedRoute>
                } />

                <Route path="/404" element={<NotFoundPage />} />
                <Route path="*" element={<Navigate to="/404" replace />} />
            </Route>
        </Routes>
    );
}