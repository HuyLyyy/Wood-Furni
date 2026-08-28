import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { cartApi } from '../services/apiCart.js';
import { useAuth } from './AuthContext.jsx';

const CartContext = createContext(null);

/**
 * CartProvider
 *
 * Single source of truth for the cart. The Header's badge, the CartPage,
 * and the "Add to Cart" button on the product detail page all read from
 * this context.
 *
 * Lifecycle:
 *   - When the user logs in → fetch cart from GET /cart.
 *   - When the user logs out → clear local state.
 *   - Every cart action (add / update / remove) doesn't just mutate local
 *     state — it calls the backend and stores the returned CartResponse,
 *     so prices/subtotals stay authoritative.
 *
 * Returns:
 *   cart        { items, totalAmount, itemCount, updatedAt, id, userId } or null
 *   loading     true during the initial fetch
 *   refresh()   manually re-fetch
 *   addItem / updateItem / removeItem / clear
 *   actionBusy  id of the item currently being mutated (for UI spinners)
 */
export function CartProvider({ children }) {
    const { isAuthenticated } = useAuth();
    const [cart, setCart] = useState(null);
    const [loading, setLoading] = useState(false);
    const [actionBusy, setActionBusy] = useState(null);

    const refresh = useCallback(async () => {
        if (!isAuthenticated) {
            setCart(null);
            return null;
        }
        setLoading(true);
        try {
            const data = await cartApi.getCart();
            setCart(data);
            return data;
        } catch (err) {
            // 401 → useAuth will redirect; for other errors we keep the
            // previous cart so the page doesn't flash empty.
            console.warn('[cart] refresh failed:', err?.message);
            return null;
        } finally {
            setLoading(false);
        }
    }, [isAuthenticated]);

    // Fetch cart on login / clear on logout
    useEffect(() => {
        if (isAuthenticated) {
            refresh();
        } else {
            setCart(null);
        }
    }, [isAuthenticated, refresh]);

    // -------- actions --------

    const addItem = useCallback(async (productId, quantity) => {
        setActionBusy(`add:${productId}`);
        try {
            const data = await cartApi.addItem(productId, quantity);
            setCart(data);
            return data;
        } finally {
            setActionBusy(null);
        }
    }, []);

    const updateItem = useCallback(async (productId, quantity) => {
        setActionBusy(`upd:${productId}`);
        try {
            // quantity=0 → also remove via the same endpoint (per backend)
            const data = await cartApi.updateItemQuantity(productId, quantity);
            setCart(data);
            return data;
        } finally {
            setActionBusy(null);
        }
    }, []);

    const removeItem = useCallback(async (productId) => {
        setActionBusy(`del:${productId}`);
        try {
            const data = await cartApi.removeItem(productId);
            setCart(data);
            return data;
        } finally {
            setActionBusy(null);
        }
    }, []);

    const clear = useCallback(async () => {
        setActionBusy('clear');
        try {
            const data = await cartApi.clearCart();
            setCart(data);
            return data;
        } finally {
            setActionBusy(null);
        }
    }, []);

    const value = {
        cart,
        loading,
        actionBusy,
        refresh,
        addItem,
        updateItem,
        removeItem,
        clear,
        // Convenience selectors
        itemCount: cart?.itemCount ?? 0,
        totalAmount: cart?.totalAmount ?? 0,
    };

    return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
    const ctx = useContext(CartContext);
    if (!ctx) {
        throw new Error('useCart must be used inside <CartProvider>');
    }
    return ctx;
}

CartProvider.propTypes = {
    children: PropTypes.node,
};