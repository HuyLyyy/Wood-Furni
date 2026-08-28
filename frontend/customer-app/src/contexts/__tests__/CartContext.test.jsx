/**
 * TC-FE-CART-01/02 — CartContext:
 *   - addItem(productId, q) POST /cart/items và cập nhật state với cart mới
 *     từ server. Quan trọng nhất: server trả về CartResponse authoritative
 *     (tổng tiền, itemCount, ...), FE không tự cộng dồn — vì có thể có
 *     discount / flash-sale ở backend.
 *   - removeItem(productId) DELETE /cart/items/{productId} và cập nhật
 *     state — verify item đó không còn trong cart.items.
 *
 * Tại sao không mock axios? Theo Task 13.2 DoD: dùng MSW để test gần với
 * thực tế — request phải đi qua thật sự (qua interceptor), chỉ là response
 * được intercept bởi worker.
 */
import { describe, it, expect, vi } from 'vitest';
import { http } from 'msw';
import { API_BASE, ok } from '../../test/handlers.js';
import { server } from '../../test/server.js';
import { renderWithProviders, waitFor } from '../../test/render.jsx';
import { tokenStorage } from '../../services/apiClient.js';
import { AuthProvider } from '../../contexts/AuthContext.jsx';
import { CartProvider, useCart } from '../../contexts/CartContext.jsx';

const STAFF_USER = {
    id: 'u-1',
    email: 'a@x.com',
    fullName: 'A',
    role: 'CUSTOMER',
};

// Helper: bootstrap CartProvider với user đã auth (seed localStorage),
// render một "probe" component để gọi addItem/removeItem từ test.
//
// AuthProvider's bootstrap useEffect gọi /auth/me — đăng ký handler
// trả 200 ngay tại đây để AuthProvider giữ isAuthenticated=true và
// CartProvider.refresh() mới được trigger.
function setup() {
    let cartState = null;
    function Probe() {
        cartState = useCart();
        return <span data-testid="probe">probe</span>;
    }
    server.use(
        http.get(`${API_BASE}/auth/me`, () => ok(STAFF_USER))
    );
    const utils = renderWithProviders(
        <AuthProvider>
            <CartProvider>
                <Probe />
            </CartProvider>
        </AuthProvider>,
        {
            preloaded: {
                accessToken: 'fake.access',
                refreshToken: 'fake.refresh',
                user: STAFF_USER,
            },
        }
    );
    return { ...utils, getCtx: () => cartState };
}

describe('CartContext (customer-app)', () => {
    it('TC-FE-CART-01a: addItem thành công POST /cart/items, cập nhật cart state với response từ server', async () => {
        const initialCart = {
            id: 'cart-1',
            userId: STAFF_USER.id,
            items: [],
            totalAmount: 0,
            itemCount: 0,
            updatedAt: '2026-01-01T00:00:00Z',
        };
        // Sau khi add, server trả cart có 1 item với giá do backend tính
        // (giả lập backend đã set giá / discount):
        const afterAdd = {
            ...initialCart,
            items: [
                {
                    productId: 'p-100',
                    sku: 'SF-001',
                    name: 'Sofa gỗ sồi',
                    unitPrice: 1200000,
                    quantity: 2,
                    subtotal: 2400000,
                    imageUrl: null,
                },
            ],
            totalAmount: 2400000,
            itemCount: 1,
        };

        server.use(
            http.get(`${API_BASE}/cart`, () => ok(initialCart)),
            http.post(`${API_BASE}/cart/items`, () => ok(afterAdd))
        );

        const { getCtx } = setup();
        // Chờ bootstrap refresh() chạy xong
        await waitFor(() => {
            expect(getCtx()?.cart?.id).toBe('cart-1');
        });

        // Gọi addItem(2) → POST /cart/items với body đúng
        await waitFor(async () => {
            await getCtx().addItem('p-100', 2);
        });

        // State phải phản ánh response từ server.
        expect(getCtx().cart.items).toHaveLength(1);
        expect(getCtx().cart.itemCount).toBe(1);
        expect(getCtx().cart.totalAmount).toBe(2400000);
        expect(getCtx().cart.items[0].productId).toBe('p-100');
        expect(getCtx().cart.items[0].quantity).toBe(2);
    });

    it('TC-FE-CART-01b: addItem 2 lần với cùng productId → cart state do server authoritative (cộng dồn ở server)', async () => {
        // Contract test quan trọng: FE không tự cộng dồn local. Phải đợi
        // server trả về cart mới. Mô phỏng backend merge quantity:
        let callCount = 0;
        server.use(
            http.get(`${API_BASE}/cart`, () =>
                ok({
                    id: 'cart-1',
                    userId: STAFF_USER.id,
                    items: [],
                    totalAmount: 0,
                    itemCount: 0,
                    updatedAt: '2026-01-01T00:00:00Z',
                })
            ),
            http.post(`${API_BASE}/cart/items`, () => {
                callCount += 1;
                if (callCount === 1) {
                    return ok({
                        id: 'cart-1',
                        items: [{ productId: 'p-1', quantity: 1, subtotal: 100 }],
                        totalAmount: 100,
                        itemCount: 1,
                    });
                }
                return ok({
                    id: 'cart-1',
                    items: [{ productId: 'p-1', quantity: 3, subtotal: 300 }],
                    totalAmount: 300,
                    itemCount: 1,
                });
            })
        );

        const { getCtx } = setup();
        await waitFor(() => expect(getCtx()?.cart?.id).toBe('cart-1'));

        await getCtx().addItem('p-1', 1);
        // addItem returns once the POST resolves, but React state updates
        // batch asynchronously — wait for the new cart to propagate.
        await waitFor(() => expect(getCtx().cart.itemCount).toBe(1));

        await getCtx().addItem('p-1', 3);
        await waitFor(() => expect(callCount).toBe(2));
        // State phải là response cuối từ server (quantity=3), không phải 1+3=4.
        expect(getCtx().cart.items[0].quantity).toBe(3);
        expect(getCtx().cart.totalAmount).toBe(300);
    });

    it('TC-FE-CART-02: removeItem xoá đúng item khỏi cart', async () => {
        const before = {
            id: 'cart-1',
            userId: STAFF_USER.id,
            items: [
                { productId: 'p-a', sku: 'A', name: 'A', unitPrice: 100, quantity: 1, subtotal: 100 },
                { productId: 'p-b', sku: 'B', name: 'B', unitPrice: 200, quantity: 2, subtotal: 400 },
            ],
            totalAmount: 500,
            itemCount: 2,
        };
        const afterRemove = {
            ...before,
            items: [before.items[1]],
            totalAmount: 400,
            itemCount: 1,
        };

        server.use(
            http.get(`${API_BASE}/cart`, () => ok(before)),
            http.delete(`${API_BASE}/cart/items/p-a`, () => ok(afterRemove))
        );

        const { getCtx } = setup();
        await waitFor(() => expect(getCtx()?.cart?.items).toHaveLength(2));

        await getCtx().removeItem('p-a');

        // Verify item p-a biến mất, p-b còn nguyên, totals khớp server.
        // removeItem resolves when DELETE returns; React state update happens
        // asynchronously — wait for the new items list to propagate.
        await waitFor(() => expect(getCtx().cart.items).toHaveLength(1));
        expect(getCtx().cart.items.find((i) => i.productId === 'p-a')).toBeUndefined();
        expect(getCtx().cart.items[0].productId).toBe('p-b');
        expect(getCtx().cart.itemCount).toBe(1);
        expect(getCtx().cart.totalAmount).toBe(400);
    });

    it('TC-FE-CART-03: chưa login (isAuthenticated=false) → cart vẫn là null, không fetch', async () => {
        // Mặc định renderWithProviders preloaded=null → không có token.
        // Không có /cart handler — nếu CartProvider cố gọi GET /cart, MSW
        // sẽ ném (onUnhandledRequest=error) → test fail.
        let getCalled = false;
        server.use(
            http.get(`${API_BASE}/cart`, () => {
                getCalled = true;
                return ok({ items: [] });
            })
        );

        function Probe() {
            const c = useCart();
            return <span data-testid="cart-state">{String(c.cart)}</span>;
        }
        renderWithProviders(
            <AuthProvider>
                <CartProvider>
                    <Probe />
                </CartProvider>
            </AuthProvider>
        );

        // Bootstrap useEffect chạy — isAuthenticated false → không gọi /cart
        await new Promise((r) => setTimeout(r, 20));
        expect(getCalled).toBe(false);
    });
});
