# WOODFURNI Frontend Test Plan

> **Module**: `frontend/customer-app` và `frontend/admin-app`
> **Spec reference**: `docs/ai-specs/WOODFURNI_AI_DEV_SPEC_1.md`, **Task 13.2 (PHASE 13 — Testing)**
> **Scope**: Unit + integration tests cho AuthContext, CartContext, FilterSidebar, admin OrderDetailPage.
> **Pattern**: Given / When / Then — đồng nhất với `backend-test-plan.md`.

---

## 1. Testing Strategy

| Layer | Tooling | Purpose | Network |
|-------|---------|---------|---------|
| Component unit | Vitest + React Testing Library + `@testing-library/user-event` | Render component thật, assert DOM + callback. Không stub React internals. | Intercept bởi **MSW** (handlers per-test) |
| Context unit | Vitest + RTL + `useAuth()` / `useCart()` probe component | Verify state machine + side-effects (tokenStorage, optimistic update). | MSW |
| Integration (page-level) | Vitest + RTL + `MemoryRouter` + MSW | Render cả page (OrderDetailPage) với router + auth seed. | MSW |

### Tại sao MSW thay vì mock axios trực tiếp?

- **Gần với thực tế hơn**: axios interceptors (401 refresh, toast, ApiResponse envelope unwrap) chạy thật → bắt được bug ở interceptor, không chỉ ở service.
- **Offline-first**: đáp ứng DoD "Test không phụ thuộc backend thật đang chạy".
- **Handlers chia sẻ giữa test**: cùng `handlers.js` có thể tái sử dụng cho cả customer/admin app.

### Cấu trúc thư mục test helper

```
frontend/<app>/src/test/
├── handlers.js     # MSW http.* factory + OK/err helpers
├── server.js       # setupServer(...handlers)
├── setup.js        # beforeAll/afterEach/afterAll lifecycle
└── render.jsx      # renderWithProviders (wrap Auth + Cart context)
```

Mỗi test đặt trong `src/**/__tests__/*.test.{js,jsx}` (Vitest `include` glob).

---

## 2. Test Environment

| Tool | Phiên bản | Lý do |
|------|-----------|-------|
| Vitest | 1.6.x | Native Vite ESM, không cần Babel/Jest config riêng |
| `@testing-library/react` | 16.x | React 18, Concurrent-safe |
| `@testing-library/user-event` | 14.x | Mô phỏng keystroke/click thật |
| `@testing-library/jest-dom` | 6.x | Custom matchers (toBeInTheDocument, …) |
| MSW | 2.x | Service-worker-API đồng nhất giữa Node/browser |
| `jsdom` | 24.x | DOM env cho React render |

---

## 3. Test Cases (Given / When / Then)

### 3.1 AuthContext (TC-FE-AUTH-01 / 02)

> File: `customer-app/src/contexts/__tests__/AuthContext.test.jsx`
> File: `admin-app/src/contexts/__tests__/AuthContext.test.jsx`

#### TC-FE-AUTH-01 — Login thành công lưu token

- **Given**: localStorage rỗng, MSW POST `/auth/login` trả 200 với `{ accessToken, refreshToken, user }`.
- **When**: Gọi `auth.login(email, password)`.
- **Then**:
  - `tokenStorage.getAccess()` trả access token vừa nhận.
  - `tokenStorage.getRefresh()` trả refresh token.
  - `tokenStorage.getUser()` trả user object.
  - `auth.login()` resolve về user object.
- **Why**: Bảo đảm happy path không bỏ sót field nào của AuthResponse — mất refresh token nghĩa là user bị logout sau khi access token hết hạn.

#### TC-FE-AUTH-02 — Login thất bại KHÔNG lưu token

- **Given**: localStorage rỗng, MSW POST `/auth/login` trả 401 với message lỗi.
- **When**: Gọi `auth.login("alice", "wrong")`.
- **Then**:
  - Promise reject với error chứa message từ backend.
  - `tokenStorage.getAccess() === null`, `getRefresh() === null`, `getUser() === null`.
- **Why**: Bug phổ biến là `setAccess()` được gọi trước khi check status → user thấy "đã login" dù 401.

#### TC-FE-AUTH-02b (admin-app only) — CUSTOMER role bị reject ngay cả khi backend trả 200

- **Given**: Login với credential CUSTOMER, server vẫn 200 OK.
- **When**: `auth.login()` chạy xong.
- **Then**: throw error "không có quyền truy cập Admin Console", tokens KHÔNG được persist.

---

### 3.2 ProductFilter (TC-FE-FILTER-01)

> File: `customer-app/src/features/catalog/__tests__/FilterSidebar.test.jsx`

Sub-tests a–h, mỗi field/behavior riêng:

| ID | Field / Action | Expected onChange payload |
|----|----------------|----------------------------|
| TC-FE-FILTER-01a | category select → `ban-tra` | `{ category: 'ban-tra' }` |
| TC-FE-FILTER-01b | environment select → `INDOOR` | `{ environment: 'INDOOR' }` |
| TC-FE-FILTER-01c | room select → `LIVING_ROOM` | `{ room: 'LIVING_ROOM' }` |
| TC-FE-FILTER-01d | woodType select → `OAK` | `{ woodType: 'OAK' }` |
| TC-FE-FILTER-01e | keyword input → gõ "sofa" | `{ keyword: 'sofa' }` (last call) |
| TC-FE-FILTER-01f | minPrice + maxPrice input | 2 onChange riêng biệt: `{ minPrice: '100000' }`, `{ maxPrice: '500000' }` |
| TC-FE-FILTER-01g | chọn "Tất cả" (empty option) | `{ category: null }` — clears URL filter |
| TC-FE-FILTER-01h | click "Xoá hết" | `onReset()` gọi 1 lần, không có onChange |

- **Given**: Component render với `filters` ban đầu, MSW không cần (không fetch).
- **When**: user.type / user.selectOptions / user.click.
- **Then**: `onChange.mock.calls[*][0]` khớp payload schema; `onReset` chỉ gọi đúng 1 lần khi bấm reset.
- **Why**: Filter là entry point của URL state → sai payload = sai query string = sai backend parse. Đây là contract test quan trọng nhất trong FE.

---

### 3.3 CartContext (TC-FE-CART-01 / 02)

> File: `customer-app/src/contexts/__tests__/CartContext.test.jsx`

#### TC-FE-CART-01 — addItem POST /cart/items, state lấy từ server

- **Given**: User đã login (tokenStorage seeded), MSW GET `/cart` trả cart rỗng, POST `/cart/items` trả cart có 1 item với giá từ backend.
- **When**: `cart.addItem('p-100', 2)`.
- **Then**:
  - State `cart.items[0].productId === 'p-100'`, `quantity === 2`.
  - `totalAmount`, `itemCount` = giá trị từ response (không phải FE tự tính).
- **Why**: Backend là authoritative cho giá / discount — nếu FE tự cộng dồn, discount sẽ bị apply sai.

#### TC-FE-CART-01b — addItem 2 lần cùng productId: state lấy từ server mỗi lần

- **Given**: POST `/cart/items` mock trả quantity tăng dần (1 → 3) — mô phỏng backend merge.
- **When**: addItem 1 lần (qty=1), rồi addItem 1 lần (qty=3) với cùng productId.
- **Then**:
  - POST được gọi 2 lần (verify qua callCount).
  - State cuối cùng có `items[0].quantity === 3` (KHÔNG phải 1+3=4).
- **Why**: Phòng case FE tự merge local — sẽ thành `4` thay vì `3`.

#### TC-FE-CART-02 — removeItem xoá đúng item

- **Given**: Cart có 2 items (`p-a`, `p-b`), MSW DELETE `/cart/items/p-a` trả cart còn `p-b`.
- **When**: `cart.removeItem('p-a')`.
- **Then**:
  - State `cart.items` không còn `p-a`.
  - `cart.totalAmount` khớp với response.
- **Why**: Sanity check cho API mapping; cũng là regression test nếu sau này ai đổi URL từ `/cart/items/{id}` sang `/cart/{id}`.

#### TC-FE-CART-03 — Chưa login không gọi GET /cart

- **Given**: preloaded=null (không có token trong localStorage), MSW không register `/cart`.
- **When**: CartProvider mount.
- **Then**: GET `/cart` KHÔNG được gọi (MSW sẽ error nếu có request stray).
- **Why**: Bảo đảm anonymous session không spam backend.

---

### 3.4 Admin OrderDetailPage (TC-FE-ORDER-01)

> File: `admin-app/src/features/order/__tests__/OrderDetailPage.test.jsx`

| ID | Current status | Action buttons hiển thị |
|----|----------------|-------------------------|
| TC-FE-ORDER-01.PENDING | PENDING | "→ Đã xác nhận", "→ Đã huỷ" |
| TC-FE-ORDER-01.CONFIRMED | CONFIRMED | "→ Đang xử lý", "→ Đã huỷ" |
| TC-FE-ORDER-01.PROCESSING | PROCESSING | "→ Đang giao" |
| TC-FE-ORDER-01.SHIPPING | SHIPPING | "→ Đã giao" |
| TC-FE-ORDER-01.DELIVERED | DELIVERED | "→ Đã trả" |
| TC-FE-ORDER-01b.CANCELLED | CANCELLED | (không render section hành động) |
| TC-FE-ORDER-01c.RETURNED | RETURNED | (không render section hành động) |

- **Given**: Admin user đã auth (role=ADMIN), MSW GET `/orders/{id}` trả order với status test.
- **When**: Render `<OrderDetailPage/>` qua MemoryRouter.
- **Then**: Các button trong section "Hành động (theo state machine)" có label đúng theo `NEXT_STATUSES[current]` trong `utils/orderMeta.js`.
- **Why**: State machine là contract giữa FE và BE OrderService.isValidTransition(). UI hiển thị transition không hợp lệ = cho phép user click vào endpoint 400.
- **Note**: `useConfirmDialog` được mock auto-confirm ở cấp module (`vi.mock`) — tránh phụ thuộc dialog UX, tập trung test state machine.

---

## 4. DoD Verification

- [x] `npm test` pass ở cả 2 app.
  - `frontend/customer-app`: 12+ test cases.
  - `frontend/admin-app`: 9+ test cases.
- [x] Test không phụ thuộc backend thật đang chạy (chạy được khi offline nhờ MSW).
  - `onUnhandledRequest: 'error'` ở `setup.js` — bất kỳ request nào không được handle sẽ fail ngay, không silently pass.
- [x] Mocking qua MSW (không mock axios trực tiếp).
- [x] Cùng pattern Given/When/Then với `backend-test-plan.md`.

---

## 5. Cách chạy

```bash
# Một app cụ thể
cd frontend/customer-app
npm install
npm test                  # one-shot, exit code 0/1
npm run test:watch        # watch mode khi dev

cd ../admin-app
npm install
npm test
```

Expected:
- `customer-app`: 12 passed (AuthContext 2, FilterSidebar 8, CartContext 4) — subtract overlap → ≥10.
- `admin-app`: ~10 passed (AuthContext 3, OrderDetailPage 7) — actual count varies with `it.each`.
