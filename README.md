# WOODFURNI — E-Commerce Platform for Wooden Furniture

> **WOODFURNI** là hệ thống thương mại điện tử kinh doanh đồ gỗ nội thất trong nhà & ngoài trời, phát triển bởi *Mộc Việt Furniture* — đồ án tốt nghiệp (KLTN) chuyên ngành Công nghệ Thông tin.

---

## Table of Contents

1. [Tổng quan](#1-tổng-quan)
2. [Tech Stack](#2-tech-stack)
3. [Cấu trúc thư mục](#3-cấu-trúc-thư-mục)
4. [Cách chạy](#4-cách-chạy)
   - [4.1. Quick Start với Docker Compose](#41-quick-start-với-docker-compose-khuyến-nghị)
   - [4.2. Local Development (không Docker)](#42-local-development-không-docker)
5. [Dữ liệu mẫu & Tài khoản Demo](#5-dữ-liệu-mẫu--tài-khoản-demo)
6. [API Documentation](#6-api-documentation)
7. [Biến môi trường](#7-biến-môi-trường)
8. [RBAC — Phân quyền](#8-rbac--phân-quyền)
9. [10 Phân hệ nghiệp vụ](#9-10-phân-hệ-nghiệp-vụ)
10. [Development Phases](#10-development-phases)
11. [License & Contact](#11-license--contact)

---

## 1. Tổng quan

**WOODFURNI** giả lập một doanh nghiệp kinh doanh đồ gỗ nội thất với 3 ưu điểm nổi bật:

1. **Product Recommendation** — gợi ý sản phẩm theo `categoryId + woodType + environment + priceRange`
2. **Realtime Order Notification** — WebSocket thông báo trạng thái đơn hàng & đơn mới cho admin
3. **Smart Inventory Warning** — cảnh báo tồn kho thấp và reservation atomic tránh oversell

### Kiến trúc (Modular Monolith)

```
┌──────────────┐
│   ReactJS    │  Customer SPA + Admin Portal (2 app)
│  Frontend    │
└──────┬───────┘
       │ HTTPS / JSON
       ▼
┌──────────────▼─────────┐
│  Node.js Gateway        │  Express + Socket.IO
│  (Auth passthrough,     │  - JWT verify (REST + WS)
│   rate-limit, WS)       │  - Realtime rooms (admins, warehouse)
└──────┬─────────────────┘
       │
       ▼
┌──────────────┐
│  Spring Boot │  10 modules: auth · catalog · inventory · cart · order ·
│  (Modular    │  payment · promotion · review · reporting
│   Monolith)  │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   MongoDB    │  12 collections (xem §9)
└──────────────┘
```

---

## 2. Tech Stack

| Công nghệ | Vai trò |
|---|---|
| **ReactJS (Vite)** | Customer Website + Admin Portal — 2 SPA riêng |
| **Node.js + Express** | API Gateway, WebSocket (Socket.IO), Notification |
| **Spring Boot 3.x** | Toàn bộ business logic (Layered: Controller → Service → Repository) |
| **Spring Security + JWT** | Authentication / Authorization (RBAC) |
| **Spring Data MongoDB** | Data access layer |
| **MongoDB 7** | CSDL chính (document-based, 12 collections) |
| **Socket.IO** | Realtime notification (order status, low-stock, new order) |
| **Docker Compose** | Đóng gói & chạy toàn bộ stack |
| **Swagger / OpenAPI** | API documentation (auto-gen từ Spring) |
| **JUnit 5 + Mockito** | Backend unit/integration test |
| **Maven** | Backend build & dependency |
| **Vite** | Frontend build, dev server |

### Roles (RBAC)

| Role | Quyền chính |
|---|---|
| `CUSTOMER` | Browse, cart, checkout, review, theo dõi đơn của mình |
| `SALES` | Xem / xử lý / cập nhật trạng thái đơn hàng |
| `WAREHOUSE` | Quản lý tồn kho, điều chỉnh stock |
| `CONTENT` | Quản lý sản phẩm / danh mục / vật liệu |
| `ADMIN` | Toàn quyền + Admin Dashboard |

---

## 3. Cấu trúc thư mục

```
woodfurni/
├── frontend/
│   ├── customer-app/                # React — Customer Website (Vite)
│   └── admin-app/                   # React — Admin Portal (Vite)
│
├── gateway/
│   └── node-gateway/                # Node.js (Express + Socket.IO)
│
├── backend/
│   └── furniture-api/               # Spring Boot Modular Monolith
│       ├── src/main/java/com/woodfurni/
│       │   ├── config/              # MongoConfig, SecurityConfig, SwaggerConfig, CorsConfig
│       │   ├── common/              # ApiResponse, PageResponse, GlobalExceptionHandler
│       │   ├── auth/                # module M01
│       │   ├── catalog/             # product (M02) · category + material (M03)
│       │   ├── inventory/           # module M07
│       │   ├── cart/                # module M05
│       │   ├── order/               # module M06
│       │   ├── payment/             # sandbox payment
│       │   ├── promotion/           # module M08
│       │   ├── review/              # module M09
│       │   ├── reporting/           # module M10 (Admin Dashboard)
│       │   ├── security/            # JwtFilter, JwtProvider, UserDetailsServiceImpl
│       │   └── FurnitureApiApplication.java
│       ├── src/main/resources/application.yml
│       └── pom.xml
│
├── database/
│   ├── sample-data/                 # ★ Seed scripts (mongosh + Node.js)
│   │   ├── seed.js                  # mongosh — chạy với `mongosh < seed.js`
│   │   ├── seed.node.js             # Node.js alternative — chạy với `npm install && node seed.node.js`
│   │   ├── package.json
│   │   └── README.md
│   ├── indexes/                     # MongoDB index scripts (auto-create qua mongo-entrypoint.sh)
│   └── backup/
│
├── docker/
│   ├── docker-compose.yml           # Full stack: mongo + backend + gateway + 2 SPA
│   ├── compose.ps1                  # Windows wrapper (tắt BuildKit/Bake)
│   ├── *.Dockerfile                 # backend / gateway / customer-app / admin-app
│   ├── nginx-frontend.conf          # SPA fallback + gzip + cache
│   ├── mongo-entrypoint.sh          # Auto-apply indexes/*.js lần đầu
│   └── .env.example
│
├── docs/
│   ├── ai-specs/                    # AI Development Specs (Cursor prompts)
│   ├── requirements/  uml/  api/  testing/  thesis/
│
├── postman/
│   └── WOODFURNI.postman_collection.json   # ★ 62 endpoints, auto-set token sau login
│
├── README.md
└── .gitignore
```

> **Ghi chú thiết kế:** module trong Spring Boot tổ chức theo **feature package** (`auth/`, `catalog/`...),
> không theo layer toàn cục — dễ maintain hơn khi hệ thống có 10 phân hệ, và là bước đệm tự nhiên
> nếu sau này tách microservices.

---

## 4. Cách chạy

### 4.1. Quick Start với Docker Compose (khuyến nghị)

> Chỉ 1 câu lệnh, không cần cài Java / Maven / Node / MongoDB local. Stack chạy đầy đủ
> trong container với volume persist data.

```bash
# 1. Clone
git clone <repo-url>
cd woodfurni

# 2. Chuẩn bị env file (JWT_SECRET ≥ 32 ký tự)
cd docker
cp .env.example .env
# Generate JWT secret ngẫu nhiên:
#   node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"
#   → dán vào JWT_SECRET=... trong .env

# 3. Build & chạy toàn bộ stack
docker compose up --build
```

> **Windows + Docker Desktop** — nếu gặp lỗi `EOF`, `Can't add file ... to tar: io: read/write on closed pipe`,
> hoặc `Bake is configured but buildkit isn't enabled`, dùng script wrapper:
> ```powershell
> cd docker
> .\compose.ps1 build
> .\compose.ps1 up -d
> ```
> Script này tự tắt BuildKit + Bake cho session. Xem `docker/.env.example` mục TROUBLESHOOTING.

Sau khi container lên (3–5 phút cho lần build đầu):

| Service | URL | Ghi chú |
|---|---|---|
| **Customer SPA** | http://localhost:8082 | React app, browse + checkout |
| **Admin SPA** | http://localhost:8083 | Login với tài khoản staff |
| **Node Gateway** (REST + WS) | http://localhost:3000 | Single entry point từ browser |
| **Spring Boot Backend** | http://localhost:8080/api/v1 | Internal — browser không gọi trực tiếp |
| **Swagger UI** | http://localhost:8080/api/v1/swagger-ui.html | OpenAPI docs đầy đủ |
| **MongoDB** | localhost:27017 | Volume `woodfurni_mongo_data` persist |
| **Mongo Express** (debug) | http://localhost:8081 | Bật bằng profile: `docker compose --profile debug up` |

**Kiến trúc request** — cả 2 SPA và WebSocket đều đi qua gateway vì:
1. Gateway đã làm JWT verification chung cho cả REST + WebSocket → 1 chỗ cấu hình secret, 1 CORS whitelist
2. Browser chỉ cần trust 1 origin (gateway) thay vì 3
3. Tương lai dễ thêm rate limit / log aggregation tại gateway
4. Backend có thể whitelist IP gateway thay vì mở public

### 4.2. Local Development (không Docker)

```bash
# 1. MongoDB local
docker run -d -p 27017:27017 --name woodfurni-mongo mongo:7

# 2. Backend (Spring Boot)
cd backend/furniture-api
mvn spring-boot:run
# → http://localhost:8080/api/v1

# 3. Gateway (Node.js)
cd gateway/node-gateway
cp .env.example .env       # JWT_SECRET giống backend
npm install && npm run dev
# → http://localhost:3000

# 4. Customer app
cd frontend/customer-app
npm install
echo "VITE_API_BASE_URL=http://localhost:3000/api/v1" > .env.local
npm run dev
# → http://localhost:5173

# 5. Admin app
cd frontend/admin-app
npm install
echo "VITE_API_BASE_URL=http://localhost:3000/api/v1" > .env.local
npm run dev
# → http://localhost:5174
```

---

## 5. Dữ liệu mẫu & Tài khoản Demo

### 5.1. Chạy seed data

Script `database/sample-data/` chèn sẵn:
- 5 categories (Living Room, Bedroom, Dining Room, Office, Garden)
- 5 materials (Oak, Walnut, Pine, Acacia, Teak)
- 15 products (10 SKU mẫu `IN001-IN005` + `OUT001-OUT005`, 5 SKU tự tạo `EX001-EX005`)
- 6 users (1 ADMIN, 1 SALES, 1 WAREHOUSE, 1 CONTENT, 2 CUSTOMER)
- 3 orders (1 DELIVERED, 1 SHIPPING, 1 PENDING — đủ test review + state machine)
- 2 promotions (1 PERCENTAGE `WELCOME10`, 1 FIXED_AMOUNT `SUMMER2TR`)
- 2 reviews (cho đơn DELIVERED)
- 3 notifications (admin / warehouse / customer)
- Inventories + indexes tương ứng

**Cách 1 — mongosh:**

```bash
mongosh "mongodb://localhost:27017/woodfurni" database/sample-data/seed.js
```

**Cách 2 — docker compose stack đang chạy:**

```bash
docker compose exec -T mongo mongosh woodfurni < database/sample-data/seed.js
```

**Cách 3 — Node.js (không cần mongosh):**

```bash
cd database/sample-data
npm install
MONGODB_URI="mongodb://localhost:27017/woodfurni" node seed.node.js
```

> ⚠️ **Idempotent** — script xoá toàn bộ collection nghiệp vụ trước khi insert lại. **KHÔNG chạy trên production.**

### 5.2. Tài khoản demo (sau khi chạy seed)

| Role | Email | Password | Phạm vi test |
|---|---|---|---|
| `ADMIN` | `admin@woodfurni.vn` | `Admin@123` | Toàn quyền — Dashboard, CRUD tất cả |
| `SALES` | `sales@woodfurni.vn` | `Sales@123` | Xem / đổi trạng thái đơn hàng |
| `WAREHOUSE` | `warehouse@woodfurni.vn` | `Warehouse@123` | Inventory + low-stock |
| `CONTENT` | `content@woodfurni.vn` | `Content@123` | Product / Category / Material / Review moderation |
| `CUSTOMER` | `customer1@woodfurni.vn` | `Customer1@123` | Mua hàng, review (đã có 1 đơn DELIVERED) |
| `CUSTOMER` | `customer2@woodfurni.vn` | `Customer2@123` | Đơn SHIPPING để test timeline |

> Login bằng tài khoản CUSTOMER vào Admin Portal sẽ bị từ chối (FE enforce + Backend `@PreAuthorize`).

### 5.3. Voucher demo

| Code | Type | Value | Min order | Cap |
|---|---|---|---|---|
| `WELCOME10` | PERCENTAGE | 10% | 5.000.000 đ | 2.000.000 đ |
| `SUMMER2TR` | FIXED_AMOUNT | 2.000.000 đ | 20.000.000 đ | — |

---

## 6. API Documentation

### 6.1. Swagger UI (OpenAPI auto-generated)

| Stack | URL |
|---|---|
| Docker compose | http://localhost:8080/api/v1/swagger-ui.html |
| Local dev | http://localhost:8080/api/v1/swagger-ui.html |

### 6.2. Postman Collection

**File:** [`postman/WOODFURNI.postman_collection.json`](postman/WOODFURNI.postman_collection.json)

**Cấu trúc:** 11 folder / **62 endpoints** đầy đủ theo Mục 4 của `WOODFURNI_AI_DEV_SPEC_1.md`:

```
00 — Health Check (1)
01 — Auth (M01)         (9)
02 — Categories (M03)   (6)
03 — Materials (M03)    (6)
04 — Products (M02/M04) (8)
05 — Cart (M05)         (5)
06 — Orders (M06)       (7)
07 — Inventory (M07)    (4)
08 — Promotions (M08)   (6)
09 — Reviews (M09)      (5)
10 — Admin Dashboard (M10) (5)
```

**Cách dùng:**

1. **Import** file `postman/WOODFURNI.postman_collection.json` vào Postman.
2. Tạo **Environment** mới (vd `WOODFURNI Local`) với 2 biến:
   - `baseUrl` = `http://localhost:8080/api/v1` (gọi thẳng backend) **hoặc** `http://localhost:3000/api/v1` (qua gateway — khuyến nghị)
   - `accessToken` — để trống, sẽ tự set
3. Mở folder **01 — Auth**, chạy **Login — Customer** (hoặc role khác). Script Tests sẽ tự ghi `accessToken` + `refreshToken` vào environment.
4. Mọi request khác dùng `{{baseUrl}}` + `Authorization: Bearer {{accessToken}}` — Postman tự chèn qua collection-level auth.

**Sample flow test trên Postman:**

```
1. Login — Customer                  → set accessToken
2. List Categories                   → set categoryId
3. List Materials                    → set materialId
4. Search Products (filter Teak outdoor 5-10tr) → xem catalog
5. Add Item to Cart                  → cart có 1 item
6. Coupon Validate (WELCOME10)       → preview discount
7. Checkout                          → set orderId, cart cleared
8. Get Order by ID                   → xem chi tiết + statusHistory
9. (đăng nhập SALES) Update Order Status → CONFIRMED
10. (đăng nhập SALES) Update Order Status → PROCESSING
11. (đăng nhập WAREHOUSE) → SHIPPING
12. (đăng nhập WAREHOUSE) → DELIVERED
13. (quay lại CUSTOMER) Create Review → ghi nhận đánh giá
```

---

## 7. Biến môi trường

Tất cả biến được liệt kê đầy đủ trong `docker/.env.example`. Copy thành `docker/.env` rồi chỉnh.

### 7.1. Backend (Spring Boot — `application.yml`)

| Variable | Default | Mô tả |
|---|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017/woodfurni` | MongoDB connection string |
| `MONGODB_DATABASE` | `woodfurni` | Database name |
| `JWT_SECRET` | (dev placeholder) | JWT signing key — **phải ≥ 32 ký tự** |
| `JWT_ACCESS_EXPIRATION` | `3600000` (1h) | Access token TTL (ms) |
| `JWT_REFRESH_EXPIRATION` | `604800000` (7d) | Refresh token TTL (ms) |
| `GATEWAY_BASE_URL` | `http://localhost:3000` | URL gọi ngược về gateway (notify) |
| `GATEWAY_INTERNAL_SECRET` | `internal-woodfurni-...` | Shared secret cho `/internal/notify/*` |
| `SERVER_PORT` | `8080` | HTTP port |

### 7.2. Gateway (Node.js — `gateway/node-gateway/.env`)

| Variable | Default | Mô tả |
|---|---|---|
| `PORT` | `3000` | HTTP port |
| `JWT_SECRET` | (bắt buộc) | JWT verify key — **giống backend** |
| `INTERNAL_SECRET` | (bắt buộc) | Shared secret cho `/internal/notify/*` — giống backend |
| `BACKEND_BASE_URL` | `http://backend:8080` | URL backend (dùng tên service trong Docker network) |
| `CORS_ORIGINS` | `http://localhost:5173,...` | Comma-separated whitelist |

### 7.3. Frontend (`.env.local`)

| Variable | Default | Mô tả |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:3000/api/v1` | URL gọi REST — **phải trỏ vào gateway** |
| `VITE_SOCKET_URL` | `http://localhost:3000` | URL WebSocket — cùng host với gateway |

> ⚠️ Vite bake env vào bundle lúc build. Đổi URL = phải rebuild image/app.

---

## 8. RBAC — Phân quyền

Enforce ở **backend** qua `@PreAuthorize` method security (không chỉ ẩn UI).

| Module | CUSTOMER | SALES | WAREHOUSE | CONTENT | ADMIN |
|---|:-:|:-:|:-:|:-:|:-:|
| Auth (register / login / refresh) | ✓ | ✓ | ✓ | ✓ | ✓ |
| Product (GET) | ✓ | ✓ | ✓ | ✓ | ✓ |
| Product (POST/PUT/PATCH) | ✗ | ✗ | ✗ | ✓ | ✓ |
| Product (DELETE) | ✗ | ✗ | ✗ | ✗ | ✓ |
| Category / Material (GET) | ✓ | ✓ | ✓ | ✓ | ✓ |
| Category / Material (POST/PUT) | ✗ | ✗ | ✗ | ✓ | ✓ |
| Category (DELETE) | ✗ | ✗ | ✗ | ✗ | ✓ |
| Cart (full) | ✓ | ✗ | ✗ | ✗ | ✓ |
| Order (GET own) | ✓ | n/a | n/a | n/a | n/a |
| Order (GET all / filter) | ✗ | ✓ | ✗ | ✗ | ✓ |
| Order (PATCH status) | ✗ | ✓ | ✓ | ✗ | ✓ |
| Order (POST cancel) | ✓ (PENDING/CONFIRMED only) | ✗ | ✗ | ✗ | ✓ |
| Inventory (GET/PATCH) | ✗ | ✗ | ✓ | ✗ | ✓ |
| Promotion (CRUD) | ✗ | ✗ | ✗ | ✗ | ✓ |
| Promotion (validate) | ✓ | ✗ | ✗ | ✗ | ✓ |
| Review (GET) | ✓ | ✓ | ✓ | ✓ | ✓ |
| Review (POST) | ✓ | ✗ | ✗ | ✗ | ✓ |
| Review (PATCH status) | ✗ | ✗ | ✗ | ✓ | ✓ |
| Admin Dashboard | ✗ | ✗ | ✗ | ✗ | ✓ |

---

## 9. 10 Phân hệ nghiệp vụ

| Module | Mô tả |
|---|---|
| **M01 — Authentication** | Register, login, JWT, refresh token, logout |
| **M02 — Product Catalog** | CRUD products với đầy đủ field đặc thù ngành gỗ |
| **M03 — Category / Material** | Category cây phân cấp (Indoor → Room), Material (Oak, Walnut, ...) |
| **M04 — Search & Filter** | keyword + environment + room + woodType + price range, có compound index |
| **M05 — Cart** | Persistent cart, snapshot price, validate stock khi add |
| **M06 — Order** | Checkout flow với state machine: PENDING → CONFIRMED → PROCESSING → SHIPPING → DELIVERED |
| **M07 — Inventory** | Atomic reserve / release / commit, low-stock alert |
| **M08 — Promotion** | PERCENTAGE / FIXED_AMOUNT voucher, min order, cap, usage limit |
| **M09 — Review** | Yêu cầu order DELIVERED, unique user+product+order, denormalize rating |
| **M10 — Administration / Reporting** | Admin Dashboard với MongoDB aggregation pipeline |

### 12 MongoDB Collections

`users` · `roles` · `products` · `categories` · `materials` · `inventories` · `carts` · `orders` · `payments` · `promotions` · `reviews` · `notifications`

---

## 10. Development Phases

| Phase | Mô tả | Status |
|---|---|---|
| 0 | Monorepo Setup | ✅ |
| 1 | Spring Boot Foundation | ✅ |
| 2 | Authentication (M01) | ✅ |
| 3 | Catalog (M02–M04) | ✅ |
| 4 | Inventory (M07) | ✅ |
| 5 | Cart (M05) | ✅ |
| 6 | Promotion (M08) | ✅ |
| 7 | Order + Payment (M06) | ✅ |
| 8 | Review (M09) | ✅ |
| 9 | Administration / Reporting (M10) | ✅ |
| 10 | Node.js Gateway & Realtime | ✅ |
| 11 | React Customer Website | ✅ |
| 12 | React Admin Portal | ✅ |
| 13 | Testing | ✅ |
| 14 | Docker & Deployment + Seed Data | ✅ |

---

## 11. License & Contact

**WOODFURNI** — đồ án tốt nghiệp (KLTN) của *Mộc Việt Furniture*.

**3 tính năng nổi bật:**

- **Product Recommendation** — rule-based theo `categoryId + woodType + environment + priceRange`
- **Realtime Order Notification** — WebSocket (Socket.IO) báo trạng thái đơn + đơn mới
- **Smart Inventory Warning** — atomic reservation + low-stock threshold alert

**Repo:** `woodfurni/` (monorepo: React + Node Gateway + Spring Boot + MongoDB)

**Issues / đóng góp:** mở issue trên repo.

---

> 📘 Tài liệu kỹ thuật đầy đủ: `docs/ai-specs/WOODFURNI_AI_DEV_SPEC_1.md` —
> 14 phase triển khai với prompt Cursor + DoD (Definition of Done) cụ thể từng task.
