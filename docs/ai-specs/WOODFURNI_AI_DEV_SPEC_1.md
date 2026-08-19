# WOODFURNI — AI DEVELOPMENT SPECIFICATION
### Tài liệu kỹ thuật để triển khai bằng Cursor (copy prompt → chạy → kiểm tra → prompt tiếp theo)

> Tài liệu này được tổng hợp lại từ file đề xuất đồ án `WOODFURNI_-_HUY.docx`, sắp xếp lại theo thứ tự triển khai thực tế cho một AI coding agent (Cursor), thay vì thứ tự trình bày học thuật (SRS → UML → code). Mục tiêu: bạn chỉ cần mở từng Task trong mục 6, copy nguyên khối "PROMPT" vào Cursor, để nó code xong, đối chiếu "Tiêu chí kiểm tra", rồi qua Task kế tiếp.

---

## 0. CÁCH DÙNG TÀI LIỆU NÀY

1. **Giữ file này (hoặc các file con trong `docs/ai-spec/`) mở trong Cursor** ở dạng `@file` reference, để Cursor luôn "nhìn thấy" schema và API contract khi sinh code — tránh việc mỗi task nó tự bịa lại field/endpoint khác với task trước.
2. Đi theo đúng thứ tự Phase 0 → Phase 14 ở mục 6. Đừng nhảy cóc sang Order trước khi Auth + Catalog + Inventory đã xong, vì các prompt sau **luôn giả định** các model/service ở prompt trước đã tồn tại.
3. Sau mỗi task: chạy build/test, đối chiếu "Tiêu chí kiểm tra (DoD)". Nếu Cursor sai, **sửa bằng cách bổ sung yêu cầu vào đúng prompt đó** (không mở task mới) — sẽ tiết kiệm request hơn là để lỗi trôi sang task sau.
4. Mỗi prompt được viết **độc lập, đủ ngữ cảnh** để bạn có thể dùng ở chat mới của Cursor nếu cần reset context (không phụ thuộc lịch sử chat).
5. Khi bạn thấy comment `// TODO(spec)`, đó là chỗ mình cố tình để Cursor tự quyết định trong khuôn khổ, không cần bạn đặc tả 100%.

Đề xuất tách file:
```
docs/ai-spec/
├── 01-folder-structure.md
├── 02-mongodb-schema.md
├── 03-api-contract.md
├── 04-modules.md
└── 05-cursor-prompts.md      ← 90% thời gian bạn sẽ mở file này
```
(Bạn có thể copy nguyên từng mục bên dưới vào các file tương ứng.)

---

## 1. TỔNG QUAN HỆ THỐNG & TECH STACK

**Đề tài:** WOODFURNI — hệ thống thương mại điện tử kinh doanh đồ gỗ nội thất trong nhà & ngoài trời (doanh nghiệp giả lập: *Mộc Việt Furniture*).

**Kiến trúc (Modular Monolith, không làm microservices thật):**

```
React (Customer + Admin)
        │  HTTPS/JSON
        ▼
Node.js Gateway (Express + Socket.IO)
   - Auth passthrough / rate limit
   - WebSocket: realtime order notification, low-stock alert
   - Upload/media proxy (tuỳ chọn)
        │
        ▼
Spring Boot (Modular Monolith)
   modules: auth · catalog · inventory · cart · order ·
            payment · promotion · review · reporting
        │
        ▼
      MongoDB
```

| Công nghệ | Vai trò |
|---|---|
| ReactJS | Customer website + Admin dashboard (2 app riêng trong `frontend/`) |
| Node.js + Express | API Gateway, WebSocket, notification |
| Spring Boot (Layered: Controller → Service → Repository) | Toàn bộ business logic |
| Spring Security + JWT | Authentication/Authorization (RBAC) |
| Spring Data MongoDB | Data access layer |
| MongoDB | CSDL chính (document-based) |
| Socket.IO / STOMP over WebSocket | Notification realtime |
| Docker Compose | Đóng gói & chạy toàn bộ stack |
| Swagger/OpenAPI | API documentation, đối chiếu contract |
| JUnit + Mockito | Backend test |
| React Testing Library | Frontend test |

**Roles (RBAC — enforce ở backend, không chỉ ẩn UI):**

| Role | Quyền |
|---|---|
| `CUSTOMER` | Mua hàng, review, theo dõi đơn của chính mình |
| `SALES` | Xem/xử lý đơn hàng, cập nhật trạng thái đơn |
| `WAREHOUSE` | Quản lý tồn kho, xác nhận stock |
| `CONTENT` | Quản lý sản phẩm/danh mục/nội dung |
| `ADMIN` | Toàn quyền, xem dashboard |

**10 phân hệ nghiệp vụ (map sang module code ở mục 5):**
M01 Authentication · M02 Product Catalog · M03 Category/Material · M04 Search & Filter · M05 Cart · M06 Order · M07 Inventory · M08 Promotion · M09 Review · M10 Administration/Reporting (+ hạ tầng: Node Gateway/Realtime, Payment sandbox).

---

## 2. CẤU TRÚC THƯ MỤC (MONOREPO)

```
woodfurni/
├── frontend/
│   ├── customer-app/                # React — website khách hàng
│   │   ├── src/
│   │   │   ├── assets/
│   │   │   ├── components/          # UI thuần, không gọi API
│   │   │   ├── pages/                # Home, ProductList, ProductDetail, Cart, Checkout, Orders...
│   │   │   ├── layouts/
│   │   │   ├── hooks/                # useCart, useAuth, useProducts...
│   │   │   ├── contexts/             # AuthContext, CartContext
│   │   │   ├── services/             # axios instance + api*.js theo module
│   │   │   ├── utils/
│   │   │   ├── routes/
│   │   │   └── features/             # feature-based: auth/, catalog/, cart/, order/, review/
│   │   └── package.json
│   │
│   └── admin-app/                    # React — Admin Portal (project riêng)
│       └── src/ (cấu trúc tương tự, thêm features/dashboard, features/reports)
│
├── gateway/
│   └── node-gateway/
│       ├── src/
│       │   ├── config/
│       │   ├── middleware/           # auth verify, rate-limit
│       │   ├── routes/               # reverse-proxy sang Spring Boot (tuỳ chọn)
│       │   ├── socket/               # namespace order, inventory
│       │   ├── notification/         # publisher/subscriber logic
│       │   └── app.js
│       └── package.json
│
├── backend/
│   └── furniture-api/
│       ├── src/main/java/com/woodfurni/
│       │   ├── config/               # MongoConfig, SecurityConfig, SwaggerConfig, CorsConfig
│       │   ├── common/                # ApiResponse<T>, PageResponse<T>, GlobalExceptionHandler
│       │   ├── auth/                  # module
│       │   │   ├── controller / service / repository / dto / model
│       │   ├── catalog/               # product, category, material
│       │   ├── inventory/
│       │   ├── cart/
│       │   ├── order/
│       │   ├── payment/
│       │   ├── promotion/
│       │   ├── review/
│       │   ├── reporting/
│       │   ├── security/             # JwtFilter, JwtProvider, UserDetailsServiceImpl
│       │   └── FurnitureApiApplication.java
│       ├── src/main/resources/application.yml
│       ├── src/test/java/com/woodfurni/...
│       └── pom.xml
│
├── database/
│   ├── sample-data/                  # *.json seed cho từng collection
│   ├── indexes/                      # script tạo index (mongosh .js)
│   └── backup/
│
├── docker/
│   └── docker-compose.yml            # mongo, backend, gateway, frontend (build), mongo-express (tuỳ chọn)
│
├── docs/
│   ├── ai-spec/                      # 5 file tách từ tài liệu này
│   ├── requirements/  ├── uml/  ├── api/  ├── testing/  └── thesis/
│
├── postman/
│   └── WOODFURNI.postman_collection.json
│
├── README.md
└── .gitignore
```

> **Ghi chú thiết kế:** module trong Spring Boot tổ chức theo **feature package** (`auth/`, `catalog/`...) chứ không theo layer toàn cục (`controller/`, `service/`...) — dễ maintain hơn khi hệ thống có 10 phân hệ, và là bước đệm tự nhiên nếu sau này tách microservices (mỗi package = 1 service tương lai).

---

## 3. MONGODB SCHEMA

### 3.1. Danh sách 12 collection

| Collection | Chức năng |
|---|---|
| `users` | Tài khoản (customer + nhân viên) |
| `roles` | Danh mục quyền (seed tĩnh) |
| `products` | Sản phẩm nội thất |
| `categories` | Danh mục (Indoor/Outdoor → Room) |
| `materials` | Loại gỗ/vật liệu |
| `inventories` | Tồn kho, tách riêng khỏi `products` |
| `carts` | Giỏ hàng |
| `orders` | Đơn hàng (chứa snapshot `items`) |
| `payments` | Giao dịch thanh toán (sandbox) |
| `promotions` | Voucher/khuyến mãi |
| `reviews` | Đánh giá sản phẩm |
| `notifications` | Thông báo realtime (đọc/chưa đọc) |

### 3.2. Nguyên tắc Embed vs Reference

| Dữ liệu | Cách lưu | Lý do |
|---|---|---|
| `Product.dimensions` | Embed | Luôn đọc cùng Product |
| `Product.images[]` | Embed | Danh sách nhỏ, không truy vấn độc lập |
| `User.addresses[]` | Embed | Thuộc sở hữu User, không chia sẻ |
| `Cart.items[]` | Embed | Luôn đọc/ghi cùng Cart |
| `Order.items[]` | **Embed (snapshot)** | Giữ nguyên tên/giá tại thời điểm mua dù Product đổi sau này |
| `Order.shippingAddress` | Embed (snapshot) | Giữ lịch sử giao hàng |
| `Product.category` | Reference (`categoryId`) | Category dùng chung nhiều sản phẩm |
| `Product.materials[]` | Reference (`materialIds[]`) | Material dùng chung |
| `Product ↔ Inventory` | Reference (`productId`) | Nghiệp vụ tồn kho thay đổi độc lập, tần suất ghi khác Product |
| `Review.productId` / `Review.userId` | Reference | Review tăng độc lập, không nên phình Product |
| `Order.customerId` | Reference | Customer tồn tại độc lập với Order |

### 3.3. Chi tiết field từng collection

#### `users`
```json
{
  "_id": "ObjectId",
  "email": "string (unique, required)",
  "passwordHash": "string (required)",
  "fullName": "string (required)",
  "phone": "string",
  "role": "enum: CUSTOMER|SALES|WAREHOUSE|CONTENT|ADMIN (required, default CUSTOMER)",
  "addresses": [
    { "id": "string", "label": "string", "line1": "string", "ward": "string",
      "district": "string", "city": "string", "isDefault": "boolean" }
  ],
  "status": "enum: ACTIVE|DISABLED (default ACTIVE)",
  "createdAt": "datetime",
  "updatedAt": "datetime"
}
```
Index: `{ email: 1 }` unique · `{ role: 1 }`

#### `categories`
```json
{
  "_id": "ObjectId",
  "name": "string (required)",
  "slug": "string (unique, required)",
  "environment": "enum: INDOOR|OUTDOOR|BOTH (required)",
  "parentId": "ObjectId | null",
  "order": "int",
  "status": "enum: ACTIVE|HIDDEN"
}
```
Index: `{ slug: 1 }` unique · `{ environment: 1, parentId: 1 }`

#### `materials`
```json
{ "_id": "ObjectId", "name": "string (required, unique)", "code": "string (e.g. OAK, WALNUT, TEAK)",
  "description": "string" }
```

#### `products` (theo đúng đặc thù ngành gỗ trong docx gốc)
```json
{
  "_id": "ObjectId",
  "sku": "string (required, unique)",
  "name": "string (required, 3-200 ký tự)",
  "slug": "string (unique)",
  "categoryId": "ObjectId (ref categories, required)",
  "materialIds": ["ObjectId (ref materials)"],
  "environment": "enum: INDOOR|OUTDOOR|BOTH (required)",
  "room": "enum: LIVING_ROOM|BEDROOM|DINING_ROOM|OFFICE|GARDEN|BALCONY|PATIO",
  "dimensions": { "width": "number(cm)", "height": "number(cm)", "depth": "number(cm)" },
  "weight": "number(kg)",
  "color": "string",
  "finish": "string",
  "price": "decimal (required, > 0)",
  "salePrice": "decimal | null",
  "images": ["string (url)"],
  "description": "string",
  "warranty": "string (vd: '24 tháng')",
  "status": "enum: DRAFT|ACTIVE|OUT_OF_STOCK|DISCONTINUED (default DRAFT)",
  "ratingAverage": "number (denormalized, cập nhật khi có review mới)",
  "ratingCount": "int (denormalized)",
  "createdAt": "datetime", "updatedAt": "datetime"
}
```
Index: `{ sku: 1 }` unique · `{ slug: 1 }` unique ·
`{ categoryId: 1, environment: 1, status: 1 }` (lọc theo danh mục) ·
`{ "price": 1 }` · text index `{ name: "text", description: "text" }` (search) ·
`{ environment: 1, room: 1, materialIds: 1, price: 1 }` (compound cho filter đa điều kiện — đúng use case "Bàn Teak ngoài trời, dưới 1.5m, 5-10 triệu" trong docx gốc).

#### `inventories`
```json
{
  "_id": "ObjectId",
  "productId": "ObjectId (ref products, required, unique)",
  "quantityOnHand": "int (required, >= 0)",
  "quantityReserved": "int (default 0, >= 0)",
  "lowStockThreshold": "int (default 5)",
  "updatedAt": "datetime"
}
```
Index: `{ productId: 1 }` unique · `{ quantityOnHand: 1 }` (để query low-stock nhanh)
`quantityAvailable = quantityOnHand - quantityReserved` (tính ở tầng service, không lưu).

#### `carts`
```json
{
  "_id": "ObjectId",
  "userId": "ObjectId (ref users, required, unique — 1 user 1 cart đang mở)",
  "items": [
    { "productId": "ObjectId", "productName": "string", "unitPrice": "decimal",
      "quantity": "int (>=1)", "subtotal": "decimal" }
  ],
  "totalAmount": "decimal",
  "updatedAt": "datetime"
}
```
Index: `{ userId: 1 }` unique

#### `orders`
```json
{
  "_id": "ObjectId",
  "orderNumber": "string (required, unique, vd ORD-20260819-0001)",
  "customerId": "ObjectId (ref users, required)",
  "items": [
    { "productId": "ObjectId", "productName": "string", "sku": "string",
      "unitPrice": "decimal", "quantity": "int", "subtotal": "decimal" }
  ],
  "shippingAddress": { "label": "string", "line1": "string", "ward": "string",
                         "district": "string", "city": "string", "phone": "string" },
  "promotionCode": "string | null",
  "discountAmount": "decimal (default 0)",
  "subtotalAmount": "decimal",
  "totalAmount": "decimal",
  "status": "enum: PENDING|CONFIRMED|PROCESSING|SHIPPING|DELIVERED|CANCELLED|RETURNED (required, default PENDING)",
  "paymentStatus": "enum: UNPAID|PAID|FAILED|REFUNDED (default UNPAID)",
  "statusHistory": [{ "status": "string", "changedAt": "datetime", "changedBy": "ObjectId" }],
  "createdAt": "datetime", "updatedAt": "datetime"
}
```
Index: `{ orderNumber: 1 }` unique · `{ customerId: 1, createdAt: -1 }` · `{ status: 1 }`

#### `payments`
```json
{ "_id": "ObjectId", "orderId": "ObjectId (ref orders, required)",
  "method": "enum: COD|SANDBOX_CARD|SANDBOX_WALLET",
  "amount": "decimal (required)", "status": "enum: PENDING|SUCCESS|FAILED",
  "transactionRef": "string", "paidAt": "datetime | null", "createdAt": "datetime" }
```
Index: `{ orderId: 1 }`

#### `promotions`
```json
{ "_id": "ObjectId", "code": "string (required, unique)",
  "type": "enum: PERCENTAGE|FIXED_AMOUNT",
  "value": "decimal (required)", "minOrderAmount": "decimal (default 0)",
  "maxDiscountAmount": "decimal | null",
  "startDate": "datetime", "endDate": "datetime",
  "usageLimit": "int | null", "usedCount": "int (default 0)",
  "status": "enum: ACTIVE|EXPIRED|DISABLED" }
```
Index: `{ code: 1 }` unique

#### `reviews`
```json
{ "_id": "ObjectId", "productId": "ObjectId (ref products, required)",
  "userId": "ObjectId (ref users, required)", "orderId": "ObjectId (ref orders — chỉ review khi đã mua)",
  "rating": "int (required, 1-5)", "comment": "string",
  "status": "enum: VISIBLE|HIDDEN (default VISIBLE)", "createdAt": "datetime" }
```
Index: `{ productId: 1, createdAt: -1 }` · `{ userId: 1, productId: 1, orderId: 1 }` unique (chống review trùng trên cùng đơn)

#### `notifications`
```json
{ "_id": "ObjectId", "userId": "ObjectId (required)", "type": "enum: ORDER_STATUS|LOW_STOCK|PROMOTION",
  "title": "string", "message": "string", "payload": "object", "isRead": "boolean (default false)",
  "createdAt": "datetime" }
```
Index: `{ userId: 1, isRead: 1, createdAt: -1 }`

### 3.4. Chuẩn hoá response wrapper (áp dụng cho toàn bộ API contract ở mục 4)

```json
// Success — single object
{ "success": true, "message": "OK", "data": { ... }, "timestamp": "2026-08-19T10:00:00Z" }

// Success — list có phân trang
{ "success": true, "message": "OK",
  "data": { "items": [ ... ], "page": 0, "size": 20, "totalElements": 120, "totalPages": 6 },
  "timestamp": "..." }

// Error
{ "success": false, "message": "Validation failed",
  "errors": [ { "field": "price", "message": "must be > 0" } ],
  "timestamp": "..." }
```

---

## 4. API CONTRACT (tổng thể)

Base path: `/api/v1`. Auth: `Authorization: Bearer <JWT>`. Role được ghi trong ngoặc `[...]`; không ghi = public.

### Auth — M01
| Method | Path | Role | Mô tả |
|---|---|---|---|
| POST | `/auth/register` | public | Đăng ký CUSTOMER |
| POST | `/auth/login` | public | Trả `accessToken` + `refreshToken` |
| POST | `/auth/refresh` | public | Cấp lại accessToken |
| GET | `/auth/me` | authenticated | Thông tin user hiện tại |
| POST | `/auth/logout` | authenticated | Vô hiệu refreshToken |

### Catalog — M02/M03
| Method | Path | Role | Mô tả |
|---|---|---|---|
| GET | `/categories` | public | Danh sách danh mục (dạng cây) |
| POST/PUT/DELETE | `/categories(/{id})` | `CONTENT,ADMIN` | Quản trị danh mục |
| GET | `/materials` | public | Danh sách vật liệu |
| POST/PUT/DELETE | `/materials(/{id})` | `CONTENT,ADMIN` | Quản trị vật liệu |
| GET | `/products` | public | Xem mục 4.1 filter bên dưới |
| GET | `/products/{id}` | public | Chi tiết sản phẩm |
| POST | `/products` | `CONTENT,ADMIN` | Tạo sản phẩm (status mặc định DRAFT) |
| PUT | `/products/{id}` | `CONTENT,ADMIN` | Cập nhật |
| PATCH | `/products/{id}/status` | `CONTENT,ADMIN` | Publish/unpublish |
| DELETE | `/products/{id}` | `ADMIN` | Xoá mềm |

**4.1. Search & Filter (M04)** — `GET /api/v1/products` hỗ trợ query params:
```
?keyword=bàn ăn
&category=dining-room
&environment=OUTDOOR
&woodType=TEAK
&minPrice=5000000&maxPrice=10000000
&sort=price,asc      // hoặc -price, -createdAt, ratingAverage
&page=0&size=20
```

### Cart — M05
| Method | Path | Role | Mô tả |
|---|---|---|---|
| GET | `/cart` | `CUSTOMER` | Lấy giỏ hàng hiện tại (tạo mới nếu chưa có) |
| POST | `/cart/items` | `CUSTOMER` | Thêm sản phẩm — body `{ productId, quantity }` |
| PUT | `/cart/items/{productId}` | `CUSTOMER` | Cập nhật số lượng |
| DELETE | `/cart/items/{productId}` | `CUSTOMER` | Xoá 1 item |
| DELETE | `/cart` | `CUSTOMER` | Xoá toàn bộ giỏ |

### Order — M06
| Method | Path | Role | Mô tả |
|---|---|---|---|
| POST | `/orders/checkout` | `CUSTOMER` | Tạo đơn từ Cart (xem sequence 4.2) |
| GET | `/orders` | `CUSTOMER` (đơn của mình) / `SALES,ADMIN` (tất cả, filter `status`, `customerId`) | Danh sách đơn |
| GET | `/orders/{id}` | chủ đơn hoặc `SALES,ADMIN` | Chi tiết đơn |
| PATCH | `/orders/{id}/status` | `SALES,WAREHOUSE,ADMIN` | Chuyển trạng thái (validate theo state machine 4.3) |
| POST | `/orders/{id}/cancel` | chủ đơn (khi còn PENDING) hoặc `ADMIN` | Huỷ đơn, hoàn tồn kho |

**4.2. Sequence checkout (rút gọn từ workflow gốc):**
```
Customer POST /orders/checkout { addressId, promotionCode? , paymentMethod }
  → OrderService đọc Cart hiện tại
  → validate stock từng item (InventoryService.reserve)
  → nếu có promotionCode: PromotionService.validateAndApply
  → tạo Order (status=PENDING, snapshot items+address)
  → InventoryService: quantityReserved += qty (transaction/atomic update)
  → PaymentService: tạo Payment record (sandbox) → nếu PAID → status=CONFIRMED
  → xoá/clear Cart
  → NodeGateway: emit WebSocket "order.created" cho Admin
  → response: Order vừa tạo
```
Ghi chú: nghiệp vụ trừ kho + tạo order nên nằm trong 1 MongoDB transaction (hoặc dùng optimistic locking bằng `findOneAndUpdate` với điều kiện `quantityOnHand - quantityReserved >= qty`) để tránh oversell.

**4.3. Order status state machine (M06/M07):**
```
PENDING → CONFIRMED → PROCESSING → SHIPPING → DELIVERED
   │                                              
   └────────────→ CANCELLED (chỉ từ PENDING/CONFIRMED)
DELIVERED → RETURNED (trong hạn đổi trả, tuỳ chọn)
```
Mỗi lần đổi status → push vào `statusHistory`, và nếu chuyển sang `CANCELLED` → hoàn `quantityReserved`.

### Inventory — M07
| Method | Path | Role | Mô tả |
|---|---|---|---|
| GET | `/inventory` | `WAREHOUSE,ADMIN` | Danh sách tồn kho (kèm tên SP) |
| GET | `/inventory/low-stock` | `WAREHOUSE,ADMIN` | SP có `quantityOnHand <= lowStockThreshold` |
| PATCH | `/inventory/{productId}/adjust` | `WAREHOUSE,ADMIN` | body `{ delta, reason }` — nhập/xuất kho thủ công |

### Promotion — M08
| Method | Path | Role | Mô tả |
|---|---|---|---|
| GET/POST/PUT/DELETE | `/promotions(/{id})` | `ADMIN` | CRUD voucher |
| POST | `/promotions/validate` | `CUSTOMER` | body `{ code, cartTotal }` → trả số tiền giảm dự kiến (dùng ở trang Cart trước khi checkout) |

### Review — M09
| Method | Path | Role | Mô tả |
|---|---|---|---|
| GET | `/products/{productId}/reviews` | public | Danh sách review + rating trung bình |
| POST | `/products/{productId}/reviews` | `CUSTOMER` | Chỉ khi có `orderId` ở trạng thái DELIVERED chứa sản phẩm này |
| PATCH | `/reviews/{id}/status` | `ADMIN` | Ẩn review vi phạm |

### Administration/Reporting — M10
| Method | Path | Role | Mô tả |
|---|---|---|---|
| GET | `/admin/dashboard/summary` | `ADMIN` | Revenue today, orders today, new customers, low-stock count |
| GET | `/admin/dashboard/revenue?range=month` | `ADMIN` | Chart data: revenue theo tháng |
| GET | `/admin/dashboard/orders-by-status` | `ADMIN` | Chart data |
| GET | `/admin/dashboard/top-products?limit=10` | `ADMIN` | Best-selling |
| GET | `/admin/dashboard/category-breakdown` | `ADMIN` | Indoor vs Outdoor, revenue by category |

### Realtime (Node Gateway, không phải REST)
| Channel/Event | Hướng | Payload |
|---|---|---|
| `ws /realtime` (kết nối kèm JWT) | client subscribe | — |
| `order.status.updated` | server → customer đó | `{ orderId, orderNumber, status }` |
| `order.created` | server → tất cả ADMIN/SALES online | `{ orderId, orderNumber, totalAmount }` |
| `inventory.low_stock` | server → tất cả WAREHOUSE/ADMIN online | `{ productId, productName, quantityOnHand, threshold }` |

---

## 5. DANH SÁCH MODULE (M01–M10) & THỨ TỰ PHỤ THUỘC

```
M01 Auth ─┬─► M03 Category/Material ─► M02 Product Catalog ─► M04 Search&Filter
          │                                    │
          │                                    ▼
          │                              M07 Inventory
          │                                    │
          └─► M05 Cart ◄───────────────────────┘
                  │
                  ▼
          M08 Promotion
                  │
                  ▼
              M06 Order ──► Payment (sandbox) ──► M09 Review
                  │
                  ▼
          M10 Administration/Reporting

Song song, sau khi M06 ổn định:
  Node Gateway (WebSocket/Realtime) — bọc quanh M06 + M07
```

Nguyên tắc: **backend trước, module theo đúng thứ tự phụ thuộc trên, frontend theo sau từng cụm chức năng đã có API**, không code frontend trước khi API tương ứng đã pass test — tránh Cursor phải mock API rồi sau phải sửa lại 2 lần.

---

## 6. CURSOR PROMPTS THEO TỪNG TASK

> Mỗi Task = 1 lần copy prompt vào Cursor. Format: **Mục tiêu → Prompt → Deliverables → Tiêu chí kiểm tra (DoD)**.

### PHASE 0 — Khởi tạo Monorepo

**Task 0.1 — Scaffold repo**

Mục tiêu: dựng khung thư mục đúng mục 2, khởi tạo git, README v0.1.

```
PROMPT:
Bạn là senior full-stack engineer. Hãy khởi tạo một monorepo tên "woodfurni" theo đúng cấu trúc thư mục sau (chỉ tạo thư mục + file rỗng/placeholder, CHƯA viết logic):

[dán nguyên khối cây thư mục ở Mục 2 tài liệu này vào đây]

Yêu cầu:
- Tạo .gitignore chuẩn cho Node + Java (Maven) + React.
- Tạo README.md gốc mô tả ngắn gọn dự án WOODFURNI (thương mại điện tử đồ gỗ nội thất), tech stack, và hướng dẫn "how to run" (điền placeholder, sẽ cập nhật dần).
- KHÔNG init package.json/pom.xml chi tiết ở bước này, chỉ tạo thư mục rỗng có .gitkeep nếu cần — các task sau sẽ init từng project riêng.
```

DoD:
- [ ] `tree woodfurni` khớp với cấu trúc mục 2 (sai khác nhỏ về sub-package Java chấp nhận được).
- [ ] `git status` sạch sau khi add + commit đầu tiên.
- [ ] README có phần Tech Stack đúng bảng ở Mục 1.

---

### PHASE 1 — Spring Boot Foundation

**Task 1.1 — Khởi tạo Spring Boot project + kết nối MongoDB**

```
PROMPT:
Trong thư mục backend/furniture-api, khởi tạo project Spring Boot 3.x (Java 17+), group "com.woodfurni", artifact "furniture-api", dùng Maven. Dependencies: Spring Web, Spring Data MongoDB, Spring Security, Validation, Lombok, Springdoc OpenAPI (Swagger UI), JJWT (io.jsonwebtoken) cho JWT.

Yêu cầu:
1. application.yml đọc MongoDB URI từ biến môi trường MONGODB_URI (default mongodb://localhost:27017/woodfurni), port server 8080, context-path /api/v1.
2. Tạo package com.woodfurni.common gồm:
   - ApiResponse<T>: record/class với success, message, data, timestamp — đúng format ở Mục 3.4 tài liệu spec (tôi sẽ dán format bên dưới).
   - PageResponse<T>: items, page, size, totalElements, totalPages.
   - GlobalExceptionHandler (@RestControllerAdvice): bắt MethodArgumentNotValidException, EntityNotFoundException (tự định nghĩa), trả về ApiResponse lỗi đúng format lỗi trong spec.
3. Tạo endpoint GET /api/v1/health trả ApiResponse.success("OK") để test.
4. Cấu hình Swagger UI tại /swagger-ui.html.

Response format cần tuân thủ:
[dán JSON success/error/list ở Mục 3.4]

Không code business logic gì khác ở task này.
```

DoD:
- [ ] `mvn spring-boot:run` chạy được, kết nối MongoDB local thành công (log không lỗi).
- [ ] `GET /api/v1/health` trả đúng format `ApiResponse` success.
- [ ] Trả lỗi validation mẫu (gọi 1 endpoint POST tạm với body sai) → đúng format lỗi.
- [ ] `/swagger-ui.html` load được.

**Task 1.2 — Base entity, response chuẩn cho toàn bộ module**

```
PROMPT:
Bổ sung vào com.woodfurni.common:
- BaseAuditable: abstract class có createdAt, updatedAt (dùng @CreatedDate/@LastModifiedDate của Spring Data MongoDB Auditing). Bật @EnableMongoAuditing trong config.
- Một annotation/util để mọi Controller trả ApiResponse thay vì raw object (có thể dùng @RestControllerAdvice ResponseBodyAdvice để tự động wrap, HOẶC yêu cầu mọi controller method trả ApiResponse<T> tường minh — chọn cách tường minh cho dễ đọc code).

Không tạo entity nghiệp vụ cụ thể ở bước này.
```

DoD:
- [ ] Build thành công, không có entity nghiệp vụ nào bị tạo thừa.
- [ ] `createdAt/updatedAt` tự set khi test thử với 1 entity tạm (viết test rồi xoá, hoặc review code).

---

### PHASE 2 — Auth Module (M01)

**Task 2.1 — User model, Repository, Role enum**

```
PROMPT:
Tạo package com.woodfurni.auth với model User (collection "users") theo đúng schema:

[dán JSON schema `users` ở Mục 3.3]

- Enum Role: CUSTOMER, SALES, WAREHOUSE, CONTENT, ADMIN.
- Enum UserStatus: ACTIVE, DISABLED.
- Class Address (embedded, KHÔNG phải collection riêng) với các field id, label, line1, ward, district, city, isDefault.
- UserRepository extends MongoRepository<User, String> với findByEmail(String email).
- Tạo index unique cho email (dùng @Indexed(unique = true) trên field email).

Không viết Controller/Service ở bước này.
```

DoD:
- [ ] Compile OK. Insert thử 2 user cùng email qua test/console → user thứ 2 bị MongoDB từ chối do unique index.

**Task 2.2 — Đăng ký, đăng nhập, JWT, Spring Security**

```
PROMPT:
Hoàn thiện module auth (com.woodfurni.auth + com.woodfurni.security):

1. DTO: RegisterRequest(email, password, fullName, phone), LoginRequest(email, password), AuthResponse(accessToken, refreshToken, user: UserSummary).
2. AuthService: register() — hash password bằng BCryptPasswordEncoder, role mặc định CUSTOMER, check email trùng → throw lỗi rõ ràng ("Email đã được sử dụng"). login() — xác thực, sinh accessToken (hết hạn 1h) + refreshToken (hết hạn 7 ngày) bằng JJWT, payload JWT chứa userId + role.
3. JwtProvider: generateToken, validateToken, getUserIdFromToken, getRoleFromToken.
4. JwtAuthenticationFilter (OncePerRequestFilter): đọc header Authorization: Bearer <token>, set SecurityContext nếu hợp lệ.
5. SecurityConfig: stateless session, permitAll cho /auth/**, /products/** (GET), /categories/** (GET); các path còn lại theo role sẽ cấu hình dần ở các task sau — tạm thời requireAuthenticated() cho mọi path khác /auth và GET catalog.
6. AuthController:
   - POST /api/v1/auth/register
   - POST /api/v1/auth/login
   - POST /api/v1/auth/refresh (body { refreshToken })
   - GET /api/v1/auth/me (lấy từ SecurityContext)
   - POST /api/v1/auth/logout (stateless — chỉ cần trả success, FE tự xoá token; nếu muốn revoke thật thì lưu refreshToken hiện hành vào field user.currentRefreshToken để so khớp)

Tất cả response bọc trong ApiResponse.
```

DoD:
- [ ] Postman: `POST /auth/register` với email mới → 200, trả user + không trả password.
- [ ] `POST /auth/register` với email trùng → lỗi đúng format, HTTP 400/409.
- [ ] `POST /auth/login` sai password → 401 với message rõ ràng.
- [ ] `POST /auth/login` đúng → có `accessToken`; gọi `GET /auth/me` kèm token → trả đúng user.
- [ ] Gọi `GET /auth/me` KHÔNG kèm token → 401.
- [ ] `POST /auth/refresh` với refreshToken hợp lệ → accessToken mới.

**Task 2.3 — RBAC theo role trên toàn bộ Security Config (đặt placeholder, hoàn thiện dần)**

```
PROMPT:
Cập nhật SecurityConfig để chuẩn bị RBAC cho các module sắp code (dùng @PreAuthorize theo method thay vì khai báo hết trong SecurityFilterChain, để mỗi module tự khai báo quyền tại Controller của mình). Bật method security: @EnableMethodSecurity(prePostEnabled = true).

Ràng buộc chung cần nhớ (sẽ áp dụng dần ở các Controller sau):
CUSTOMER: cart, checkout, review, xem đơn của mình.
SALES: xem/đổi trạng thái order.
WAREHOUSE: quản lý inventory.
CONTENT: quản lý category/material/product.
ADMIN: toàn quyền + dashboard.

Không cần áp dụng cho module chưa tồn tại, chỉ setup cơ chế.
```

DoD:
- [ ] `@PreAuthorize` hoạt động (test bằng 1 endpoint tạm gắn `@PreAuthorize("hasRole('ADMIN')")`, gọi bằng token CUSTOMER → 403).

---

### PHASE 3 — Catalog: Category, Material, Product (M02/M03)

**Task 3.1 — Category & Material module**

```
PROMPT:
Tạo package com.woodfurni.catalog.category và com.woodfurni.catalog.material.

Category (collection "categories") theo schema:
[dán schema categories Mục 3.3]
- Hỗ trợ cây phân cấp qua parentId (self-reference bằng String id, không embed).
- CategoryController:
  GET /api/v1/categories (public, trả dạng cây — build tree ở service layer từ danh sách phẳng)
  POST /api/v1/categories (role CONTENT, ADMIN)
  PUT /api/v1/categories/{id} (role CONTENT, ADMIN)
  DELETE /api/v1/categories/{id} (role ADMIN)

Material (collection "materials") theo schema:
[dán schema materials Mục 3.3]
- MaterialController: GET public, POST/PUT/DELETE role CONTENT/ADMIN — tương tự Category nhưng không cần dạng cây.

Viết DTO request/response riêng, KHÔNG trả thẳng Entity ra ngoài API.
```

DoD:
- [ ] `POST /categories` bằng token CUSTOMER → 403.
- [ ] Tạo được cây 2 cấp: "Indoor Furniture" → "Living Room" (parentId trỏ đúng).
- [ ] `GET /categories` trả đúng cấu trúc cây lồng nhau.
- [ ] Seed thử 5 material (Oak, Walnut, Pine, Acacia, Teak) qua API thành công.

**Task 3.2 — Product module (CRUD + DTO/Mapper/Validation)**

```
PROMPT:
Tạo package com.woodfurni.catalog.product với model Product (collection "products") đầy đủ theo schema:

[dán schema products Mục 3.3 + index list]

Yêu cầu:
1. ProductRepository extends MongoRepository, thêm findBySku, findBySlug.
2. ProductRequest DTO (khi tạo/sửa) có Bean Validation: sku @NotBlank, name @Size(min=3,max=200), price @Positive, environment @NotNull enum, categoryId @NotBlank.
3. ProductResponse DTO: đầy đủ field + tên category/material đã resolve (không chỉ trả id).
4. ProductMapper (MapStruct hoặc thủ công): Entity <-> DTO.
5. ProductService:
   - create(): validate categoryId/materialIds tồn tại, tự sinh slug từ name nếu chưa có, status mặc định DRAFT.
   - update(), changeStatus(id, status) — riêng để publish (status=ACTIVE) phải validate images.size() >= 1, nếu không đủ điều kiện thì trả lỗi rõ ràng.
   - findById(), delete() (soft delete: đổi status=DISCONTINUED thay vì xoá cứng).
6. ProductController:
   GET /api/v1/products/{id} (public)
   POST /api/v1/products (role CONTENT, ADMIN)
   PUT /api/v1/products/{id} (role CONTENT, ADMIN)
   PATCH /api/v1/products/{id}/status (role CONTENT, ADMIN)
   DELETE /api/v1/products/{id} (role ADMIN)

Chưa làm GET /products (list + filter + pagination) — để Task 3.3 xử lý riêng vì đây là phần phức tạp nhất.
```

DoD:
- [ ] Tạo product thiếu `images` rồi PATCH status=ACTIVE → bị từ chối đúng message.
- [ ] Tạo product với `categoryId` không tồn tại → lỗi rõ ràng, không phải NullPointerException.
- [ ] `sku` trùng → lỗi 400/409, không phải lỗi MongoDB thô (DuplicateKeyException phải được bắt và format lại qua GlobalExceptionHandler).
- [ ] Response không lộ field thừa (không có `_class` của Spring Data, v.v).

**Task 3.3 — Search, Filter, Pagination, Sorting (M04) — quan trọng nhất về MongoDB**

```
PROMPT:
Bổ sung vào ProductController + ProductService endpoint:

GET /api/v1/products?keyword=&category=&environment=&woodType=&minPrice=&maxPrice=&sort=&page=0&size=20

Yêu cầu kỹ thuật:
1. Dùng Spring Data MongoDB Criteria/Query API (org.springframework.data.mongodb.core.query) để build query động — CHỈ thêm điều kiện cho param nào có giá trị.
   - keyword: dùng text search trên name+description (yêu cầu đã có text index — nếu chưa có, hãy tạo bằng @TextIndexed trên field name và description, hoặc tạo qua database/indexes script và ghi chú lại).
   - category: filter theo categoryId (hoặc slug, resolve sang id trước).
   - environment: enum match.
   - woodType: filter theo materialIds chứa material có code tương ứng.
   - minPrice/maxPrice: range trên field price (ưu tiên salePrice nếu có, fallback price — nêu rõ trong docstring).
2. Chỉ trả product có status=ACTIVE cho public (nếu người gọi có role CONTENT/ADMIN thì cho xem cả DRAFT — dùng SecurityContext optional).
3. Pagination bằng Spring Data Pageable, sort default -createdAt, cho phép sort=price,asc / -price / ratingAverage.
4. Response dùng PageResponse<ProductResponse> đã tạo ở Task 1.1, bọc trong ApiResponse.
5. Viết thêm script MongoDB (database/indexes/products.indexes.js) tạo đầy đủ các index đã liệt kê ở Mục 3.3 phần products (bao gồm compound index environment+room+materialIds+price).

Sau khi code xong, chạy thử với ít nhất 10 sản phẩm mẫu (tôi sẽ cung cấp seed ở task sau) để đảm bảo query đúng.
```

DoD:
- [ ] `GET /products?environment=OUTDOOR&woodType=TEAK&minPrice=5000000&maxPrice=10000000` trả đúng tập con mong đợi (test bằng ít nhất 3 sản phẩm mẫu: 1 khớp, 2 không khớp theo từng điều kiện riêng biệt).
- [ ] `GET /products?keyword=bàn` trả sản phẩm có "bàn" trong tên (kể cả không dấu nếu bạn kỳ vọng — ghi rõ giới hạn nếu MongoDB text index tiếng Việt không tách dấu tốt, đây là điểm có thể ghi vào phần "Hạn chế" của báo cáo đồ án).
- [ ] `page=1&size=5` trả đúng `totalElements`, `totalPages`.
- [ ] Explain query (`db.products.find(...).explain()`) cho thấy index được dùng, không COLLSCAN toàn bộ.

---

### PHASE 4 — Inventory (M07)

**Task 4.1 — Inventory module**

```
PROMPT:
Tạo package com.woodfurni.inventory với model Inventory (collection "inventories") theo schema:
[dán schema inventories Mục 3.3]

Yêu cầu:
1. Khi Product được tạo (Task 3.2), hook tạo kèm 1 Inventory với quantityOnHand=0 — thêm event/callback đơn giản trong ProductService.create() gọi InventoryService.initForProduct(productId) (không cần dùng event bus phức tạp, gọi trực tiếp là đủ).
2. InventoryService:
   - getAvailable(productId): quantityOnHand - quantityReserved.
   - reserve(productId, qty): atomic update bằng MongoTemplate.findAndModify với điều kiện quantityOnHand - quantityReserved >= qty, nếu không đủ throw InsufficientStockException.
   - release(productId, qty): hoàn reserve (dùng khi huỷ đơn).
   - commit(productId, qty): khi đơn giao thành công — trừ thật quantityOnHand và quantityReserved cùng lúc.
   - adjust(productId, delta, reason): nhập/xuất kho thủ công, không cho âm.
3. InventoryController:
   GET /api/v1/inventory (role WAREHOUSE, ADMIN) — trả kèm tên/SKU sản phẩm, phân trang.
   GET /api/v1/inventory/low-stock (role WAREHOUSE, ADMIN) — quantityOnHand <= lowStockThreshold.
   PATCH /api/v1/inventory/{productId}/adjust (role WAREHOUSE, ADMIN) — body { delta, reason }.

Không tích hợp với Order module ở task này (Order chưa tồn tại) — chỉ để sẵn các method reserve/release/commit cho task Order dùng sau.
```

DoD:
- [ ] Tạo 1 product mới → tự động có Inventory quantityOnHand=0.
- [ ] `PATCH /inventory/{id}/adjust { delta: 10 }` → quantityOnHand=10.
- [ ] `PATCH ... { delta: -20 }` khi chỉ có 10 → bị từ chối, không cho âm.
- [ ] Gọi thử `reserve(productId, 100)` khi chỉ có 10 available → throw đúng exception, không sửa dữ liệu.
- [ ] `GET /inventory/low-stock` với threshold=5, quantityOnHand=3 → sản phẩm xuất hiện trong danh sách.

---

### PHASE 5 — Cart (M05)

**Task 5.1 — Cart module**

```
PROMPT:
Tạo package com.woodfurni.cart với model Cart (collection "carts") theo schema:
[dán schema carts Mục 3.3]

CartService:
- getOrCreateCart(userId): nếu chưa có thì tạo mới rỗng.
- addItem(userId, productId, quantity): validate product tồn tại và status=ACTIVE, validate InventoryService.getAvailable(productId) >= quantity (không reserve ở bước này, chỉ cảnh báo/chặn nếu vượt tồn kho), nếu productId đã có trong cart thì cộng dồn quantity, snapshot lại productName + unitPrice tại thời điểm thêm (nhưng sẽ refresh giá mới nhất mỗi lần GET cart — nêu rõ trong code comment: cart KHÔNG cần giữ lịch sử giá như Order).
- updateItemQuantity(userId, productId, quantity): quantity=0 thì tự xoá item.
- removeItem(userId, productId).
- clearCart(userId).
- Luôn tính lại totalAmount sau mỗi thao tác.

CartController (toàn bộ role CUSTOMER):
GET /api/v1/cart
POST /api/v1/cart/items { productId, quantity }
PUT /api/v1/cart/items/{productId} { quantity }
DELETE /api/v1/cart/items/{productId}
DELETE /api/v1/cart
```

DoD:
- [ ] Thêm sản phẩm chưa từng có trong cart → items.length=1, totalAmount đúng.
- [ ] Thêm lại cùng sản phẩm → cộng dồn quantity, không tạo item trùng.
- [ ] Thêm số lượng vượt tồn kho hiện có → bị từ chối rõ ràng.
- [ ] `PUT .../{productId} { quantity: 0 }` → item bị xoá khỏi cart.
- [ ] Gọi API bằng token role khác CUSTOMER → 403.

---

### PHASE 6 — Promotion (M08)

**Task 6.1 — Promotion module**

```
PROMPT:
Tạo package com.woodfurni.promotion với model Promotion (collection "promotions") theo schema:
[dán schema promotions Mục 3.3]

PromotionService:
- validateAndCalculate(code, orderAmount): kiểm tra tồn tại, status=ACTIVE, trong khoảng startDate-endDate, orderAmount >= minOrderAmount, usedCount < usageLimit (nếu có giới hạn). Tính discount: PERCENTAGE thì value% của orderAmount (cap ở maxDiscountAmount nếu có), FIXED_AMOUNT thì trừ thẳng value (không vượt quá orderAmount). Trả về discountAmount, không tự tăng usedCount ở bước validate (chỉ tăng khi order thực sự được tạo — sẽ gọi ở module Order).
- incrementUsage(code): dùng khi Order tạo thành công.

PromotionController:
GET/POST/PUT/DELETE /api/v1/promotions(/{id}) — role ADMIN.
POST /api/v1/promotions/validate { code, cartTotal } — role CUSTOMER, trả { valid, discountAmount, message }.
```

DoD:
- [ ] Voucher hết hạn → `validate` trả `valid:false` kèm message rõ ràng, không throw lỗi 500.
- [ ] Voucher `minOrderAmount=1000000`, cartTotal=500000 → từ chối đúng lý do.
- [ ] Voucher PERCENTAGE=10%, maxDiscountAmount=500000, cartTotal=10000000 → discount bị cap ở 500000 chứ không phải 1000000.

---

### PHASE 7 — Order + Payment (M06)

**Task 7.1 — Order model + Checkout flow (task quan trọng nhất hệ thống)**

```
PROMPT:
Tạo package com.woodfurni.order với model Order (collection "orders") theo schema:
[dán schema orders Mục 3.3]
Và model Payment (collection "payments") theo schema:
[dán schema payments Mục 3.3]

Yêu cầu — implement đúng sequence checkout đã đặc tả:
[dán nguyên đoạn "4.2. Sequence checkout" ở Mục 4 tài liệu spec]

Chi tiết kỹ thuật bắt buộc:
1. OrderService.checkout(userId, CheckoutRequest{addressId, promotionCode, paymentMethod}):
   a. Lấy Cart hiện tại của user, nếu rỗng → lỗi rõ ràng.
   b. Với TỪNG item trong cart: gọi InventoryService.reserve(productId, qty) — nếu bất kỳ item nào thất bại, ROLLBACK các reserve đã thành công trước đó của cùng request này (dùng @Transactional với MongoDB transaction nếu bạn đã cấu hình replica set/transaction manager; nếu môi trường dev là standalone Mongo không hỗ trợ transaction, hãy tự viết compensating logic để release lại các item đã reserve khi có lỗi giữa chừng — PHẢI xử lý case này, đừng bỏ qua).
   c. Nếu có promotionCode: PromotionService.validateAndCalculate, nếu invalid thì rollback reserve và trả lỗi.
   d. Sinh orderNumber dạng ORD-yyyyMMdd-xxxx (đếm số đơn trong ngày, hoặc dùng sequence riêng — nêu rõ cách bạn chọn).
   e. Tạo Order với items = snapshot từ cart (copy productName, unitPrice tại thời điểm này — KHÔNG tham chiếu lại Product sau này), status=PENDING, statusHistory=[{PENDING, now}].
   f. Tạo Payment record tương ứng, method theo request.
      - Nếu method=COD: Payment status=PENDING, Order.paymentStatus=UNPAID, Order.status chuyển CONFIRMED ngay (không cần đợi thanh toán).
      - Nếu method=SANDBOX_CARD/SANDBOX_WALLET: giả lập luôn thành công sau khi gọi (đây là sandbox, không tích hợp cổng thật) → Payment status=SUCCESS, Order.paymentStatus=PAID, Order.status=CONFIRMED.
   g. Nếu promotionCode hợp lệ: PromotionService.incrementUsage(code).
   h. Clear cart.
   i. Trả về OrderResponse đầy đủ.
2. OrderService.updateStatus(orderId, newStatus, actorUserId): validate theo state machine:
[dán nguyên đoạn "4.3. Order status state machine" ở Mục 4 tài liệu spec]
   - Khi chuyển CANCELLED: gọi InventoryService.release() hoàn lại reserve.
   - Khi chuyển DELIVERED: gọi InventoryService.commit() trừ thật kho.
   - Chuyển sai thứ tự (vd PENDING -> DELIVERED thẳng) → lỗi rõ ràng.
   - Push vào statusHistory mỗi lần đổi.
3. OrderController:
   POST /api/v1/orders/checkout — role CUSTOMER
   GET /api/v1/orders — CUSTOMER thấy đơn của mình, SALES/ADMIN thấy tất cả + filter status/customerId (phân biệt qua role trong service, không phải 2 endpoint riêng)
   GET /api/v1/orders/{id} — chủ đơn hoặc SALES/ADMIN
   PATCH /api/v1/orders/{id}/status — role SALES, WAREHOUSE, ADMIN
   POST /api/v1/orders/{id}/cancel — chủ đơn (chỉ khi PENDING/CONFIRMED) hoặc ADMIN

Viết ít nhất 3 Unit Test cho OrderService.checkout() với Mockito, cover: (1) checkout thành công, (2) hết hàng giữa chừng → rollback reserve các item trước đó, (3) voucher hết hạn → rollback toàn bộ.
```

DoD — đối chiếu đúng bảng test case gốc trong docx:
- [ ] TC-ORDER-01: Given stock=10, When mua 2 → Then stock reserved đúng, available=8.
- [ ] TC-ORDER-02: Given stock=1, When mua 3 → Then order bị từ chối, KHÔNG có Order nào được tạo, KHÔNG có reserve nào bị treo.
- [ ] Checkout với giỏ có 2 sản phẩm, sản phẩm thứ 2 hết hàng → sản phẩm thứ 1 phải được release lại (kiểm tra bằng cách gọi lại `GET /inventory` xem quantityReserved có bị "kẹt" không).
- [ ] Đổi status PENDING → SHIPPING (bỏ qua CONFIRMED/PROCESSING) → bị từ chối.
- [ ] Đổi status → CANCELLED từ CONFIRMED → inventory được hoàn lại đúng số lượng.
- [ ] Đổi status → DELIVERED → `quantityOnHand` giảm thật, `quantityReserved` giảm tương ứng.
- [ ] `orderNumber` không trùng khi tạo nhiều đơn liên tiếp trong cùng ngày (test tạo 5 đơn liền, check unique).
- [ ] 3 unit test đã viết chạy pass qua `mvn test`.

---

### PHASE 8 — Review (M09)

**Task 8.1 — Review module**

```
PROMPT:
Tạo package com.woodfurni.review với model Review (collection "reviews") theo schema:
[dán schema reviews Mục 3.3]

ReviewService:
- create(userId, productId, orderId, rating, comment): validate Order thuộc về userId, Order.status=DELIVERED, Order.items chứa productId, và chưa từng review (unique userId+productId+orderId) — nếu vi phạm bất kỳ điều kiện nào, trả lỗi rõ ràng tương ứng (không gộp chung 1 message mơ hồ).
- Sau khi tạo review thành công: cập nhật lại Product.ratingAverage và Product.ratingCount (denormalized field) — tính trung bình từ toàn bộ review VISIBLE của sản phẩm đó (đơn giản nhất: query lại và tính average, không cần optimize incremental ở giai đoạn này).
- listByProduct(productId, page, size): chỉ trả review status=VISIBLE.

ReviewController:
GET /api/v1/products/{productId}/reviews — public
POST /api/v1/products/{productId}/reviews — role CUSTOMER, body { orderId, rating, comment }
PATCH /api/v1/reviews/{id}/status — role ADMIN, body { status }
```

DoD:
- [ ] Review khi đơn hàng chưa DELIVERED → bị từ chối.
- [ ] Review 2 lần cùng 1 sản phẩm/1 đơn → lần 2 bị từ chối.
- [ ] Sau khi tạo review rating=5, Product.ratingAverage cập nhật đúng.
- [ ] Admin set status=HIDDEN → review biến mất khỏi `GET /products/{id}/reviews` nhưng vẫn còn trong DB.

---

### PHASE 9 — Administration/Reporting (M10)

**Task 9.1 — Dashboard & Reporting**

```
PROMPT:
Tạo package com.woodfurni.reporting. Dùng MongoDB Aggregation Framework (MongoTemplate.aggregate) cho toàn bộ, KHÔNG load hết dữ liệu về Java rồi tính tay.

ReportingController (role ADMIN cho tất cả):
1. GET /api/v1/admin/dashboard/summary → { revenueToday, ordersToday, newCustomersToday, lowStockCount }.
   - revenueToday: tổng totalAmount các Order có createdAt trong ngày hôm nay và paymentStatus=PAID.
   - ordersToday: đếm Order tạo hôm nay.
   - newCustomersToday: đếm User role=CUSTOMER tạo hôm nay.
   - lowStockCount: đếm Inventory có quantityOnHand <= lowStockThreshold.
2. GET /api/v1/admin/dashboard/revenue?range=month → aggregate group theo tháng (12 tháng gần nhất), trả [{ month, revenue }].
3. GET /api/v1/admin/dashboard/orders-by-status → group theo status, trả [{ status, count }].
4. GET /api/v1/admin/dashboard/top-products?limit=10 → $unwind Order.items, group theo productId, sum quantity, sort desc, lookup lại tên sản phẩm.
5. GET /api/v1/admin/dashboard/category-breakdown → doanh thu theo Indoor/Outdoor và theo category, dùng lookup sang products/categories.

Viết mỗi aggregation pipeline thành 1 method riêng trong ReportingService, có comment giải thích từng $stage.
```

DoD:
- [ ] Tạo ít nhất 5 order mẫu (script seed) với ngày tạo khác nhau, kiểm tra `revenue?range=month` group đúng theo tháng.
- [ ] `top-products` trả đúng thứ tự giảm dần theo tổng quantity đã bán.
- [ ] `summary` chạy nhanh (<300ms với vài trăm order test) — nếu chậm, kiểm tra lại index trên `orders.createdAt`.

---

### PHASE 10 — Node.js Gateway & Realtime

**Task 10.1 — Gateway cơ bản + WebSocket**

```
PROMPT:
Trong gateway/node-gateway, khởi tạo project Node.js (Express + Socket.IO). Yêu cầu:

1. Middleware xác thực: đọc JWT giống Spring Boot (cùng secret key, đọc từ env JWT_SECRET) để verify khi client kết nối WebSocket — namespace "/realtime", lưu userId + role vào socket sau khi verify, reject nếu token sai/thiếu.
2. Expose các hàm publish nội bộ (sẽ được Spring Boot gọi qua 1 endpoint nội bộ hoặc qua message queue đơn giản — ở mức đồ án, dùng cách đơn giản nhất: Spring Boot gọi HTTP POST vào gateway):
   POST /internal/notify/order-created { orderId, orderNumber, totalAmount } → gateway emit tới room "admins" (mọi socket có role SALES/ADMIN đang connect).
   POST /internal/notify/order-status { orderId, orderNumber, status, customerId } → emit tới đúng socket của customerId đó.
   POST /internal/notify/low-stock { productId, productName, quantityOnHand, threshold } → emit tới room "warehouse".
   (Bảo vệ các route /internal/* bằng 1 shared secret header, không public.)
3. Khi client connect thành công, tự động join room theo role: SALES/ADMIN → room "admins", WAREHOUSE/ADMIN → room "warehouse".
4. package.json script "dev" dùng nodemon.

Đồng thời, thêm 1 NotificationClient nhỏ ở phía Spring Boot (com.woodfurni.notification hoặc trong từng module liên quan) gọi 3 endpoint /internal/notify/* ở đúng các điểm nghiệp vụ: sau khi Order checkout thành công → gọi order-created; sau khi updateStatus thành công → gọi order-status; sau khi Inventory phát hiện xuống dưới threshold (kiểm tra trong InventoryService.commit/adjust) → gọi low-stock.
```

DoD:
- [ ] Kết nối WebSocket bằng token role ADMIN → join đúng room "admins".
- [ ] Kết nối bằng token sai/hết hạn → bị reject.
- [ ] Checkout 1 đơn ở Postman → client admin đang connect nhận được event `order.created` real-time (test bằng 1 trang HTML/socket.io-client đơn giản hoặc Postman WebSocket).
- [ ] Đổi status đơn → đúng customer đó (không phải người khác) nhận được `order.status.updated`.
- [ ] Chỉnh Inventory xuống dưới threshold → room "warehouse" nhận `inventory.low_stock`.

---

### PHASE 11 — React Customer Website

**Task 11.1 — Scaffold + Auth pages**

```
PROMPT:
Trong frontend/customer-app, khởi tạo React (Vite), cấu trúc theo đúng cây thư mục ở Mục 2 (assets, components, pages, layouts, hooks, contexts, services, utils, routes, features).

Yêu cầu:
1. services/apiClient.js: axios instance, baseURL từ VITE_API_BASE_URL, tự đính kèm Bearer token từ localStorage vào header, tự xử lý 401 (redirect về /login).
2. contexts/AuthContext.jsx: state user, login(), logout(), register(), đọc/ghi token vào localStorage, expose useAuth() hook.
3. features/auth/: LoginPage, RegisterPage — form + validate cơ bản, gọi đúng API contract Mục 4 (POST /auth/login, /auth/register).
4. routes/: React Router — route /login, /register public; wrapper <ProtectedRoute> cho các route cần đăng nhập (sẽ dùng ở các task sau).
5. layouts/MainLayout.jsx: Header (logo, menu Indoor/Outdoor, giỏ hàng icon, user menu), Footer cơ bản.

KHÔNG code trang Home/Product ở task này.
```

DoD:
- [ ] Đăng ký tài khoản mới qua UI → chuyển hướng đúng, gọi đúng API.
- [ ] Đăng nhập sai mật khẩu → hiển thị lỗi từ response backend (không phải lỗi generic "Something went wrong").
- [ ] Đăng nhập đúng → token lưu localStorage, F5 lại trang vẫn giữ trạng thái đăng nhập.
- [ ] Gọi 1 API cần auth mà không đăng nhập → tự động về `/login`.

**Task 11.2 — Product listing, detail, search & filter**

```
PROMPT:
Trong frontend/customer-app, xây dựng:

1. features/catalog/: ProductListPage (grid sản phẩm, phân trang), ProductDetailPage (gallery ảnh, thông số: woodType, dimensions, environment, room, price/salePrice, stock status, mô tả, warranty, nút Add to Cart/Buy Now, danh sách review).
2. Bộ Filter sidebar: environment (Indoor/Outdoor), room, woodType, khoảng giá (min/max), ô tìm kiếm keyword — map đúng query param ở API contract Mục 4.1. Filter phải cập nhật URL query string (để share link/back button hoạt động đúng).
3. hooks/useProducts.js: gọi GET /products với params hiện tại, xử lý loading/error state.
4. Trang Home: hiển thị categories nổi bật (Indoor/Outdoor → Room), banner, vài sản phẩm nổi bật (gọi products sort theo ratingAverage hoặc mới nhất).
5. Responsive: layout đúng trên mobile (grid 2 cột) và desktop (grid 4 cột, sidebar filter cố định).

Không code Cart/Checkout ở task này.
```

DoD:
- [ ] Filter environment=OUTDOOR + woodType=Teak + khoảng giá → danh sách cập nhật đúng, khớp với backend đã test ở Task 3.3.
- [ ] Đổi filter → URL thay đổi tương ứng; copy URL mở tab mới → giữ đúng filter đã áp dụng.
- [ ] Trang chi tiết sản phẩm hiển thị đủ toàn bộ field đặc thù ngành gỗ (woodType, dimensions, environment...).
- [ ] Resize xuống mobile width → layout không vỡ, sidebar filter chuyển thành drawer/collapse hợp lý.

**Task 11.3 — Cart, Checkout, Order tracking, Review**

```
PROMPT:
Hoàn thiện luồng mua hàng cho frontend/customer-app:

1. contexts/CartContext.jsx: state cart (đồng bộ với GET /cart mỗi lần load), addItem/updateItem/removeItem gọi đúng API Mục 4 module Cart, cập nhật badge số lượng trên Header.
2. features/cart/CartPage.jsx: danh sách item, chỉnh quantity, xoá item, ô nhập mã voucher (gọi POST /promotions/validate để preview giảm giá trước khi qua checkout), nút "Tiến hành Checkout".
3. features/checkout/CheckoutPage.jsx: chọn địa chỉ giao hàng (từ user.addresses, hoặc thêm địa chỉ mới), chọn phương thức thanh toán (COD/SANDBOX_CARD/SANDBOX_WALLET), tóm tắt đơn hàng, nút xác nhận gọi POST /orders/checkout.
4. features/order/: OrderListPage (danh sách đơn của tôi, filter theo status), OrderDetailPage (chi tiết đơn, timeline trạng thái từ statusHistory, nút Huỷ đơn nếu còn PENDING/CONFIRMED, form đánh giá sản phẩm nếu status=DELIVERED và chưa review).
5. Kết nối WebSocket (socket.io-client) tới gateway ở OrderDetailPage/OrderListPage: khi nhận event order.status.updated cho đúng đơn đang xem, tự cập nhật UI + hiện toast thông báo, KHÔNG cần reload trang.

Toàn bộ action quan trọng (checkout, huỷ đơn, submit review) cần có confirm dialog + loading state + xử lý lỗi hiển thị message rõ ràng từ backend.
```

DoD:
- [ ] Luồng đầy đủ: đăng ký → đăng nhập → tìm "Outdoor" + "Teak" + khoảng giá → xem chi tiết → thêm giỏ → áp voucher → checkout → thấy đơn hàng mới trong danh sách — chạy mượt, không lỗi console.
- [ ] Huỷ đơn khi còn PENDING → thành công, đơn chuyển CANCELLED.
- [ ] Không huỷ được đơn khi đã SHIPPING (nút bị disable hoặc backend trả lỗi rõ ràng hiển thị đúng).
- [ ] Mở 2 tab: 1 tab customer xem OrderDetailPage, 1 tab dùng token ADMIN gọi PATCH đổi status qua Postman → tab customer tự cập nhật realtime không cần F5.
- [ ] Review chỉ hiện được khi đơn DELIVERED, submit xong không cho review lại lần 2.

---

### PHASE 12 — React Admin Portal

**Task 12.1 — Admin scaffold + Dashboard**

```
PROMPT:
Trong frontend/admin-app, khởi tạo React (Vite) riêng biệt (không dùng chung code với customer-app, có thể copy lại services/apiClient.js + AuthContext nhưng route login riêng, chỉ cho phép role khác CUSTOMER đăng nhập — nếu login bằng tài khoản CUSTOMER thì từ chối ở phía FE và hiển thị thông báo).

Yêu cầu:
1. Layout AdminLayout: sidebar menu (Dashboard, Products, Categories, Inventory, Orders, Customers, Promotions, Reviews, Reports), ẩn/hiện menu item theo role đăng nhập (SALES không thấy menu Products, WAREHOUSE không thấy Orders, v.v — nhưng nhắc lại: đây chỉ là UX, quyền thật đã enforce ở backend).
2. features/dashboard/DashboardPage.jsx: 4 số liệu summary (Revenue Today, Orders Today, New Customers, Low-stock Products) từ GET /admin/dashboard/summary, và các chart: Revenue by Month (line/bar chart), Orders by Status (pie chart), Top 10 Products (bar chart) — dùng recharts.
3. Kết nối WebSocket namespace admins: khi nhận order.created → hiện toast "Có đơn hàng mới #ORD-xxx" + tự refresh số Orders Today.

Không code Products/Orders management page ở task này.
```

DoD:
- [ ] Login bằng token CUSTOMER → bị từ chối vào admin-app.
- [ ] Login bằng SALES → không thấy menu "Products" trên sidebar.
- [ ] Dashboard hiển thị đúng số liệu khớp với dữ liệu seed đã kiểm tra ở Task 9.1.
- [ ] Tạo 1 order mới (qua customer-app hoặc Postman) → admin-app đang mở tự hiện toast realtime.

**Task 12.2 — Product/Category/Inventory management**

```
PROMPT:
features/catalog/ (admin-app): 
- ProductListPage: bảng sản phẩm (phân trang, filter theo status/category), nút Thêm/Sửa/Publish/Unpublish/Xoá.
- ProductFormPage: form đầy đủ toàn bộ field theo schema products (bao gồm upload nhiều ảnh — nếu chưa có backend upload thật, cho nhập URL ảnh trực tiếp và ghi rõ TODO tích hợp upload service sau), validate theo đúng rule Mục 3.3 (price>0, name 3-200 ký tự...).
- CategoryPage: quản lý cây danh mục (thêm/sửa/xoá, kéo thả đổi thứ tự là optional, không bắt buộc).
- InventoryPage: bảng tồn kho (product, quantityOnHand, quantityReserved, available, threshold), nút "Điều chỉnh" mở modal nhập delta + lý do, tab riêng "Low Stock" lọc sẵn.

Role tương ứng: CONTENT/ADMIN cho Product/Category, WAREHOUSE/ADMIN cho Inventory — ẩn nút thao tác nếu không đủ quyền.
```

DoD:
- [ ] Tạo sản phẩm mới đầy đủ field qua form → xuất hiện đúng trên customer-app sau khi Publish.
- [ ] Sửa giá sản phẩm đã có trong đơn hàng cũ → kiểm tra lại OrderDetailPage của đơn cũ, giá snapshot KHÔNG đổi (đúng thiết kế Order.items snapshot).
- [ ] Điều chỉnh Inventory delta âm vượt quá tồn → bị từ chối, hiển thị lỗi từ backend.
- [ ] Tab Low Stock chỉ hiện đúng sản phẩm dưới ngưỡng.

**Task 12.3 — Order, Customer, Promotion, Review management**

```
PROMPT:
features/order/ (admin-app): OrderListPage (bảng đơn, filter status/khách hàng/khoảng ngày), OrderDetailPage (chi tiết + nút đổi trạng thái theo đúng state machine — chỉ hiện các trạng thái hợp lệ tiếp theo dựa trên trạng thái hiện tại, KHÔNG cho chọn tự do).

features/customer/ (admin-app): CustomerListPage (danh sách User role=CUSTOMER, xem lịch sử đơn hàng của từng khách khi click vào).

features/promotion/ (admin-app): CRUD voucher, hiển thị usedCount/usageLimit dạng progress bar.

features/review/ (admin-app): danh sách review toàn hệ thống, filter theo rating/status, nút Ẩn/Hiện.

Role tương ứng: Order → SALES/WAREHOUSE/ADMIN tuỳ hành động cụ thể, Customer/Promotion/Review → ADMIN.
```

DoD:
- [ ] Dropdown đổi trạng thái đơn chỉ hiện các bước hợp lệ tiếp theo (vd đang PENDING chỉ hiện CONFIRMED/CANCELLED, không hiện DELIVERED).
- [ ] Login bằng SALES → đổi trạng thái đơn được, nhưng vào trang Promotion bị chặn (redirect hoặc 403 page).
- [ ] Ẩn 1 review → biến mất trên customer-app ngay (F5 lại trang sản phẩm đó).

---

### PHASE 13 — Testing (M-Testing)

**Task 13.1 — Backend test suite**

```
PROMPT:
Rà soát toàn bộ backend/furniture-api, bổ sung test còn thiếu (nếu Task 7.1 đã có 3 test Order thì giữ nguyên, bổ sung thêm):

1. Unit test (JUnit + Mockito) cho Service layer của TỐI THIỂU: AuthService (register trùng email, login sai password), ProductService (publish thiếu ảnh), InventoryService (reserve vượt tồn kho, release đúng số lượng), PromotionService (voucher hết hạn, cap discount).
2. Integration test (Spring Boot Test + Testcontainers cho MongoDB, hoặc embedded Mongo nếu Testcontainers không khả dụng trong môi trường của tôi — hãy hỏi tôi trước khi chọn, đừng tự quyết) cho luồng checkout đầy đủ: tạo user → tạo product+inventory → thêm cart → checkout → kiểm tra order + inventory sau checkout.
3. Test RBAC: với mỗi role, gọi thử ít nhất 1 endpoint KHÔNG thuộc quyền của role đó, assert HTTP 403.

Ghi lại danh sách test case đã viết vào docs/testing/backend-test-plan.md theo format Given/When/Then giống 2 test case mẫu trong tài liệu đồ án gốc (TC-ORDER-01, TC-ORDER-02).
```

DoD:
- [ ] `mvn test` pass toàn bộ, coverage report (jacoco, nếu thêm) cho thấy Service layer các module trọng yếu (Order, Inventory, Auth) được cover.
- [ ] `docs/testing/backend-test-plan.md` liệt kê đủ ≥15 test case dạng Given/When/Then.

**Task 13.2 — Frontend test**

```
PROMPT:
Trong frontend/customer-app và frontend/admin-app, dùng React Testing Library + Vitest, viết test cho:
1. AuthContext: login thành công lưu token, login thất bại không lưu token.
2. ProductFilter component: thay đổi filter → gọi đúng callback/API params.
3. CartContext: addItem cộng dồn đúng, removeItem xoá đúng item.
4. Admin OrderDetailPage: dropdown trạng thái chỉ hiện option hợp lệ theo trạng thái hiện tại (mock data).

Mock API bằng MSW (Mock Service Worker) thay vì mock trực tiếp axios, để test gần với thực tế hơn.
```

DoD:
- [ ] `npm test` pass ở cả 2 app.
- [ ] Test không phụ thuộc backend thật đang chạy (chạy được khi offline nhờ MSW).

---

### PHASE 14 — Docker & Deployment

**Task 14.1 — Docker Compose full stack**

```
PROMPT:
Tạo docker/docker-compose.yml với các service:
- mongo: image mongo:7, volume persist data, expose 27017, healthcheck.
- backend: build từ backend/furniture-api (multi-stage Dockerfile: build bằng Maven, run bằng JRE nhẹ), depends_on mongo healthy, env MONGODB_URI trỏ tới service mongo, JWT_SECRET từ .env.
- gateway: build từ gateway/node-gateway, env JWT_SECRET giống backend, INTERNAL_SECRET.
- customer-app, admin-app: build production (npm run build) rồi serve bằng nginx, biến VITE_API_BASE_URL trỏ qua gateway hoặc thẳng backend (quyết định rõ và ghi lý do trong README).
- mongo-express (tuỳ chọn): để dễ debug dữ liệu lúc bảo vệ đồ án.

Viết Dockerfile riêng cho từng service (backend, gateway, customer-app, admin-app). Tạo .env.example liệt kê đầy đủ biến môi trường cần thiết. Cập nhật README.md phần "How to run" bằng `docker compose up --build`.
```

DoD:
- [ ] `docker compose up --build` từ máy sạch (chưa từng chạy `mvn`/`npm` local) → toàn bộ 4-5 service lên healthy.
- [ ] Truy cập customer-app qua trình duyệt, thực hiện được luồng mua hàng đầy đủ end-to-end trong môi trường Docker (không phải localhost dev server).
- [ ] Restart container backend → dữ liệu MongoDB không mất (volume hoạt động đúng).

**Task 14.2 — Seed data + finalize docs**

```
PROMPT:
Trong database/sample-data/, viết script (Node.js hoặc mongosh .js) seed dữ liệu mẫu:
- ≥5 category (Indoor: Living Room, Bedroom, Dining Room, Office; Outdoor: Garden), ≥5 material (Oak, Walnut, Pine, Acacia, Teak).
- ≥15 sản phẩm (dùng đúng 10 sản phẩm mẫu trong bảng SKU của tài liệu gốc: IN001-IN005, OUT001-OUT005, cộng thêm ≥5 sản phẩm tự tạo cho đa dạng filter).
- ≥5 user (1 ADMIN, 1 SALES, 1 WAREHOUSE, 1 CONTENT, 2 CUSTOMER).
- ≥3 order mẫu ở các trạng thái khác nhau (1 DELIVERED để test review được, 1 đang SHIPPING, 1 PENDING).
- ≥2 promotion mẫu (1 PERCENTAGE, 1 FIXED_AMOUNT).

Cập nhật README.md hoàn chỉnh: giới thiệu, tech stack, cách chạy (dev + docker), cấu trúc thư mục, link Swagger, link Postman collection, tài khoản demo (email/password) cho từng role.

Export Postman collection đầy đủ toàn bộ endpoint ở Mục 4 vào postman/WOODFURNI.postman_collection.json, có environment variable {{baseUrl}}, {{accessToken}} tự set qua script sau khi gọi login.
```

DoD:
- [ ] Chạy script seed trên DB rỗng → dữ liệu đúng số lượng như yêu cầu, không lỗi trùng key.
- [ ] Đăng nhập bằng tài khoản demo mỗi role → đúng quyền tương ứng đã test ở các task trước.
- [ ] Import Postman collection → chạy thử login rồi gọi 1 API cần auth → tự động dùng đúng token vừa lấy (nhờ script).
- [ ] README đủ để một người chưa biết gì về project chạy được `docker compose up` và demo thành công luồng mua hàng.

---

## 7. GHI CHÚ VẬN HÀNH — TIẾT KIỆM REQUEST KHI DÙNG CURSOR

1. **1 prompt = 1 task ở trên, không gộp nhiều Phase vào 1 prompt.** Task càng nhỏ, Cursor càng ít bị lạc đề, bạn càng ít phải sửa tay → tiết kiệm request hơn là prompt to rồi phải fix nhiều vòng.
2. Khi Cursor sinh sai (vd quên field, sai role), **paste lại đúng đoạn schema/API contract liên quan** (copy thẳng từ Mục 3/4 tài liệu này) kèm câu "sửa lại cho đúng phần này", thay vì diễn giải lại bằng lời — vì Cursor bám sát dữ liệu có cấu trúc tốt hơn mô tả tự do.
3. Trước khi bắt đầu task mới, đảm bảo Cursor đang có trong context: file chứa model/service của task liền trước (dùng `@` để đính kèm file cụ thể thay vì để nó tự tìm — nhanh hơn và ít tốn token hơn).
4. Nếu dùng Cursor Agent mode cho cả 1 Phase gồm nhiều Task liên tiếp, vẫn nên dừng lại đối chiếu DoD sau mỗi Task thay vì để chạy hết cả Phase rồi mới kiểm tra — lỗi ở Task đầu (vd sai schema) sẽ kéo theo lỗi dây chuyền ở các Task sau, sửa muộn tốn request hơn nhiều.
5. Giữ Postman collection cập nhật dần theo từng Phase — dùng nó để tự kiểm tra DoD thay vì hỏi lại Cursor "endpoint này đã đúng chưa", vừa nhanh hơn vừa không tốn request AI.

---

*(Tài liệu này bám sát đề xuất gốc trong WOODFURNI_-_HUY.docx: 10 phân hệ M01–M10, 12 collection MongoDB, kiến trúc Modular Monolith React + Node Gateway + Spring Boot + MongoDB, và 3 tính năng nổi bật — Product Recommendation, Realtime Order Notification, Smart Inventory Warning. Recommendation rule-based (Task nâng cao, không bắt buộc trong 14 Phase trên) có thể bổ sung sau như 1 endpoint `GET /products/{id}/recommendations` filter theo category+woodType+environment+priceRange — thêm vào cuối Phase 3 nếu còn thời gian.)*
