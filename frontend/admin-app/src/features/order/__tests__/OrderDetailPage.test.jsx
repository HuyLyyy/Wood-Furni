/**
 * TC-FE-ORDER-01 — Admin OrderDetailPage dropdown trạng thái chỉ hiện
 * các option hợp lệ theo trạng thái hiện tại (state machine).
 *
 * Spec Mục 4.3 (mirrored trong admin-app/src/utils/orderMeta.js):
 *   PENDING    → { CONFIRMED, CANCELLED }
 *   CONFIRMED  → { PROCESSING, CANCELLED }
 *   PROCESSING → { SHIPPING }
 *   SHIPPING   → { DELIVERED }
 *   DELIVERED  → { RETURNED }
 *   CANCELLED  → {}   // terminal
 *   RETURNED   → {}   // terminal
 *
 * Bài test này đảm bảo UI dropdown chỉ render đúng các button theo state
 * hiện tại, không bao giờ cho user click vào transition không hợp lệ
 * (vd: PENDING → DELIVERED).
 *
 * MSW mock GET /orders/{id} trả về order với status test cần.
 * useConfirmDialog được mock để auto-confirm → test tập trung vào button
 * ở dropdown, không phải dialog UX (đã có test riêng ở component).
 */
import { describe, it, expect, vi, beforeAll } from 'vitest';
import { http } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { API_BASE, ok, err } from '../../../test/handlers.js';
import { server } from '../../../test/server.js';
import { renderWithProviders, waitFor, screen } from '../../../test/render.jsx';
import OrderDetailPage from '../OrderDetailPage.jsx';

// Auto-confirm dialog → bỏ qua UX dialog, click thẳng button action.
vi.mock('../../../components/ConfirmDialog.jsx', () => ({
    default: () => null,
    useConfirmDialog: () => ({
        confirm: () => Promise.resolve(true),
        dialog: null,
    }),
}));

const ADMIN_USER = {
    id: 'u-adm',
    email: 'adm@x.com',
    fullName: 'Adm',
    role: 'ADMIN',
};

function buildOrder(status) {
    return {
        id: 'o-1',
        orderNumber: 'ORD-2026-0001',
        status,
        paymentStatus: 'UNPAID',
        paymentMethod: 'COD',
        customerId: 'u-cust',
        items: [
            {
                productId: 'p-1',
                sku: 'SF-001',
                productName: 'Sofa',
                quantity: 1,
                unitPrice: 1000,
                subtotal: 1000,
            },
        ],
        subtotalAmount: 1000,
        discountAmount: 0,
        totalAmount: 1000,
        shippingAddress: {
            label: 'Nhà',
            line1: '12 Nguyễn Huệ',
            ward: 'P.Bến Nghé',
            district: 'Q.1',
            city: 'HCM',
            phone: '0909000000',
        },
        statusHistory: [
            { status, changedAt: '2026-01-01T00:00:00Z' },
        ],
        createdAt: '2026-01-01T00:00:00Z',
    };
}

function renderOrderDetail(order) {
    // /auth/me trả về user → bootstrap AuthContext set isAuthenticated=true
    // và `await new Promise(r => setTimeout(r, 0))` để useEffect settle.
    server.use(
        http.get(`${API_BASE}/auth/me`, () =>
            ok({
                id: ADMIN_USER.id,
                email: ADMIN_USER.email,
                fullName: ADMIN_USER.fullName,
                role: ADMIN_USER.role,
            })
        ),
        http.get(`${API_BASE}/orders/o-1`, () => ok(order))
    );
    return renderWithProviders(
        <MemoryRouter initialEntries={['/orders/o-1']}>
            <Routes>
                <Route path="/orders/:id" element={<OrderDetailPage />} />
                <Route path="/orders" element={<div>LIST</div>} />
            </Routes>
        </MemoryRouter>,
        {
            preloaded: {
                accessToken: 'admin.token',
                refreshToken: 'admin.refresh',
                user: ADMIN_USER,
            },
        }
    );
}

/**
 * Helper: lấy danh sách label của các button trong section "Hành động"
 * (state-machine action bar). Loại bỏ "← Quay lại" / "Thử lại" / dialog.
 *
 * Dùng findByRole('heading') thay vì queryByText vì orderNumber xuất
 * hiện ở 2 chỗ (breadcrumb + h1). Vẫn chờ loading xong bằng cách
 * đợi heading "ORD-…" render.
 */
async function getActionLabels() {
    await screen.findByRole('heading', { name: 'ORD-2026-0001', level: 1 });
    const heading = screen.getByText('Hành động (theo state machine)');
    const section = heading.closest('section');
    if (!section) return [];
    return [...section.querySelectorAll('button')]
        .map((b) => (b.textContent || '').trim())
        .filter(Boolean);
}

describe('OrderDetailPage — dropdown state machine', () => {
    it.each([
        {
            current: 'PENDING',
            expected: ['Đã xác nhận', 'Đã huỷ'],
        },
        {
            current: 'CONFIRMED',
            expected: ['Đang xử lý', 'Đã huỷ'],
        },
        {
            current: 'PROCESSING',
            expected: ['Đang giao'],
        },
        {
            current: 'SHIPPING',
            expected: ['Đã giao'],
        },
        {
            current: 'DELIVERED',
            expected: ['Đã trả'],
        },
    ])(
        'TC-FE-ORDER-01: từ trạng thái %s hiển thị đúng %j',
        async ({ current, expected }) => {
            const order = buildOrder(current);
            renderOrderDetail(order);
            const labels = await getActionLabels();
            expect(labels).toEqual(expected.map((l) => `→ ${l}`));
        }
    );

    it('TC-FE-ORDER-01b: CANCELLED là terminal → không có button hành động nào', async () => {
        const order = buildOrder('CANCELLED');
        renderOrderDetail(order);
        await screen.findByRole('heading', { name: 'ORD-2026-0001', level: 1 });
        // Toàn bộ section Hành động không được render khi validNext.length===0
        expect(
            screen.queryByText('Hành động (theo state machine)')
        ).not.toBeInTheDocument();
    });

    it('TC-FE-ORDER-01c: RETURNED là terminal → không có button hành động nào', async () => {
        const order = buildOrder('RETURNED');
        renderOrderDetail(order);
        await screen.findByRole('heading', { name: 'ORD-2026-0001', level: 1 });
        expect(
            screen.queryByText('Hành động (theo state machine)')
        ).not.toBeInTheDocument();
    });
});
