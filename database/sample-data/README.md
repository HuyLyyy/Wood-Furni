# WOODFURNI — Sample Data Seed

Dữ liệu mẫu cho toàn bộ 11 collection nghiệp vụ (đủ cho demo luồng mua hàng
end-to-end + bảo vệ đồ án). Có **2 cách chạy** — chọn 1:

| Cách | File | Yêu cầu |
|---|---|---|
| **mongosh** (khuyến nghị khi dev local) | `seed.js` | `mongosh` CLI + MongoDB 6/7 đang chạy |
| **Node.js** (không cần mongosh) | `seed.node.js` | Node 18+ + `npm install` |

---

## 1. Dữ liệu sinh ra

### 1.1. Categories (5)
| Slug | Environment | Order |
|---|---|---|
| `living-room` | INDOOR | 1 |
| `bedroom` | INDOOR | 2 |
| `dining-room` | INDOOR | 3 |
| `office` | INDOOR | 4 |
| `garden` | OUTDOOR | 5 |

### 1.2. Materials (5)
| Code | Name | Ghi chú |
|---|---|---|
| `OAK` | Oak | Gỗ Sồi |
| `WALNUT` | Walnut | Gỗ Óc Chó |
| `PINE` | Pine | Gỗ Thông |
| `ACACIA` | Acacia | Gỗ Tràm |
| `TEAK` | Teak | Gỗ Tếch — chịu nước tốt |

### 1.3. Products (15)
- 10 SKU mẫu theo spec gốc: `IN001-IN005` (Indoor) + `OUT001-OUT005` (Outdoor)
- 5 SKU tự tạo: `EX001-EX005` — đa dạng cho filter (mix Indoor/Outdoor, materials khác nhau)

Toàn bộ products `status=ACTIVE`, `ratingAverage`/`ratingCount` đã có sẵn cho Hiển thị.

### 1.4. Users (6) — tài khoản demo

| Role | Email | Password |
|---|---|---|
| ADMIN | `admin@woodfurni.vn` | `Admin@123` |
| SALES | `sales@woodfurni.vn` | `Sales@123` |
| WAREHOUSE | `warehouse@woodfurni.vn` | `Warehouse@123` |
| CONTENT | `content@woodfurni.vn` | `Content@123` |
| CUSTOMER | `customer1@woodfurni.vn` | `Customer1@123` |
| CUSTOMER | `customer2@woodfurni.vn` | `Customer2@123` |

**Password đã hash bằng BCrypt cost=10** (khớp Spring Security default `BCryptPasswordEncoder`).
Login thử trên Postman / FE đều dùng được ngay.

`customer1` có 2 địa chỉ (mặc định + văn phòng), `customer2` có 1 địa chỉ — đủ để test
checkout flow có `addressId`.

### 1.5. Promotions (2)
| Code | Type | Value | Min order | Cap |
|---|---|---|---|---|
| `WELCOME10` | PERCENTAGE | 10% | 5.000.000 đ | 2.000.000 đ |
| `SUMMER2TR` | FIXED_AMOUNT | 2.000.000 đ | 20.000.000 đ | — |

### 1.6. Orders (3) — đủ trạng thái test
| Order | Customer | Status | Payment | Items |
|---|---|---|---|---|
| `ORD-YYYYMMDD-0001` (cũ 15 ngày) | customer1 | **DELIVERED** | PAID (SANDBOX_CARD) | Sofa Oak + Bàn Ăn Pine — dùng để test review |
| `ORD-YYYYMMDD-0001` (cũ 3 ngày) | customer2 | **SHIPPING** | PAID (SANDBOX_WALLET) | Sofa Teak + 2 Ghế Tắm Nắng |
| `ORD-YYYYMMDD-0001` (hôm nay) | customer1 | **PENDING** | UNPAID (COD) | Bàn Làm Việc + 2 Ghế Văn Phòng |

Trạng thái `statusHistory` được ghi đầy đủ để test timeline trên UI order detail.

### 1.7. Reviews (2)
customer1 đã review Sofa Oak (5★) + Bàn Ăn Pine (4★) cho đơn DELIVERED — để test:
- `GET /products/{id}/reviews` trả review
- `POST /products/{id}/reviews` của customer1 với cùng orderId → bị từ chối (duplicate)

### 1.8. Notifications (3)
1 ADMIN (ORDER_STATUS, chưa đọc), 1 WAREHOUSE (LOW_STOCK alert), 1 CUSTOMER (DELIVERED thông báo).

### 1.9. Inventory
- 1 inventory record / product
- `OUT001` (3) và `EX005` (4) để tồn kho thấp → test `GET /inventory/low-stock`
- Đơn DELIVERED → `quantityOnHand` giảm 1 (commit)
- Đơn SHIPPING + PENDING → `quantityReserved` tăng (reserve)

---

## 2. Cách chạy

### 2.1. Với mongosh (cần cài `mongosh` CLI)

```bash
# Default URI: mongodb://localhost:27017/woodfurni
mongosh "mongodb://localhost:27017/woodfurni" database/sample-data/seed.js

# Override URI qua biến môi trường
MONGODB_URI="mongodb://mongo:27017/woodfurni" \
  mongosh "mongodb://mongo:27017/woodfurni" database/sample-data/seed.js
```

### 2.2. Với docker compose (stack đang chạy)

```bash
# Inject file vào mongo container rồi chạy mongosh
docker compose exec -T mongo mongosh woodfurni < database/sample-data/seed.js
```

### 2.3. Với Node.js (không cần mongosh)

```bash
cd database/sample-data
npm install                                   # bcrypt + mongodb driver
MONGODB_URI="mongodb://localhost:27017/woodfurni" node seed.node.js
```

Hoặc:
```bash
npm run seed                                  # dùng MONGODB_URI mặc định
npm run seed:mongosh                          # alias cho cách 2.1
```

---

## 3. Idempotency

Script **reset toàn bộ collection nghiệp vụ** trước khi insert lại — an toàn để chạy nhiều lần.
Nếu muốn test concurrent (vd tạo order trong khi đang seed), nên chạy seed **trước** khi start backend.

> ⚠️ KHÔNG chạy trên production — lệnh `db.collection.drop()` sẽ xoá sạch dữ liệu.

---

## 4. Nếu cần đổi password hoặc regenerate hash

Script `seed.js` đã bake sẵn hash. Nếu muốn tự generate hash (vd tạo user mới):

```bash
node -e "console.log(require('bcrypt').hashSync('YOUR_PASSWORD', 10))"
```

Dán hash (ví dụ `$2b$10$...`) vào field `passwordHash` trong `seed.js`. Spring Security
`BCryptPasswordEncoder` chấp nhận cả 3 prefix `$2a$`, `$2b$`, `$2y$` — không cần convert.

---

## 5. Indexes

Script tự recreate tất cả indexes trùng với `database/indexes/*.js`. Nếu chạy seed
trước khi backend khởi động, bạn không cần chạy `database/indexes/*.js` riêng.

Nếu backend đã từng chạy (auto-index từ Spring `auto-index-creation: true`), nó
sẽ tạo trùng — MongoDB bỏ qua `createIndex` với cùng spec (`name` + `key`).
