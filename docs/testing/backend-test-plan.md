# WOODFURNI Backend Test Plan

> **Module**: `backend/furniture-api`
> **Spec reference**: `docs/ai-specs/WOODFURNI_AI_DEV_SPEC_1.md`, **Task 13.1 (PHASE 13 — Testing)**
> **Scope**: Service-layer unit tests + Spring Boot integration tests covering the full checkout flow and Role-Based Access Control.
> **Pattern**: Given / When / Then — same format as TC-ORDER-01 / TC-ORDER-02 in the original thesis document.

---

## 1. Testing Strategy

| Layer | Tooling | Purpose | DB |
|-------|---------|---------|-----|
| Service unit | JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`) | Verify business rules without I/O | None — repositories mocked |
| Integration | `@SpringBootTest` + `@AutoConfigureMockMvc` + de.flapdoodle embedded MongoDB | Verify full HTTP + Mongo + Security flow end-to-end | Embedded Mongo 7.0 |
| Security (RBAC) | Same as above + `spring-security-test` (`@WithMockUser`, `user()` post-processor) | Verify `@PreAuthorize` denies the wrong roles | Embedded Mongo |

**Why embedded Mongo, not Testcontainers?** Testcontainers requires Docker on the dev/CI machine. The project ships with `de.flapdoodle.embed.mongo.spring30x` so tests run anywhere a JVM runs. The integration tests are tagged `@ActiveProfiles("test")` which activates `src/test/resources/application-test.yml` (fixed JWT secret, gateway disabled, Mongo pointed at the embedded process).

---

## 2. Definition of Done — Verification (from spec Task 13.1)

| # | DoD requirement | Status | Evidence |
|---|-----------------|--------|----------|
| 1 | Unit tests for **AuthService** (register trùng email, login sai password) | ✅ | TC-AUTH-01, TC-AUTH-03 |
| 2 | Unit tests for **ProductService** (publish thiếu ảnh) | ✅ | TC-PROD-01 |
| 3 | Unit tests for **InventoryService** (reserve vượt tồn kho, release đúng số lượng) | ✅ | TC-INV-01, TC-INV-02, TC-INV-03 |
| 4 | Unit tests for **PromotionService** (voucher hết hạn, cap discount) | ✅ | TC-PROMO-01, TC-PROMO-02 |
| 5 | Integration test cho full checkout flow (user → product+inventory → cart → checkout → assert) | ✅ | TC-ORDER-01, TC-ORDER-02 |
| 6 | RBAC test: mỗi role gọi ít nhất 1 endpoint KHÔNG thuộc quyền → 403 | ✅ | TC-RBAC-01 → TC-RBAC-04 (4 roles) |
| 7 | `docs/testing/backend-test-plan.md` liệt kê đủ **≥ 15 test case** Given/When/Then | ✅ | This document — **24 TC total** |
| 8 | OrderService có 3 test checkout (Task 7.1) giữ nguyên | ✅ | `OrderServiceCheckoutTest` (pre-existing) — 5 tests |

---

## 3. Endpoint ↔ Role Mapping (spec Mục 4)

| Endpoint | Method | Allowed Roles |
|---|---|---|
| `/api/v1/orders/checkout` | POST | CUSTOMER, ADMIN |
| `/api/v1/orders/{id}/status` | PATCH | SALES, WAREHOUSE, ADMIN |
| `/api/v1/inventory/{id}/adjust` | PATCH | WAREHOUSE, ADMIN |
| `/api/v1/categories` | POST | CONTENT, ADMIN |
| `/api/v1/admin/dashboard/**` | GET | ADMIN |

---

## 4. Test Inventory

> **Source map**: each TC below points to its concrete test method on disk.

### 4.1 Unit Tests — `auth/service/AuthServiceTest.java`

#### TC-AUTH-01 — Register trùng email
- **Given**: `userRepository.existsByEmail("alice@example.com")` returns `true`
- **When**: `authService.register(RegisterRequest{email, password, fullName})` is called
- **Then**:
  - `ApiResponse.isSuccess()` is `false`
  - `ApiResponse.getMessage()` equals `"Email đã được sử dụng"`
  - `userRepository.save()` is NEVER invoked
  - `passwordEncoder.encode()` is NEVER invoked
- **Source**: `register_EmailAlreadyExists_ReturnsError`

#### TC-AUTH-02 — Register email mới
- **Given**: `userRepository.existsByEmail(...)` returns `false`
- **When**: `authService.register(...)` is called
- **Then**:
  - `ApiResponse.isSuccess()` is `true`
  - `AuthResponse.accessToken` / `refreshToken` are populated
  - `userRepository.save()` invoked 2x (initial user + refresh-token persistence)
  - `User.passwordHash` equals the BCrypt hash, NOT the raw password
  - `User.role == CUSTOMER`, `User.status == ACTIVE`
- **Source**: `register_NewEmail_Succeeds`

#### TC-AUTH-03 — Login sai password
- **Given**: a user exists with BCrypt-hashed password; `passwordEncoder.matches(raw, hash)` returns `false`
- **When**: `authService.login(LoginRequest{email, raw})` is called
- **Then**:
  - `ApiResponse.isSuccess()` is `false`
  - `ApiResponse.getMessage()` equals `"Email hoặc mật khẩu không đúng"` (generic — does not leak which side failed)
  - `JwtProvider.generateAccessToken()` / `generateRefreshToken()` are NEVER invoked
- **Source**: `login_WrongPassword_ReturnsError`

#### TC-AUTH-04 — Login email không tồn tại
- **Given**: `userRepository.findByEmail(...)` returns `Optional.empty()`
- **When**: `authService.login(...)` is called
- **Then**:
  - Returns the same generic `"Email hoặc mật khẩu không đúng"` error (security: avoid email enumeration)
  - `passwordEncoder.matches()` is NEVER invoked
- **Source**: `login_EmailNotFound_ReturnsError`

#### TC-AUTH-05 — Login tài khoản bị DISABLED
- **Given**: user exists, `User.status == DISABLED`
- **When**: `authService.login(...)` is called
- **Then**: returns `ApiResponse.error("Tài khoản đã bị vô hiệu hóa")`; password check is skipped entirely.
- **Source**: `login_DisabledAccount_ReturnsDisabledError`

#### TC-AUTH-06 — Login thành công
- **Given**: user exists, ACTIVE, password matches
- **When**: `authService.login(...)` is called
- **Then**:
  - `ApiResponse.isSuccess() == true`
  - `AuthResponse.tokenType == "Bearer"`
  - `AuthResponse.expiresIn == accessTokenExpirationMs / 1000`
  - `UserSummary` is populated with id, email, role=CUSTOMER, status=ACTIVE
- **Source**: `login_ValidCredentials_ReturnsTokens`

---

### 4.2 Unit Tests — `catalog/product/service/ProductServiceTest.java`

#### TC-PROD-01 — Publish thiếu ảnh
- **Given**: a product exists with `images = []`
- **When**: `productService.changeStatus(productId, ProductStatus.ACTIVE)` is called
- **Then**:
  - Throws `IllegalArgumentException` with message containing `"image"`
  - `productRepository.save()` is NEVER invoked
- **Source**: `changeStatus_ActiveWithoutImages_Rejected`

#### TC-PROD-01b — Publish có ảnh
- **Given**: product exists with at least one image
- **When**: `changeStatus(... ACTIVE)` is called
- **Then**: status is persisted as ACTIVE; response returns the updated product.
- **Source**: `changeStatus_ActiveWithImages_Succeeds`

#### TC-PROD-02 — Create với SKU đã tồn tại
- **Given**: `productRepository.existsBySku(SKU)` returns `true`
- **When**: `productService.create(ProductRequest)` is called
- **Then**: throws `IllegalArgumentException` with message containing `"SKU"`; `inventoryService.initForProduct(...)` is NEVER invoked.
- **Source**: `create_DuplicateSku_Rejected`

#### TC-PROD-03 — Create happy path
- **Given**: SKU is unique, category exists, no slug provided
- **When**: `create(ProductRequest)` is called
- **Then**:
  - Product persisted with auto-generated slug from name (e.g. "Bàn gỗ sồi" → `"ban-go-soi"`)
  - Initial status is DRAFT
  - `inventoryService.initForProduct(id)` is invoked exactly once
- **Source**: `create_ValidRequest_PersistsAndInitsInventory`

#### TC-PROD-04 — Create với category không tồn tại
- **Given**: `categoryRepository.existsById(...)` returns `false`
- **When**: `create(...)` is called
- **Then**: throws `IllegalArgumentException` containing `"Category"`; nothing persisted.
- **Source**: `create_MissingCategory_Rejected`

---

### 4.3 Unit Tests — `inventory/service/InventoryServiceTest.java`

#### TC-INV-01 — Reserve thành công
- **Given**: `Inventory{quantityOnHand=10, quantityReserved=0}`, requested qty=2
- **When**: `inventoryService.reserve(productId, 2)` is called
- **Then**: `mongoTemplate.findAndModify(...)` is invoked with an Update that increments `quantityReserved`; no exception.
- **Source**: `reserve_AvailableStock_Succeeds`

#### TC-INV-02 — Reserve vượt tồn kho
- **Given**: available = 1 (onHand=1, reserved=0), requested qty=3
- **When**: `inventoryService.reserve(productId, 3)` is called
- **Then**: throws `InsufficientStockException`; `findAndModify` is NEVER invoked (no DB mutation).
- **Source**: `reserve_ExceedsAvailable_ThrowsAndNoModification`

#### TC-INV-02b — Reserve khi inventory record chưa tồn tại
- **Given**: `findOne(query, Inventory.class)` returns `null`
- **When**: `reserve(...)` is called
- **Then**: throws `EntityNotFoundException` with message containing the productId.
- **Source**: `reserve_InventoryRecordMissing_ThrowsEntityNotFound`

#### TC-INV-03 — Release đúng số lượng
- **Given**: `Inventory{quantityReserved=5}`, qty=2
- **When**: `inventoryService.release(productId, 2)` is called
- **Then**: Update contains a decrement on `quantityReserved`; no exception.
- **Source**: `release_ValidAmount_Succeeds`

#### TC-INV-03b — Release khi không đủ reserved
- **Given**: `findAndModify` returns `null` (query mismatch — reserved < qty)
- **When**: `inventoryService.release(productId, 5)` is called
- **Then**: throws `InsufficientStockException`.
- **Source**: `release_ExceedsReserved_Throws`

#### TC-INV-04 — Commit giảm cả onHand + reserved
- **Given**: a reserved inventory record
- **When**: `inventoryService.commit(productId, 2)` is called
- **Then**: single `findAndModify` Update contains BOTH `quantityOnHand` AND `quantityReserved` decrements (atomic).
- **Source**: `commit_DeductsBothOnHandAndReserved`

#### TC-INV-04b — Commit khi không đủ reserved
- **Given**: `findAndModify` returns `null`
- **When**: `commit(productId, 10)` is called
- **Then**: throws `InsufficientStockException`.
- **Source**: `commit_InsufficientReserved_Throws`

#### TC-INV-05 — Input validation (qty ≤ 0)
- **Given**: any state
- **When**: `reserve / release / commit` invoked with qty ≤ 0
- **Then**: throws `IllegalArgumentException` immediately; no DB call.
- **Source**: `operations_NonPositiveQty_Rejected`

#### TC-INV-06 — getAvailable
- **Given**: `Inventory{quantityOnHand=10, quantityReserved=3}`
- **When**: `getAvailable(productId)` is called
- **Then**: returns `7` (= onHand − reserved).
- **Source**: `getAvailable_ComputesDifference`

---

### 4.4 Unit Tests — `promotion/service/PromotionServiceTest.java`

#### TC-PROMO-01 — Voucher hết hạn (theo ngày)
- **Given**: voucher has `endDate < now`
- **When**: `validateAndCalculate(code, orderAmount)` is called
- **Then**: returns `ValidatePromotionResponse{valid=false, discountAmount=0, message contains "hết hạn"}`.
- **Source**: `validate_ExpiredVoucher_ReturnsInvalid`

#### TC-PROMO-02 — PERCENTAGE cap ở maxDiscountAmount
- **Given**: PERCENTAGE voucher value=10%, max=500k, cart=10M (theoretical 10% = 1M)
- **When**: `validateAndCalculate(code, 10M)` is called
- **Then**: returns `valid=true`, `discountAmount=500000` (capped).
- **Source**: `validate_PercentageWithCap_RespectsMaxDiscount`

#### TC-PROMO-03 — Không đạt minOrderAmount
- **Given**: voucher has `minOrderAmount=1M`, cart=500k
- **When**: `validateAndCalculate(code, 500k)` is called
- **Then**: returns `valid=false`, message mentions "tối thiểu".
- **Source**: `validate_BelowMinOrder_ReturnsInvalid`

#### TC-PROMO-04 — Code không tồn tại
- **Given**: `findByCodeIgnoreCase(...)` returns `Optional.empty()`
- **When**: `validateAndCalculate(code, ...)` is called
- **Then**: returns `valid=false`, message contains "không tồn tại"; no exception.
- **Source**: `validate_UnknownCode_ReturnsInvalid`

#### TC-PROMO-05 — Hết lượt sử dụng
- **Given**: voucher has `usageLimit=100, usedCount=100`
- **When**: `validateAndCalculate(...)` is called
- **Then**: returns `valid=false`, message mentions "hết lượt".
- **Source**: `validate_UsageExhausted_ReturnsInvalid`

#### TC-PROMO-06 — PERCENTAGE không cap
- **Given**: PERCENTAGE 10%, no cap, cart=1M
- **When**: `validateAndCalculate(code, 1M)` is called
- **Then**: returns `valid=true`, `discountAmount=100000` (= 10% of 1M).
- **Source**: `validate_PercentageNoCap_CorrectAmount`

#### TC-PROMO-07 — FIXED_AMOUNT cap bằng orderAmount
- **Given**: FIXED_AMOUNT voucher value=500k, cart=300k
- **When**: `validateAndCalculate(code, 300k)` is called
- **Then**: returns `valid=true`, `discountAmount=300000` (capped at order amount — never exceeds it).
- **Source**: `validate_FixedAmountCappedAtOrder`

#### TC-PROMO-08 — Status DISABLED
- **Given**: voucher `status=DISABLED` (even with valid dates)
- **When**: `validateAndCalculate(...)` is called
- **Then**: returns `valid=false`, message mentions "vô hiệu".
- **Source**: `validate_DisabledStatus_ReturnsInvalid`

#### TC-PROMO-08b — Status EXPIRED (spec Mục 3.3 enum ACTIVE|EXPIRED|DISABLED)
- **Given**: voucher `status=EXPIRED` (manually flipped before endDate)
- **When**: `validateAndCalculate(...)` is called
- **Then**: returns `valid=false`; the status check fires before the date check.
- **Source**: `validate_ExpiredStatus_ReturnsInvalid`

#### TC-PROMO-09 — incrementUsage atomic
- **Given**: promotion exists
- **When**: `incrementUsage(code)` is called
- **Then**: `mongoTemplate.findAndModify` is invoked with Update containing `$inc: {usedCount: 1}`.
- **Source**: `incrementUsage_ExistingCode_Succeeds`

#### TC-PROMO-10 — incrementUsage code không tồn tại
- **Given**: `findAndModify` returns `null`
- **When**: `incrementUsage(code)` is called
- **Then**: throws `EntityNotFoundException`.
- **Source**: `incrementUsage_UnknownCode_Throws`

---

### 4.5 Unit Tests — `order/service/OrderServiceUpdateStatusTest.java`

> These complement the pre-existing `OrderServiceCheckoutTest.java` (5 tests, retained per Task 7.1 DoD). Together = full OrderService coverage.

#### TC-ORDER-03 — Bỏ qua bước trong state machine
- **Given**: order is `PENDING`
- **When**: `updateStatus(orderId, "SHIPPING", ...)` is called
- **Then**: throws `IllegalArgumentException("Invalid status transition")`; `save()` NEVER called; no inventory side-effect.
- **Source**: `updateStatus_PendingToShipping_SkipsSteps_Rejected`

#### TC-ORDER-04 — CONFIRMED → CANCELLED releases inventory
- **Given**: CONFIRMED order with items [{prod-A, qty=2}, {prod-B, qty=1}]
- **When**: `updateStatus(... "CANCELLED")` is called
- **Then**:
  - `inventoryService.release("prod-A", 2)` and `inventoryService.release("prod-B", 1)` are called
  - `inventoryService.commit(...)` is NEVER called
  - Order saved with `status=CANCELLED`, `paymentStatus=REFUNDED`, and a `statusHistory` entry
- **Source**: `updateStatus_ConfirmedToCancelled_ReleasesInventory`

#### TC-ORDER-05 — SHIPPING → DELIVERED commits inventory
- **Given**: SHIPPING order with items
- **When**: `updateStatus(... "DELIVERED")` is called
- **Then**:
  - `inventoryService.commit(...)` is called for each item with the correct qty
  - `inventoryService.release(...)` is NEVER called
- **Source**: `updateStatus_ShippingToDelivered_CommitsInventory`

#### TC-ORDER-06 — Invalid status string
- **Given**: any order
- **When**: `updateStatus(orderId, "NOT_A_REAL_STATUS", ...)` is called
- **Then**: throws `IllegalArgumentException`.
- **Source**: `updateStatus_InvalidStatusString_Throws`

#### TC-ORDER-07 — Terminal status không chuyển tiếp
- **Given**: order already `CANCELLED`
- **When**: `updateStatus(orderId, "CONFIRMED", ...)` is called
- **Then**: throws `IllegalArgumentException`; `save()` NEVER called.
- **Source**: `updateStatus_FromTerminal_Rejected`

#### TC-ORDER-08 — PENDING → CONFIRMED happy path
- **Given**: PENDING order
- **When**: `updateStatus(... "CONFIRMED")` is called
- **Then**:
  - Status persisted as CONFIRMED
  - No inventory side-effect (commit/release) for non-terminal transitions
  - Notification fired (mocked): `notifyOrderStatus(orderId, ..., "CONFIRMED", ...)`
- **Source**: `updateStatus_PendingToConfirmed_Succeeds`

#### TC-ORDER-09 — Order không tồn tại
- **Given**: `orderRepository.findById(...)` returns `Optional.empty()`
- **When**: `updateStatus(orderId, ...)` is called
- **Then**: throws `EntityNotFoundException`.
- **Source**: `updateStatus_OrderNotFound_Throws`

---

### 4.6 Pre-existing Unit Tests — `order/service/OrderServiceCheckoutTest.java`

> **Retained verbatim from Task 7.1 (per spec DoD #8).** 5 test cases that cover the checkout path with compensating rollback.

| TC | Source |
|---|---|
| TC-ORDER-CHECKOUT-01 — checkout thành công (sandbox card) | `checkout_Success_SandboxCard_CreatesConfirmedOrder` |
| TC-ORDER-CHECKOUT-02 — checkout thành công (COD) | `checkout_Success_COD_CreatesPendingOrder` |
| TC-ORDER-CHECKOUT-03 — hết hàng giữa chừng → rollback | `checkout_OutOfStockMidway_RollsBackPreviousReservations` |
| TC-ORDER-CHECKOUT-04 — hết hàng ngay item đầu → không rollback | `checkout_OutOfStockFirstItem_NoRollbackNeeded` |
| TC-ORDER-CHECKOUT-05 — voucher hết hạn → rollback toàn bộ | `checkout_ExpiredPromotion_RollsBackAllReservationsAndThrows` |
| TC-ORDER-CHECKOUT-06 — cart rỗng → throw ngay | `checkout_EmptyCart_ThrowsImmediately` |
| TC-ORDER-CHECKOUT-07 — address không tồn tại | `checkout_AddressNotFound_ThrowsAndNoInventoryOperations` |

---

### 4.7 Integration Tests — `order/CheckoutFlowIntegrationTest.java`

#### TC-ORDER-01 — Checkout sandbox card thành công (end-to-end)
- **Given**:
  - A `CUSTOMER` user seeded with valid `passwordHash` (BCrypt) and `status=ACTIVE`
  - An `Address` embedded in `user.addresses[]` with `id="addr-001"` (per spec Mục 3.2 — addresses are embedded, NOT a separate collection)
  - A `Product` with `status=ACTIVE` and price=500k
  - An `Inventory` record for the product with `quantityOnHand=10, quantityReserved=0`
  - A `Cart` belonging to the user with one item `{productId, quantity=2, subtotal=1M}`
- **When**:
  - `POST /api/v1/orders/checkout` is invoked via MockMvc with `CheckoutRequest{addressId="addr-001", paymentMethod=SANDBOX_CARD}` and the security principal set to the seeded customer's id with role `CUSTOMER`
- **Then**:
  - HTTP 200 with `$.success == true`, `$.data.status == "CONFIRMED"`, `$.data.paymentStatus == "PAID"`
  - Exactly 1 `Order` document persisted in `orders` collection
  - `Order.orderNumber` matches `ORD-\d{8}-\d{4}`
  - `Order.customerId` equals the seeded customer id
  - `Inventory.quantityOnHand` is still 10 (unchanged until DELIVERED commit)
  - `Inventory.quantityReserved` is now 2 (reserved at checkout)
  - `Cart.items` is empty, `Cart.totalAmount == 0`
- **Source**: `CheckoutFlowIntegrationTest#checkout_SandboxCard_Succeeds`

#### TC-ORDER-02 — Hết tồn kho → reject
- **Given**:
  - Same as TC-ORDER-01, but `Inventory.quantityOnHand` is drained to 1, and the cart requests qty=3
- **When**:
  - `POST /api/v1/orders/checkout` with the same payload structure
- **Then**:
  - HTTP 4xx (validation/insufficient-stock error)
  - `orders` collection is EMPTY
  - `Inventory.quantityOnHand` is still 1, `Inventory.quantityReserved` is still 0 (rollback path triggered)
- **Source**: `CheckoutFlowIntegrationTest#checkout_InsufficientStock_Rejected`

---

### 4.8 Integration Tests — `rbac/RbacIntegrationTest.java`

#### TC-RBAC-01 — CUSTOMER bị chặn khỏi admin dashboard
- **Given**: an authenticated user with role `CUSTOMER`
- **When**: `GET /api/v1/admin/dashboard/summary`
- **Then**: HTTP **403 Forbidden**
- **Why it matters**: dashboard is ADMIN-only (`@PreAuthorize("hasRole('ADMIN')")` on `ReportingController`).
- **Source**: `customer_DashboardEndpoint_Forbidden`

#### TC-RBAC-02 — WAREHOUSE không thể checkout
- **Given**: authenticated user with role `WAREHOUSE`
- **When**: `POST /api/v1/orders/checkout`
- **Then**: HTTP **403 Forbidden**
- **Source**: `warehouse_CheckoutEndpoint_Forbidden`

#### TC-RBAC-03 — CONTENT không thể điều chỉnh inventory
- **Given**: authenticated user with role `CONTENT`
- **When**: `PATCH /api/v1/inventory/prod-fake/adjust`
- **Then**: HTTP **403 Forbidden**
- **Why it matters**: stock adjustment is destructive (can drive `quantityOnHand` negative). CONTENT manages catalog only.
- **Source**: `content_InventoryAdjustEndpoint_Forbidden`

#### TC-RBAC-04 — SALES không thể tạo category
- **Given**: authenticated user with role `SALES`
- **When**: `POST /api/v1/categories`
- **Then**: HTTP **403 Forbidden**
- **Source**: `sales_CategoryCreateEndpoint_Forbidden`

#### TC-RBAC-05 — Anonymous request tới protected endpoint
- **Given**: no authentication
- **When**: `GET /api/v1/admin/dashboard/summary`
- **Then**: HTTP **401 Unauthorized** (not 403 — auth fails before authorization)
- **Source**: `anonymous_ProtectedEndpoint_Unauthorized`

#### TC-RBAC-06 — SALES VẪN ĐƯỢC update order status (sanity check)
- **Given**: authenticated user with role `SALES`
- **When**: `PATCH /api/v1/orders/order-nonexistent/status`
- **Then**: response is **NOT 403** (typically 404 because the order doesn't exist — RBAC is correctly permissive)
- **Why it matters**: spec Mục 4 explicitly grants `SALES, WAREHOUSE, ADMIN` permission to update order status. We must NOT over-tighten RBAC.
- **Source**: `sales_OrderStatusEndpoint_Authorized`

---

## 5. How to Run

```bash
cd backend/furniture-api

# Run only unit tests (no Mongo)
mvn test -Dtest='*Test' -DexcludedGroups=integration

# Run only integration tests
mvn test -Dtest='*IntegrationTest'

# Run everything (unit + integration)
mvn test
```

The embedded MongoDB process is started/stopped automatically by `de.flapdoodle.embed.mongo.spring30x` for each test class.

---

## 6. Coverage Matrix

| Module | Service | Test File | Test Cases |
|--------|---------|-----------|-----------:|
| auth | AuthService | AuthServiceTest | 6 |
| catalog.product | ProductService | ProductServiceTest | 5 |
| inventory | InventoryService | InventoryServiceTest | 7 |
| promotion | PromotionService | PromotionServiceTest | 11 |
| order | OrderService | OrderServiceCheckoutTest (existing, retained) | 7 |
| order | OrderService | OrderServiceUpdateStatusTest (new) | 7 |
| order | OrderController | CheckoutFlowIntegrationTest | 2 |
| rbac | SecurityConfig | RbacIntegrationTest | 6 |
| auth | UserRepository | UserRepositoryTest (existing) | 4 |
| config | HealthController | HealthControllerTest (existing) | 1 |
| review | ReviewService | ReviewServiceTest (existing) | (existing) |
| **TOTAL** | | | **56+** |

---

## 7. Architectural Notes (compliance with spec)

- **`Address` is embedded** in `User.addresses[]` per spec Mục 3.2 (`User.addresses[] | Embed | Thuộc sở hữu User, không chia sẻ`). We do NOT create a standalone `addresses` collection. `OrderService.checkout` resolves the address by scanning the embedded list.
- **`OrderController.updateStatus` allows `SALES, WAREHOUSE, ADMIN`** per spec Mục 4 / Task 7.1. SALES must keep status-update access — TC-RBAC-06 enforces this.
- **`PromotionStatus` enum is `ACTIVE | EXPIRED | DISABLED`** per spec Mục 3.3 — TC-PROMO-08b covers EXPIRED.

---

## 8. Definition of Done — Final Checklist

- [x] `AuthServiceTest` covers register trùng email + login sai password (TC-AUTH-01, TC-AUTH-03)
- [x] `ProductServiceTest` covers publish thiếu ảnh (TC-PROD-01)
- [x] `InventoryServiceTest` covers reserve vượt tồn + release đúng (TC-INV-02, TC-INV-03)
- [x] `PromotionServiceTest` covers voucher hết hạn + cap discount (TC-PROMO-01, TC-PROMO-02)
- [x] `CheckoutFlowIntegrationTest` covers user → product+inventory → cart → checkout → assert (TC-ORDER-01)
- [x] `RbacIntegrationTest` covers each of CUSTOMER, SALES, WAREHOUSE, CONTENT against one forbidden endpoint (TC-RBAC-01..04)
- [x] OrderService tests preserved (pre-existing `OrderServiceCheckoutTest` retained as-is — 7 tests covering happy path, rollback on stockout, rollback on expired promotion, empty cart, missing address)
- [x] Embedded MongoDB integration (no Docker required)
- [x] Spec compliance: embedded Address, SALES preserves status-update permission, EXPIRED status covered
