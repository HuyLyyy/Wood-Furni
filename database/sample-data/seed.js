// =============================================================================
// WOODFURNI — Sample Data Seed (mongosh)
// =============================================================================
// Run with:
//   mongosh "mongodb://localhost:27017/woodfurni" database/sample-data/seed.js
//
// Hoặc trong docker compose stack:
//   docker compose exec -T mongo mongosh woodfurni < database/sample-data/seed.js
//
// Idempotent: sẽ xoá trước các collection nghiệp vụ rồi insert lại — dùng khi
// muốn reset về dữ liệu mẫu. KHÔNG chạy trên production.
//
// Passwords đã được hash sẵn bằng BCrypt cost=10 (khớp Spring Security
// BCryptPasswordEncoder mặc định). Xem database/sample-data/README.md để biết
// chi tiết và cách tạo lại hash nếu cần.
// =============================================================================

print("==> WOODFURNI seed starting...");

// Centralised DB handle so we can scope deletes cleanly.
const dbHandle = db.getSiblingDB('woodfurni');

const now = new Date();
const daysAgo = (n) => new Date(now.getTime() - n * 24 * 60 * 60 * 1000);
const daysFromNow = (n) => new Date(now.getTime() + n * 24 * 60 * 60 * 1000);

// =============================================================================
// 1. RESET — drop all collections that this script touches.
// =============================================================================
print("==> Resetting collections...");
dbHandle.users.drop();
dbHandle.categories.drop();
dbHandle.materials.drop();
dbHandle.products.drop();
dbHandle.inventories.drop();
dbHandle.carts.drop();
dbHandle.orders.drop();
dbHandle.payments.drop();
dbHandle.promotions.drop();
dbHandle.reviews.drop();
dbHandle.notifications.drop();

// =============================================================================
// 2. ROLES seed reference — không insert vào DB (Roles được kiểm tra qua enum)
// =============================================================================
// Role enum values (dùng cho User.role):
// CUSTOMER, SALES, WAREHOUSE, CONTENT, ADMIN

// =============================================================================
// 3. CATEGORIES (5) — Indoor: Living Room, Bedroom, Dining Room, Office; Outdoor: Garden
// =============================================================================
print("==> Inserting categories...");
const catLivingRoomId = ObjectId();
const catBedroomId = ObjectId();
const catDiningRoomId = ObjectId();
const catOfficeId = ObjectId();
const catGardenId = ObjectId();

dbHandle.categories.insertMany([
  {
    _id: catLivingRoomId,
    name: 'Living Room',
    slug: 'living-room',
    environment: 'INDOOR',
    parentId: null,
    order: 1,
    status: 'ACTIVE',
    createdAt: daysAgo(30),
    updatedAt: daysAgo(30),
  },
  {
    _id: catBedroomId,
    name: 'Bedroom',
    slug: 'bedroom',
    environment: 'INDOOR',
    parentId: null,
    order: 2,
    status: 'ACTIVE',
    createdAt: daysAgo(30),
    updatedAt: daysAgo(30),
  },
  {
    _id: catDiningRoomId,
    name: 'Dining Room',
    slug: 'dining-room',
    environment: 'INDOOR',
    parentId: null,
    order: 3,
    status: 'ACTIVE',
    createdAt: daysAgo(30),
    updatedAt: daysAgo(30),
  },
  {
    _id: catOfficeId,
    name: 'Office',
    slug: 'office',
    environment: 'INDOOR',
    parentId: null,
    order: 4,
    status: 'ACTIVE',
    createdAt: daysAgo(30),
    updatedAt: daysAgo(30),
  },
  {
    _id: catGardenId,
    name: 'Garden',
    slug: 'garden',
    environment: 'OUTDOOR',
    parentId: null,
    order: 5,
    status: 'ACTIVE',
    createdAt: daysAgo(30),
    updatedAt: daysAgo(30),
  },
]);

// =============================================================================
// 4. MATERIALS (5) — Oak, Walnut, Pine, Acacia, Teak
// =============================================================================
print("==> Inserting materials...");
const matOakId = ObjectId();
const matWalnutId = ObjectId();
const matPineId = ObjectId();
const matAcaciaId = ObjectId();
const matTeakId = ObjectId();

dbHandle.materials.insertMany([
  {
    _id: matOakId,
    name: 'Oak',
    code: 'OAK',
    description: 'Gỗ Sồi — cứng, bền, vân đẹp, phù hợp nội thất cao cấp.',
    createdAt: daysAgo(30),
    updatedAt: daysAgo(30),
  },
  {
    _id: matWalnutId,
    name: 'Walnut',
    code: 'WALNUT',
    description: 'Gỗ Óc Chó — màu nâu socola sang trọng, thường dùng cho nội thất hiện đại.',
    createdAt: daysAgo(30),
    updatedAt: daysAgo(30),
  },
  {
    _id: matPineId,
    name: 'Pine',
    code: 'PINE',
    description: 'Gỗ Thông — nhẹ, giá phải chăng, thân thiện phong cách Bắc Âu.',
    createdAt: daysAgo(30),
    updatedAt: daysAgo(30),
  },
  {
    _id: matAcaciaId,
    name: 'Acacia',
    code: 'ACACIA',
    description: 'Gỗ Tràm — chịu nước tốt, phù hợp đồ ngoại thất và sân vườn.',
    createdAt: daysAgo(30),
    updatedAt: daysAgo(30),
  },
  {
    _id: matTeakId,
    name: 'Teak',
    code: 'TEAK',
    description: 'Gỗ Tếch — chịu thời tiết khắc nghiệt, lý tưởng cho ngoài trời.',
    createdAt: daysAgo(30),
    updatedAt: daysAgo(30),
  },
]);

// =============================================================================
// 5. USERS (6) — 1 ADMIN, 1 SALES, 1 WAREHOUSE, 1 CONTENT, 2 CUSTOMER
// =============================================================================
// Password đã được hash bằng BCrypt cost=10 (khớp Spring Security default).
// Generate lại bằng: bcrypt.hash('PlainPassword', 10) trong Node.js.
print("==> Inserting users...");
const userAdminId = ObjectId();
const userSalesId = ObjectId();
const userWarehouseId = ObjectId();
const userContentId = ObjectId();
const userCustomer1Id = ObjectId();
const userCustomer2Id = ObjectId();

const cust1AddrId = 'addr-c1-default';
const cust1Addr2Id = 'addr-c1-office';
const cust2AddrId = 'addr-c2-default';

dbHandle.users.insertMany([
  {
    _id: userAdminId,
    email: 'admin@woodfurni.vn',
    passwordHash: '$2b$10$xMDF/ktGpyrN0cHJQFSp/.IGw5d/cWrcCWEi8mix3IJBE1lR6VCFy',
    fullName: 'Nguyễn Văn Admin',
    phone: '0900000001',
    role: 'ADMIN',
    addresses: [],
    status: 'ACTIVE',
    createdAt: daysAgo(60),
    updatedAt: daysAgo(60),
  },
  {
    _id: userSalesId,
    email: 'sales@woodfurni.vn',
    passwordHash: '$2b$10$tJhT5eYIfE5eaBqacuYkxuHXFUxuIlJDcOAsOm2v0hAzVeUC85XAy',
    fullName: 'Trần Thị Sales',
    phone: '0900000002',
    role: 'SALES',
    addresses: [],
    status: 'ACTIVE',
    createdAt: daysAgo(60),
    updatedAt: daysAgo(60),
  },
  {
    _id: userWarehouseId,
    email: 'warehouse@woodfurni.vn',
    passwordHash: '$2b$10$od9fNJ86txU1xITMueTHcO0wHRJWTn2uf4wWgNzuP4hSQo75qMpuy',
    fullName: 'Lê Văn Kho',
    phone: '0900000003',
    role: 'WAREHOUSE',
    addresses: [],
    status: 'ACTIVE',
    createdAt: daysAgo(60),
    updatedAt: daysAgo(60),
  },
  {
    _id: userContentId,
    email: 'content@woodfurni.vn',
    passwordHash: '$2b$10$TNusCwO0sS2XTdhbZ62Zc.0obpzXmzodDNPJMDhPrxWBWYKb3z1zm',
    fullName: 'Phạm Thị Content',
    phone: '0900000004',
    role: 'CONTENT',
    addresses: [],
    status: 'ACTIVE',
    createdAt: daysAgo(60),
    updatedAt: daysAgo(60),
  },
  {
    _id: userCustomer1Id,
    email: 'customer1@woodfurni.vn',
    passwordHash: '$2b$10$NYP2AAAE1am/z1mXaZPYMOUMkAjlBL4wz6b2zbnTUhqO9sLKdeZvq',
    fullName: 'Hoàng Minh Khách',
    phone: '0901234567',
    role: 'CUSTOMER',
    addresses: [
      {
        id: cust1AddrId,
        label: 'Nhà riêng',
        line1: '12 Nguyễn Huệ',
        ward: 'Phường Bến Nghé',
        district: 'Quận 1',
        city: 'TP. Hồ Chí Minh',
        phone: '0901234567',
        isDefault: true,
      },
      {
        id: cust1Addr2Id,
        label: 'Văn phòng',
        line1: 'Tầng 5, 99 Lê Lợi',
        ward: 'Phường Bến Thành',
        district: 'Quận 1',
        city: 'TP. Hồ Chí Minh',
        phone: '0901234567',
        isDefault: false,
      },
    ],
    status: 'ACTIVE',
    createdAt: daysAgo(20),
    updatedAt: daysAgo(20),
  },
  {
    _id: userCustomer2Id,
    email: 'customer2@woodfurni.vn',
    passwordHash: '$2b$10$OnifNrZ/.YKh2TJ2dyqsg.NepI0tU.KOWbLgpLE8KwAclYqok1Xe2',
    fullName: 'Đỗ Thị Mua',
    phone: '0909876543',
    role: 'CUSTOMER',
    addresses: [
      {
        id: cust2AddrId,
        label: 'Nhà riêng',
        line1: '45 Trần Phú',
        ward: 'Phường Điện Biên',
        district: 'Quận Ba Đình',
        city: 'Hà Nội',
        phone: '0909876543',
        isDefault: true,
      },
    ],
    status: 'ACTIVE',
    createdAt: daysAgo(15),
    updatedAt: daysAgo(15),
  },
]);

// =============================================================================
// 6. PRODUCTS (15) — 10 SKU mẫu (IN001-IN005, OUT001-OUT005) + 5 tự tạo
// =============================================================================
print("==> Inserting products...");
const productIds = {};
const p = (sku) => {
  if (!productIds[sku]) productIds[sku] = ObjectId();
  return productIds[sku];
};

const products = [
  // ----- INDOOR (IN001–IN005) -----------------------------------------------
  {
    _id: p('IN001'),
    sku: 'IN001',
    name: 'Sofa Gỗ Sồi 3 Chỗ',
    slug: 'sofa-go-soi-3-cho',
    categoryId: catLivingRoomId,
    materialIds: [matOakId],
    environment: 'INDOOR',
    room: 'LIVING_ROOM',
    dimensions: { width: 220, height: 85, depth: 90 },
    weight: 65,
    color: 'Nâu tự nhiên',
    finish: 'Lacquer bóng mờ',
    price: NumberDecimal('28500000'),
    salePrice: NumberDecimal('25500000'),
    images: [
      'https://placehold.co/800x600/8B6F47/FFF?text=IN001+Sofa+Oak',
    ],
    description: 'Sofa 3 chỗ ngồi làm từ gỗ Sồi tự nhiên nhập khẩu, khung chắc chắn, vân gỗ đẹp. Kèm đệm mousse D40 bọc vải nỉ cao cấp. Phù hợp phòng khách hiện đại.',
    warranty: '24 tháng',
    status: 'ACTIVE',
    ratingAverage: 4.8,
    ratingCount: 12,
    createdAt: daysAgo(25),
    updatedAt: daysAgo(5),
  },
  {
    _id: p('IN002'),
    sku: 'IN002',
    name: 'Giường Ngủ Gỗ Óc Chó 1m8',
    slug: 'giuong-ngu-go-oc-cho-1m8',
    categoryId: catBedroomId,
    materialIds: [matWalnutId],
    environment: 'INDOOR',
    room: 'BEDROOM',
    dimensions: { width: 180, height: 110, depth: 210 },
    weight: 95,
    color: 'Nâu socola',
    finish: 'Oliu tự nhiên',
    price: NumberDecimal('32500000'),
    salePrice: null,
    images: [
      'https://placehold.co/800x600/5C3A21/FFF?text=IN002+Bed+Walnut',
    ],
    description: 'Giường ngủ đôi 1m8 gỗ Óc Chó nguyên khối, đầu giường bọc da công nghiệp. Thiết kế tối giản, sang trọng cho phòng ngủ master.',
    warranty: '36 tháng',
    status: 'ACTIVE',
    ratingAverage: 4.9,
    ratingCount: 8,
    createdAt: daysAgo(24),
    updatedAt: daysAgo(3),
  },
  {
    _id: p('IN003'),
    sku: 'IN003',
    name: 'Bàn Ăn Gỗ Thông 6 Ghế',
    slug: 'ban-an-go-thong-6-ghe',
    categoryId: catDiningRoomId,
    materialIds: [matPineId],
    environment: 'INDOOR',
    room: 'DINING_ROOM',
    dimensions: { width: 180, height: 75, depth: 90 },
    weight: 45,
    color: 'Nâu nhạt',
    finish: 'Sơn PU mờ',
    price: NumberDecimal('12500000'),
    salePrice: NumberDecimal('11250000'),
    images: [
      'https://placehold.co/800x600/C9A574/FFF?text=IN003+Dining+Pine',
    ],
    description: 'Bộ bàn ăn 6 ghế làm từ gỗ Thông tự nhiên, phong cách Scandinavian. Mặt bàn phủ lớp chống thấm, dễ vệ sinh.',
    warranty: '12 tháng',
    status: 'ACTIVE',
    ratingAverage: 4.5,
    ratingCount: 15,
    createdAt: daysAgo(22),
    updatedAt: daysAgo(2),
  },
  {
    _id: p('IN004'),
    sku: 'IN004',
    name: 'Bàn Làm Việc Gỗ Sồi Có Ngăn Kéo',
    slug: 'ban-lam-viec-go-soi-co-ngan-keo',
    categoryId: catOfficeId,
    materialIds: [matOakId],
    environment: 'INDOOR',
    room: 'OFFICE',
    dimensions: { width: 140, height: 75, depth: 60 },
    weight: 38,
    color: 'Nâu tự nhiên',
    finish: 'Lacquer bóng mờ',
    price: NumberDecimal('9800000'),
    salePrice: null,
    images: [
      'https://placehold.co/800x600/8B6F47/FFF?text=IN004+Desk+Oak',
    ],
    description: 'Bàn làm việc gỗ Sồi tự nhiên, 3 ngăn kéo ray âm, mặt bàn rộng rãi cho laptop + tài liệu. Lắp ráp đơn giản.',
    warranty: '24 tháng',
    status: 'ACTIVE',
    ratingAverage: 4.3,
    ratingCount: 6,
    createdAt: daysAgo(20),
    updatedAt: daysAgo(1),
  },
  {
    _id: p('IN005'),
    sku: 'IN005',
    name: 'Tủ Quần Áo 3 Cánh Gỗ Óc Chó',
    slug: 'tu-quan-ao-3-canh-go-oc-cho',
    categoryId: catBedroomId,
    materialIds: [matWalnutId],
    environment: 'INDOOR',
    room: 'BEDROOM',
    dimensions: { width: 180, height: 220, depth: 60 },
    weight: 120,
    color: 'Nâu socola',
    finish: 'Oliu tự nhiên',
    price: NumberDecimal('28000000'),
    salePrice: NumberDecimal('24500000'),
    images: [
      'https://placehold.co/800x600/5C3A21/FFF?text=IN005+Wardrobe',
    ],
    description: 'Tủ quần áo 3 cánh gỗ Óc Chó, bên trong chia ngăn thông minh: kệ treo, ngăn gấp, ngăn kéo. Cửa gương soi toàn thân.',
    warranty: '36 tháng',
    status: 'ACTIVE',
    ratingAverage: 4.7,
    ratingCount: 9,
    createdAt: daysAgo(18),
    updatedAt: daysAgo(1),
  },
  // ----- OUTDOOR (OUT001–OUT005) --------------------------------------------
  {
    _id: p('OUT001'),
    sku: 'OUT001',
    name: 'Bộ Bàn Ghế Sân Vườn Gỗ Teak',
    slug: 'bo-ban-ghe-san-vuon-go-teak',
    categoryId: catGardenId,
    materialIds: [matTeakId],
    environment: 'OUTDOOR',
    room: 'GARDEN',
    dimensions: { width: 200, height: 75, depth: 100 },
    weight: 55,
    color: 'Vàng nâu tự nhiên',
    finish: 'Teak oil',
    price: NumberDecimal('22500000'),
    salePrice: null,
    images: [
      'https://placehold.co/800x600/A0824D/FFF?text=OUT001+Teak+Set',
    ],
    description: 'Bộ bàn ghế 4 chỗ ngoài trời gỗ Teak nhập khẩu, chịu mưa nắng tuyệt vời. Bao gồm 1 bàn chữ nhật + 4 ghế có đệm.',
    warranty: '60 tháng',
    status: 'ACTIVE',
    ratingAverage: 4.9,
    ratingCount: 21,
    createdAt: daysAgo(28),
    updatedAt: daysAgo(7),
  },
  {
    _id: p('OUT002'),
    sku: 'OUT002',
    name: 'Ghế Tắm Nắng Gỗ Teak Có Bánh Xe',
    slug: 'ghe-tam-nang-go-teak-co-banh-xe',
    categoryId: catGardenId,
    materialIds: [matTeakId],
    environment: 'OUTDOOR',
    room: 'GARDEN',
    dimensions: { width: 70, height: 95, depth: 180 },
    weight: 28,
    color: 'Vàng nâu',
    finish: 'Teak oil',
    price: NumberDecimal('8500000'),
    salePrice: NumberDecimal('7350000'),
    images: [
      'https://placehold.co/800x600/A0824D/FFF?text=OUT002+Sun+Lounger',
    ],
    description: 'Ghế tắm nắng gỗ Teak, điều chỉnh 5 nấc ngả, bánh xe di chuyển. Hoàn hảo cho hồ bơi hoặc sân vườn.',
    warranty: '36 tháng',
    status: 'ACTIVE',
    ratingAverage: 4.6,
    ratingCount: 11,
    createdAt: daysAgo(17),
    updatedAt: daysAgo(2),
  },
  {
    _id: p('OUT003'),
    sku: 'OUT003',
    name: 'Xích Đu Gỗ Tràm Sân Vườn',
    slug: 'xich-du-go-tram-san-vuon',
    categoryId: catGardenId,
    materialIds: [matAcaciaId],
    environment: 'OUTDOOR',
    room: 'GARDEN',
    dimensions: { width: 150, height: 180, depth: 120 },
    weight: 45,
    color: 'Nâu ấm',
    finish: 'Dầu lau tự nhiên',
    price: NumberDecimal('6500000'),
    salePrice: null,
    images: [
      'https://placehold.co/800x600/7D5A3A/FFF?text=OUT003+Swing',
    ],
    description: 'Xích đu gỗ Tràm dành cho sân vườn, khung chịu lực 200kg, dây xích thép không gỉ. Đã bao gồm đệm ngồi.',
    warranty: '24 tháng',
    status: 'ACTIVE',
    ratingAverage: 4.4,
    ratingCount: 7,
    createdAt: daysAgo(15),
    updatedAt: daysAgo(1),
  },
  {
    _id: p('OUT004'),
    sku: 'OUT004',
    name: 'Chậu Cây Gỗ Teak Vuông Lớn',
    slug: 'chau-cay-go-teak-vuong-lon',
    categoryId: catGardenId,
    materialIds: [matTeakId],
    environment: 'OUTDOOR',
    room: 'GARDEN',
    dimensions: { width: 50, height: 50, depth: 50 },
    weight: 15,
    color: 'Vàng nâu',
    finish: 'Teak oil',
    price: NumberDecimal('2900000'),
    salePrice: null,
    images: [
      'https://placehold.co/800x600/A0824D/FFF?text=OUT004+Planter',
    ],
    description: 'Chậu cây vuông gỗ Teak cao cấp, lót nilon chống thấm bên trong. Phù hợp trang trí sảnh, ban công, sân vườn.',
    warranty: '24 tháng',
    status: 'ACTIVE',
    ratingAverage: 4.2,
    ratingCount: 4,
    createdAt: daysAgo(13),
    updatedAt: daysAgo(1),
  },
  {
    _id: p('OUT005'),
    sku: 'OUT005',
    name: 'Bàn Ăn Ngoài Trời Gỗ Acacia 6 Ghế',
    slug: 'ban-an-ngoai-troi-go-acacia-6-ghe',
    categoryId: catGardenId,
    materialIds: [matAcaciaId],
    environment: 'OUTDOOR',
    room: 'GARDEN',
    dimensions: { width: 200, height: 75, depth: 100 },
    weight: 50,
    color: 'Nâu ấm',
    finish: 'Dầu lau tự nhiên',
    price: NumberDecimal('15500000'),
    salePrice: NumberDecimal('13500000'),
    images: [
      'https://placehold.co/800x600/7D5A3A/FFF?text=OUT005+Dining+Acacia',
    ],
    description: 'Bộ bàn ăn ngoài trời 6 ghế gỗ Acacia, chịu nước và tia UV. Phù hợp cho sân vườn, sân thượng, quán cafe.',
    warranty: '36 tháng',
    status: 'ACTIVE',
    ratingAverage: 4.7,
    ratingCount: 13,
    createdAt: daysAgo(16),
    updatedAt: daysAgo(2),
  },
  // ----- EXTRA 5 (tự tạo cho filter đa dạng) --------------------------------
  {
    _id: p('EX001'),
    name: 'Bàn Trà Gỗ Sồi Hình Chữ Nhật',
    sku: 'EX001',
    slug: 'ban-tra-go-soi-hinh-chu-nhat',
    categoryId: catLivingRoomId,
    materialIds: [matOakId],
    environment: 'INDOOR',
    room: 'LIVING_ROOM',
    dimensions: { width: 120, height: 45, depth: 60 },
    weight: 25,
    color: 'Nâu tự nhiên',
    finish: 'Lacquer bóng mờ',
    price: NumberDecimal('6200000'),
    salePrice: null,
    images: [
      'https://placehold.co/800x600/8B6F47/FFF?text=EX001+Coffee+Table',
    ],
    description: 'Bàn trà gỗ Sồi hình chữ nhật, thiết kế tối giản Bắc Âu, ngăn kéo âm bên hông. Phù hợp phòng khách nhỏ.',
    warranty: '24 tháng',
    status: 'ACTIVE',
    ratingAverage: 4.4,
    ratingCount: 5,
    createdAt: daysAgo(12),
    updatedAt: daysAgo(1),
  },
  {
    _id: p('EX002'),
    name: 'Ghế Văn Phòng Gỗ Óc Chó Bọc Da',
    sku: 'EX002',
    slug: 'ghe-van-phong-go-oc-cho-boc-da',
    categoryId: catOfficeId,
    materialIds: [matWalnutId],
    environment: 'INDOOR',
    room: 'OFFICE',
    dimensions: { width: 65, height: 110, depth: 65 },
    weight: 18,
    color: 'Nâu socola + đen',
    finish: 'Da PU + gỗ Óc Chó',
    price: NumberDecimal('4500000'),
    salePrice: NumberDecimal('3950000'),
    images: [
      'https://placehold.co/800x600/5C3A21/FFF?text=EX002+Office+Chair',
    ],
    description: 'Ghế xoay văn phòng khung gỗ Óc Chó, đệm ngồi bọc da PU cao cấp, tay vịn da, chân thép mạ crom.',
    warranty: '12 tháng',
    status: 'ACTIVE',
    ratingAverage: 4.1,
    ratingCount: 3,
    createdAt: daysAgo(10),
    updatedAt: daysAgo(1),
  },
  {
    _id: p('EX003'),
    name: 'Kệ Sách Gỗ Thông 5 Tầng',
    sku: 'EX003',
    slug: 'ke-sach-go-thong-5-tang',
    categoryId: catOfficeId,
    materialIds: [matPineId],
    environment: 'INDOOR',
    room: 'OFFICE',
    dimensions: { width: 80, height: 180, depth: 30 },
    weight: 22,
    color: 'Nâu nhạt',
    finish: 'Sơn PU mờ',
    price: NumberDecimal('3200000'),
    salePrice: null,
    images: [
      'https://placehold.co/800x600/C9A574/FFF?text=EX003+Bookshelf',
    ],
    description: 'Kệ sách 5 tầng gỗ Thông tự nhiên, chịu lực tốt, phù hợp phòng đọc sách, văn phòng nhỏ.',
    warranty: '12 tháng',
    status: 'ACTIVE',
    ratingAverage: 4.0,
    ratingCount: 2,
    createdAt: daysAgo(9),
    updatedAt: daysAgo(1),
  },
  {
    _id: p('EX004'),
    name: 'Bàn Ăn Tròn Gỗ Sồi 4 Ghế',
    sku: 'EX004',
    slug: 'ban-an-tron-go-soi-4-ghe',
    categoryId: catDiningRoomId,
    materialIds: [matOakId],
    environment: 'INDOOR',
    room: 'DINING_ROOM',
    dimensions: { width: 120, height: 75, depth: 120 },
    weight: 40,
    color: 'Nâu tự nhiên',
    finish: 'Lacquer bóng mờ',
    price: NumberDecimal('16500000'),
    salePrice: null,
    images: [
      'https://placehold.co/800x600/8B6F47/FFF?text=EX004+Round+Dining',
    ],
    description: 'Bàn ăn tròn 4 ghế gỗ Sồi nguyên khối, phù hợp phòng ăn gia đình nhỏ. Mặt bàn đường kính 120cm.',
    warranty: '24 tháng',
    status: 'ACTIVE',
    ratingAverage: 4.6,
    ratingCount: 8,
    createdAt: daysAgo(14),
    updatedAt: daysAgo(2),
  },
  {
    _id: p('EX005'),
    name: 'Bộ Sofa Gỗ Teak Ngoài Trời 4 Chỗ',
    sku: 'EX005',
    slug: 'bo-sofa-go-teak-ngoai-troi-4-cho',
    categoryId: catGardenId,
    materialIds: [matTeakId],
    environment: 'OUTDOOR',
    room: 'GARDEN',
    dimensions: { width: 240, height: 85, depth: 90 },
    weight: 75,
    color: 'Vàng nâu',
    finish: 'Teak oil',
    price: NumberDecimal('36500000'),
    salePrice: NumberDecimal('32500000'),
    images: [
      'https://placehold.co/800x600/A0824D/FFF?text=EX005+Outdoor+Sofa',
    ],
    description: 'Bộ sofa góc 4 chỗ ngoài trời gỗ Teak, kèm đệm mousse chống thấm nước. Hoàn hảo cho patio hoặc sân thượng lớn.',
    warranty: '60 tháng',
    status: 'ACTIVE',
    ratingAverage: 4.8,
    ratingCount: 16,
    createdAt: daysAgo(19),
    updatedAt: daysAgo(3),
  },
];

dbHandle.products.insertMany(products);

// =============================================================================
// 7. INVENTORIES (1 per product)
// =============================================================================
print("==> Inserting inventories...");
const inventoryRows = products.map((prod) => {
  // OUT001 và EX005 để tồn kho thấp để test low-stock + low-stock alert
  let qty;
  if (prod.sku === 'OUT001') qty = 3;
  else if (prod.sku === 'EX005') qty = 4;
  else if (prod.sku === 'IN001') qty = 12;
  else if (prod.sku === 'IN002') qty = 8;
  else if (prod.sku === 'IN003') qty = 25;
  else if (prod.sku === 'IN004') qty = 18;
  else if (prod.sku === 'IN005') qty = 6;
  else if (prod.sku === 'OUT002') qty = 15;
  else if (prod.sku === 'OUT003') qty = 10;
  else if (prod.sku === 'OUT004') qty = 30;
  else if (prod.sku === 'OUT005') qty = 14;
  else qty = 20;

  return {
    _id: ObjectId(),
    productId: prod._id,
    quantityOnHand: qty,
    quantityReserved: 0,
    lowStockThreshold: 5,
    updatedAt: new Date(),
  };
});

dbHandle.inventories.insertMany(inventoryRows);

// =============================================================================
// 8. PROMOTIONS (2) — 1 PERCENTAGE, 1 FIXED_AMOUNT
// =============================================================================
print("==> Inserting promotions...");
dbHandle.promotions.insertMany([
  {
    _id: ObjectId(),
    code: 'WELCOME10',
    type: 'PERCENTAGE',
    value: NumberDecimal('10'),
    minOrderAmount: NumberDecimal('5000000'),
    maxDiscountAmount: NumberDecimal('2000000'),
    startDate: daysAgo(30),
    endDate: daysFromNow(60),
    usageLimit: 100,
    usedCount: 1,
    status: 'ACTIVE',
  },
  {
    _id: ObjectId(),
    code: 'SUMMER2TR',
    type: 'FIXED_AMOUNT',
    value: NumberDecimal('2000000'),
    minOrderAmount: NumberDecimal('20000000'),
    maxDiscountAmount: null,
    startDate: daysAgo(15),
    endDate: daysFromNow(45),
    usageLimit: 50,
    usedCount: 0,
    status: 'ACTIVE',
  },
]);

// =============================================================================
// 9. ORDERS (3) — 1 DELIVERED, 1 SHIPPING, 1 PENDING — đủ để test review
// =============================================================================
print("==> Inserting orders + payments + reviews...");
const orderDeliveredId = ObjectId();
const orderShippingId = ObjectId();
const orderPendingId = ObjectId();

const orderDeliveredNumber = 'ORD-' + formatDate(daysAgo(15)) + '-0001';
const orderShippingNumber = 'ORD-' + formatDate(daysAgo(3)) + '-0001';
const orderPendingNumber = 'ORD-' + formatDate(now) + '-0001';

function formatDate(d) {
  return d.toISOString().slice(0, 10).replace(/-/g, '');
}

// ---- Order 1: DELIVERED (customer1 mua Sofa Oak + Bàn Ăn Pine) --------------
const orderDeliveredItems = [
  {
    productId: productIds['IN001'],
    productName: 'Sofa Gỗ Sồi 3 Chỗ',
    sku: 'IN001',
    unitPrice: NumberDecimal('25500000'),
    quantity: 1,
    subtotal: NumberDecimal('25500000'),
  },
  {
    productId: productIds['IN003'],
    productName: 'Bàn Ăn Gỗ Thông 6 Ghế',
    sku: 'IN003',
    unitPrice: NumberDecimal('11250000'),
    quantity: 1,
    subtotal: NumberDecimal('11250000'),
  },
];

const orderDeliveredSubtotal = NumberDecimal('36750000');
const orderDeliveredDiscount = NumberDecimal('2000000'); // áp SUMMER2TR
const orderDeliveredTotal = NumberDecimal('34750000');

dbHandle.orders.insertOne({
  _id: orderDeliveredId,
  orderNumber: orderDeliveredNumber,
  customerId: userCustomer1Id,
  items: orderDeliveredItems,
  shippingAddress: {
    label: 'Nhà riêng',
    line1: '12 Nguyễn Huệ',
    ward: 'Phường Bến Nghé',
    district: 'Quận 1',
    city: 'TP. Hồ Chí Minh',
    phone: '0901234567',
  },
  promotionCode: 'SUMMER2TR',
  discountAmount: orderDeliveredDiscount,
  subtotalAmount: orderDeliveredSubtotal,
  totalAmount: orderDeliveredTotal,
  status: 'DELIVERED',
  paymentStatus: 'PAID',
  statusHistory: [
    { status: 'PENDING', changedAt: daysAgo(15), changedBy: userCustomer1Id.toString() },
    { status: 'CONFIRMED', changedAt: daysAgo(14), changedBy: userSalesId.toString() },
    { status: 'PROCESSING', changedAt: daysAgo(13), changedBy: userSalesId.toString() },
    { status: 'SHIPPING', changedAt: daysAgo(10), changedBy: userWarehouseId.toString() },
    { status: 'DELIVERED', changedAt: daysAgo(7), changedBy: userWarehouseId.toString() },
  ],
  createdAt: daysAgo(15),
  updatedAt: daysAgo(7),
});

dbHandle.payments.insertOne({
  _id: ObjectId(),
  orderId: orderDeliveredId,
  method: 'SANDBOX_CARD',
  amount: orderDeliveredTotal,
  status: 'SUCCESS',
  transactionRef: 'TX-' + orderDeliveredNumber + '-CARD',
  paidAt: daysAgo(14),
  createdAt: daysAgo(15),
});

// Reserve/commit cho inventory tương ứng (DELIVERED → commit luôn)
dbHandle.inventories.updateOne(
  { productId: productIds['IN001'] },
  { $inc: { quantityOnHand: -1, quantityReserved: 0 }, $set: { updatedAt: daysAgo(7) } }
);
dbHandle.inventories.updateOne(
  { productId: productIds['IN003'] },
  { $inc: { quantityOnHand: -1, quantityReserved: 0 }, $set: { updatedAt: daysAgo(7) } }
);

// Reviews (customer1 có thể đã review Sofa Oak vì đơn DELIVERED)
const reviewId1 = ObjectId();
dbHandle.reviews.insertMany([
  {
    _id: reviewId1,
    productId: productIds['IN001'],
    userId: userCustomer1Id,
    orderId: orderDeliveredId,
    rating: 5,
    comment: 'Sofa rất đẹp, gỗ Sồi chắc chắn, đệm ngồi êm. Giao hàng nhanh, nhân viên lắp đặt chuyên nghiệp.',
    status: 'PUBLISHED',
    createdAt: daysAgo(5),
  },
  {
    _id: ObjectId(),
    productId: productIds['IN003'],
    userId: userCustomer1Id,
    orderId: orderDeliveredId,
    rating: 4,
    comment: 'Bàn ăn đúng mô tả, gỗ Thông nhẹ và dễ vệ sinh. Trừ 1 sao vì giao hàng trễ 1 ngày.',
    status: 'PUBLISHED',
    createdAt: daysAgo(4),
  },
]);

// ---- Order 2: SHIPPING (customer2 mua Sofa Teak + Ghế tắm nắng) -----------
const orderShippingItems = [
  {
    productId: productIds['OUT001'],
    productName: 'Bộ Bàn Ghế Sân Vườn Gỗ Teak',
    sku: 'OUT001',
    unitPrice: NumberDecimal('22500000'),
    quantity: 1,
    subtotal: NumberDecimal('22500000'),
  },
  {
    productId: productIds['OUT002'],
    productName: 'Ghế Tắm Nắng Gỗ Teak Có Bánh Xe',
    sku: 'OUT002',
    unitPrice: NumberDecimal('7350000'),
    quantity: 2,
    subtotal: NumberDecimal('14700000'),
  },
];

const orderShippingSubtotal = NumberDecimal('37200000');
const orderShippingDiscount = NumberDecimal('0');
const orderShippingTotal = NumberDecimal('37200000');

dbHandle.orders.insertOne({
  _id: orderShippingId,
  orderNumber: orderShippingNumber,
  customerId: userCustomer2Id,
  items: orderShippingItems,
  shippingAddress: {
    label: 'Nhà riêng',
    line1: '45 Trần Phú',
    ward: 'Phường Điện Biên',
    district: 'Quận Ba Đình',
    city: 'Hà Nội',
    phone: '0909876543',
  },
  promotionCode: null,
  discountAmount: orderShippingDiscount,
  subtotalAmount: orderShippingSubtotal,
  totalAmount: orderShippingTotal,
  status: 'SHIPPING',
  paymentStatus: 'PAID',
  statusHistory: [
    { status: 'PENDING', changedAt: daysAgo(3), changedBy: userCustomer2Id.toString() },
    { status: 'CONFIRMED', changedAt: daysAgo(2), changedBy: userSalesId.toString() },
    { status: 'PROCESSING', changedAt: daysAgo(1), changedBy: userWarehouseId.toString() },
    { status: 'SHIPPING', changedAt: daysAgo(0), changedBy: userWarehouseId.toString() },
  ],
  createdAt: daysAgo(3),
  updatedAt: daysAgo(0),
});

dbHandle.payments.insertOne({
  _id: ObjectId(),
  orderId: orderShippingId,
  method: 'SANDBOX_WALLET',
  amount: orderShippingTotal,
  status: 'SUCCESS',
  transactionRef: 'TX-' + orderShippingNumber + '-WALLET',
  paidAt: daysAgo(2),
  createdAt: daysAgo(3),
});

// Reserve cho inventory (SHIPPING → quantityReserved = qty, chưa commit)
dbHandle.inventories.updateOne(
  { productId: productIds['OUT001'] },
  { $inc: { quantityReserved: 1 }, $set: { updatedAt: daysAgo(0) } }
);
dbHandle.inventories.updateOne(
  { productId: productIds['OUT002'] },
  { $inc: { quantityReserved: 2 }, $set: { updatedAt: daysAgo(0) } }
);

// ---- Order 3: PENDING (customer1 mua Bàn Làm Việc + Ghế Văn Phòng) ---------
const orderPendingItems = [
  {
    productId: productIds['IN004'],
    productName: 'Bàn Làm Việc Gỗ Sồi Có Ngăn Kéo',
    sku: 'IN004',
    unitPrice: NumberDecimal('9800000'),
    quantity: 1,
    subtotal: NumberDecimal('9800000'),
  },
  {
    productId: productIds['EX002'],
    productName: 'Ghế Văn Phòng Gỗ Óc Chó Bọc Da',
    sku: 'EX002',
    unitPrice: NumberDecimal('3950000'),
    quantity: 2,
    subtotal: NumberDecimal('7900000'),
  },
];

const orderPendingSubtotal = NumberDecimal('17700000');
const orderPendingDiscount = NumberDecimal('1770000'); // WELCOME10 = 10% cap 2tr
const orderPendingTotal = NumberDecimal('15930000');

dbHandle.orders.insertOne({
  _id: orderPendingId,
  orderNumber: orderPendingNumber,
  customerId: userCustomer1Id,
  items: orderPendingItems,
  shippingAddress: {
    label: 'Văn phòng',
    line1: 'Tầng 5, 99 Lê Lợi',
    ward: 'Phường Bến Thành',
    district: 'Quận 1',
    city: 'TP. Hồ Chí Minh',
    phone: '0901234567',
  },
  promotionCode: 'WELCOME10',
  discountAmount: orderPendingDiscount,
  subtotalAmount: orderPendingSubtotal,
  totalAmount: orderPendingTotal,
  status: 'PENDING',
  paymentStatus: 'UNPAID',
  statusHistory: [
    { status: 'PENDING', changedAt: now, changedBy: userCustomer1Id.toString() },
  ],
  createdAt: now,
  updatedAt: now,
});

dbHandle.payments.insertOne({
  _id: ObjectId(),
  orderId: orderPendingId,
  method: 'COD',
  amount: orderPendingTotal,
  status: 'PENDING',
  transactionRef: null,
  paidAt: null,
  createdAt: now,
});

// Reserve cho inventory (PENDING → quantityReserved = qty)
dbHandle.inventories.updateOne(
  { productId: productIds['IN004'] },
  { $inc: { quantityReserved: 1 }, $set: { updatedAt: now } }
);
dbHandle.inventories.updateOne(
  { productId: productIds['EX002'] },
  { $inc: { quantityReserved: 2 }, $set: { updatedAt: now } }
);

// =============================================================================
// 10. NOTIFICATIONS (3 mẫu) — cho ADMIN và WAREHOUSE
// =============================================================================
print("==> Inserting notifications...");
dbHandle.notifications.insertMany([
  {
    _id: ObjectId(),
    userId: userAdminId,
    type: 'ORDER_STATUS',
    title: 'Đơn hàng mới',
    message: 'Đơn ' + orderPendingNumber + ' vừa được tạo, tổng 15.930.000đ.',
    payload: { orderId: orderPendingId.toString(), orderNumber: orderPendingNumber },
    isRead: false,
    createdAt: now,
  },
  {
    _id: ObjectId(),
    userId: userWarehouseId,
    type: 'LOW_STOCK',
    title: 'Cảnh báo tồn kho thấp',
    message: 'Sản phẩm OUT001 chỉ còn 3 sản phẩm.',
    payload: { productId: productIds['OUT001'].toString(), quantityOnHand: 3, threshold: 5 },
    isRead: false,
    createdAt: daysAgo(1),
  },
  {
    _id: ObjectId(),
    userId: userCustomer1Id,
    type: 'ORDER_STATUS',
    title: 'Đơn hàng đã giao',
    message: 'Đơn ' + orderDeliveredNumber + ' đã được giao thành công. Cảm ơn quý khách!',
    payload: { orderId: orderDeliveredId.toString(), orderNumber: orderDeliveredNumber },
    isRead: true,
    createdAt: daysAgo(7),
  },
]);

// =============================================================================
// 11. RECREATE INDEXES (theo database/indexes/*.js) — để chắc chắn query đúng
// =============================================================================
print("==> Recreating indexes...");
dbHandle.users.createIndex({ email: 1 }, { unique: true, name: 'email_unique' });
dbHandle.users.createIndex({ role: 1 }, { name: 'role_idx' });

dbHandle.categories.createIndex({ slug: 1 }, { unique: true, name: 'slug_unique' });
dbHandle.categories.createIndex({ environment: 1, parentId: 1 }, { name: 'env_parent_idx' });

dbHandle.materials.createIndex({ name: 1 }, { unique: true, name: 'name_unique' });
dbHandle.materials.createIndex({ code: 1 }, { unique: true, name: 'code_unique' });

dbHandle.products.createIndex({ sku: 1 }, { unique: true, name: 'sku_unique' });
dbHandle.products.createIndex({ slug: 1 }, { unique: true, name: 'slug_unique' });
dbHandle.products.createIndex(
  { categoryId: 1, environment: 1, status: 1 },
  { name: 'category_env_status_idx' }
);
dbHandle.products.createIndex(
  { environment: 1, room: 1, materialIds: 1, price: 1 },
  { name: 'env_room_material_price_idx' }
);
dbHandle.products.createIndex({ price: 1 }, { name: 'price_asc' });
dbHandle.products.createIndex({ status: 1 }, { name: 'status_idx' });
dbHandle.products.createIndex({ createdAt: -1 }, { name: 'createdAt_desc' });
dbHandle.products.createIndex({ ratingAverage: -1 }, { name: 'ratingAverage_desc' });
dbHandle.products.createIndex(
  { name: 'text', description: 'text' },
  { name: 'text_search_idx', weights: { name: 3, description: 1 }, default_language: 'none' }
);

dbHandle.inventories.createIndex({ productId: 1 }, { unique: true, name: 'productId_unique' });
dbHandle.inventories.createIndex({ quantityOnHand: 1 }, { name: 'quantityOnHand_idx' });

dbHandle.carts.createIndex({ userId: 1 }, { unique: true, name: 'userId_unique' });

dbHandle.orders.createIndex({ orderNumber: 1 }, { unique: true, name: 'orderNumber_unique' });
dbHandle.orders.createIndex({ customerId: 1, createdAt: -1 }, { name: 'customer_orders_idx' });
dbHandle.orders.createIndex({ status: 1 }, { name: 'status_idx' });
dbHandle.orders.createIndex({ createdAt: -1 }, { name: 'createdAt_desc' });

dbHandle.payments.createIndex({ orderId: 1 }, { name: 'orderId_idx' });

dbHandle.promotions.createIndex({ code: 1 }, { unique: true, name: 'code_unique' });

dbHandle.reviews.createIndex({ productId: 1, createdAt: -1 }, { name: 'productId_createdAt_idx' });
dbHandle.reviews.createIndex(
  { userId: 1, productId: 1, orderId: 1 },
  { unique: true, name: 'user_product_order_unique_idx' }
);
dbHandle.reviews.createIndex({ status: 1 }, { name: 'status_idx' });

dbHandle.notifications.createIndex(
  { userId: 1, isRead: 1, createdAt: -1 },
  { name: 'userId_isRead_createdAt_idx' }
);

// =============================================================================
// SUMMARY
// =============================================================================
print('');
print('==> WOODFURNI seed completed.');
print('    users:        ' + dbHandle.users.countDocuments({}));
print('    categories:   ' + dbHandle.categories.countDocuments({}));
print('    materials:    ' + dbHandle.materials.countDocuments({}));
print('    products:     ' + dbHandle.products.countDocuments({}));
print('    inventories:  ' + dbHandle.inventories.countDocuments({}));
print('    orders:       ' + dbHandle.orders.countDocuments({}) +
      ' (PENDING: ' + dbHandle.orders.countDocuments({ status: 'PENDING' }) +
      ', SHIPPING: ' + dbHandle.orders.countDocuments({ status: 'SHIPPING' }) +
      ', DELIVERED: ' + dbHandle.orders.countDocuments({ status: 'DELIVERED' }) + ')');
print('    payments:     ' + dbHandle.payments.countDocuments({}));
print('    promotions:   ' + dbHandle.promotions.countDocuments({}));
print('    reviews:      ' + dbHandle.reviews.countDocuments({}));
print('    notifications:' + dbHandle.notifications.countDocuments({}));
print('');
print('Demo accounts (email / password):');
print('  ADMIN     admin@woodfurni.vn     / Admin@123');
print('  SALES     sales@woodfurni.vn     / Sales@123');
print('  WAREHOUSE warehouse@woodfurni.vn / Warehouse@123');
print('  CONTENT   content@woodfurni.vn   / Content@123');
print('  CUSTOMER  customer1@woodfurni.vn / Customer1@123');
print('  CUSTOMER  customer2@woodfurni.vn / Customer2@123');
