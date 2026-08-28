/**
 * WOODFURNI — Sample Data Seed (Node.js alternative)
 * =============================================================================
 * Tương đương với seed.js (mongosh) nhưng chạy bằng Node.js + driver mongodb chính thức.
 * Ưu điểm: hash password bằng BCrypt thực sự (cost 10 = Spring Security default),
 *          không phụ thuộc mongosh, dễ debug.
 *
 * Cài đặt:
 *   cd database/sample-data
 *   npm install        # lần đầu
 *   MONGODB_URI="mongodb://localhost:27017/woodfurni" node seed.node.js
 *
 * Hoặc trong docker compose stack:
 *   docker compose exec -T mongo sh -c "node -e 'process.exit(0)'"   # sanity check
 *   docker compose run --rm -e MONGODB_URI=mongodb://mongo:27017/woodfurni \
 *       --entrypoint "node /seed/seed.node.js" backend
 *
 *   Lưu ý: backend image dùng JRE, không có node. Cách khuyến nghị là copy
 *   script này vào mongo container hoặc chạy trực tiếp trên host Node 18+:
 *     npm install
 *     MONGODB_URI=mongodb://localhost:27017/woodfurni node seed.node.js
 *
 * Idempotent: xoá toàn bộ collections nghiệp vụ rồi insert lại.
 */

'use strict';

const { MongoClient, ObjectId, Decimal128 } = require('mongodb');
const bcrypt = require('bcrypt');

const MONGODB_URI = process.env.MONGODB_URI || 'mongodb://localhost:27017';
const DB_NAME = process.env.MONGODB_DATABASE || 'woodfurni';
const BCRYPT_COST = 10;

// ----- Demo accounts plaintext -----------------------------------------------
const DEMO_USERS = [
  { email: 'admin@woodfurni.vn',     password: 'Admin@123',     role: 'ADMIN',     fullName: 'Nguyễn Văn Admin' },
  { email: 'sales@woodfurni.vn',     password: 'Sales@123',     role: 'SALES',     fullName: 'Trần Thị Sales' },
  { email: 'warehouse@woodfurni.vn', password: 'Warehouse@123', role: 'WAREHOUSE', fullName: 'Lê Văn Kho' },
  { email: 'content@woodfurni.vn',   password: 'Content@123',   role: 'CONTENT',   fullName: 'Phạm Thị Content' },
  { email: 'customer1@woodfurni.vn', password: 'Customer1@123', role: 'CUSTOMER',  fullName: 'Hoàng Minh Khách' },
  { email: 'customer2@woodfurni.vn', password: 'Customer2@123', role: 'CUSTOMER',  fullName: 'Đỗ Thị Mua' },
];

// ----- Helpers ---------------------------------------------------------------
const now = new Date();
const daysAgo = (n) => new Date(now.getTime() - n * 24 * 60 * 60 * 1000);
const daysFromNow = (n) => new Date(now.getTime() + n * 24 * 60 * 60 * 1000);
const D = (v) => Decimal128.fromString(String(v));
const newId = () => new ObjectId();
const idMap = {};
const pid = (sku) => (idMap[sku] = idMap[sku] || newId());

function formatDateYMD(d) {
  return d.toISOString().slice(0, 10).replace(/-/g, '');
}

// =============================================================================
// Seed payload builders
// =============================================================================
function buildCategories() {
  const ids = {
    livingRoom: newId(),
    bedroom: newId(),
    diningRoom: newId(),
    office: newId(),
    garden: newId(),
  };
  const rows = [
    { id: ids.livingRoom, name: 'Living Room',   slug: 'living-room',   parent: null, order: 1 },
    { id: ids.bedroom,    name: 'Bedroom',       slug: 'bedroom',       parent: null, order: 2 },
    { id: ids.diningRoom, name: 'Dining Room',   slug: 'dining-room',   parent: null, order: 3 },
    { id: ids.office,     name: 'Office',        slug: 'office',        parent: null, order: 4 },
    { id: ids.garden,     name: 'Garden',        slug: 'garden',        parent: null, order: 5 },
  ];
  return {
    ids,
    docs: rows.map((r) => ({
      _id: r.id,
      name: r.name,
      slug: r.slug,
      environment: 'GARDEN' in ids && r.id === ids.garden ? 'OUTDOOR' : 'INDOOR',
      parentId: r.parent,
      order: r.order,
      status: 'ACTIVE',
      createdAt: daysAgo(30),
      updatedAt: daysAgo(30),
    })),
  };
}

function buildMaterials() {
  const ids = {
    oak: newId(), walnut: newId(), pine: newId(), acacia: newId(), teak: newId(),
  };
  const docs = [
    { _id: ids.oak,    name: 'Oak',    code: 'OAK',    description: 'Gỗ Sồi — cứng, bền, vân đẹp, phù hợp nội thất cao cấp.' },
    { _id: ids.walnut, name: 'Walnut', code: 'WALNUT', description: 'Gỗ Óc Chó — màu nâu socola sang trọng, thường dùng cho nội thất hiện đại.' },
    { _id: ids.pine,   name: 'Pine',   code: 'PINE',   description: 'Gỗ Thông — nhẹ, giá phải chăng, thân thiện phong cách Bắc Âu.' },
    { _id: ids.acacia, name: 'Acacia', code: 'ACACIA', description: 'Gỗ Tràm — chịu nước tốt, phù hợp đồ ngoại thất và sân vườn.' },
    { _id: ids.teak,   name: 'Teak',   code: 'TEAK',   description: 'Gỗ Tếch — chịu thời tiết khắc nghiệt, lý tưởng cho ngoài trời.' },
  ].map((m) => ({ ...m, createdAt: daysAgo(30), updatedAt: daysAgo(30) }));
  return { ids, docs };
}

function buildProducts(catIds, matIds) {
  // 10 SKU mẫu (IN001-IN005, OUT001-OUT005) + 5 tự tạo (EX001-EX005)
  const specs = [
    { sku: 'IN001', name: 'Sofa Gỗ Sồi 3 Chỗ',                slug: 'sofa-go-soi-3-cho',                  cat: catIds.livingRoom, mat: [matIds.oak],    env: 'INDOOR',  room: 'LIVING_ROOM', price: '28500000', sale: '25500000', warranty: '24 tháng', desc: 'Sofa 3 chỗ ngồi làm từ gỗ Sồi tự nhiên nhập khẩu, khung chắc chắn, vân gỗ đẹp. Kèm đệm mousse D40 bọc vải nỉ cao cấp. Phù hợp phòng khách hiện đại.', color: 'Nâu tự nhiên', finish: 'Lacquer bóng mờ', rating: 4.8, count: 12, dims: { width: 220, height: 85, depth: 90 }, weight: 65 },
    { sku: 'IN002', name: 'Giường Ngủ Gỗ Óc Chó 1m8',            slug: 'giuong-ngu-go-oc-cho-1m8',            cat: catIds.bedroom,    mat: [matIds.walnut], env: 'INDOOR',  room: 'BEDROOM',    price: '32500000', sale: null,       warranty: '36 tháng', desc: 'Giường ngủ đôi 1m8 gỗ Óc Chó nguyên khối, đầu giường bọc da công nghiệp. Thiết kế tối giản, sang trọng cho phòng ngủ master.', color: 'Nâu socola', finish: 'Oliu tự nhiên', rating: 4.9, count: 8, dims: { width: 180, height: 110, depth: 210 }, weight: 95 },
    { sku: 'IN003', name: 'Bàn Ăn Gỗ Thông 6 Ghế',              slug: 'ban-an-go-thong-6-ghe',              cat: catIds.diningRoom, mat: [matIds.pine],   env: 'INDOOR',  room: 'DINING_ROOM', price: '12500000', sale: '11250000', warranty: '12 tháng', desc: 'Bộ bàn ăn 6 ghế làm từ gỗ Thông tự nhiên, phong cách Scandinavian. Mặt bàn phủ lớp chống thấm, dễ vệ sinh.', color: 'Nâu nhạt', finish: 'Sơn PU mờ', rating: 4.5, count: 15, dims: { width: 180, height: 75, depth: 90 }, weight: 45 },
    { sku: 'IN004', name: 'Bàn Làm Việc Gỗ Sồi Có Ngăn Kéo',    slug: 'ban-lam-viec-go-soi-co-ngan-keo',    cat: catIds.office,     mat: [matIds.oak],    env: 'INDOOR',  room: 'OFFICE',    price: '9800000',  sale: null,       warranty: '24 tháng', desc: 'Bàn làm việc gỗ Sồi tự nhiên, 3 ngăn kéo ray âm, mặt bàn rộng rãi cho laptop + tài liệu. Lắp ráp đơn giản.', color: 'Nâu tự nhiên', finish: 'Lacquer bóng mờ', rating: 4.3, count: 6, dims: { width: 140, height: 75, depth: 60 }, weight: 38 },
    { sku: 'IN005', name: 'Tủ Quần Áo 3 Cánh Gỗ Óc Chó',        slug: 'tu-quan-ao-3-canh-go-oc-cho',        cat: catIds.bedroom,    mat: [matIds.walnut], env: 'INDOOR',  room: 'BEDROOM',    price: '28000000', sale: '24500000', warranty: '36 tháng', desc: 'Tủ quần áo 3 cánh gỗ Óc Chó, bên trong chia ngăn thông minh: kệ treo, ngăn gấp, ngăn kéo. Cửa gương soi toàn thân.', color: 'Nâu socola', finish: 'Oliu tự nhiên', rating: 4.7, count: 9, dims: { width: 180, height: 220, depth: 60 }, weight: 120 },
    { sku: 'OUT001', name: 'Bộ Bàn Ghế Sân Vườn Gỗ Teak',        slug: 'bo-ban-ghe-san-vuon-go-teak',        cat: catIds.garden,     mat: [matIds.teak],   env: 'OUTDOOR', room: 'GARDEN',    price: '22500000', sale: null,       warranty: '60 tháng', desc: 'Bộ bàn ghế 4 chỗ ngoài trời gỗ Teak nhập khẩu, chịu mưa nắng tuyệt vời. Bao gồm 1 bàn chữ nhật + 4 ghế có đệm.', color: 'Vàng nâu tự nhiên', finish: 'Teak oil', rating: 4.9, count: 21, dims: { width: 200, height: 75, depth: 100 }, weight: 55 },
    { sku: 'OUT002', name: 'Ghế Tắm Nắng Gỗ Teak Có Bánh Xe',   slug: 'ghe-tam-nang-go-teak-co-banh-xe',   cat: catIds.garden,     mat: [matIds.teak],   env: 'OUTDOOR', room: 'GARDEN',    price: '8500000',  sale: '7350000',  warranty: '36 tháng', desc: 'Ghế tắm nắng gỗ Teak, điều chỉnh 5 nấc ngả, bánh xe di chuyển. Hoàn hảo cho hồ bơi hoặc sân vườn.', color: 'Vàng nâu', finish: 'Teak oil', rating: 4.6, count: 11, dims: { width: 70, height: 95, depth: 180 }, weight: 28 },
    { sku: 'OUT003', name: 'Xích Đu Gỗ Tràm Sân Vườn',          slug: 'xich-du-go-tram-san-vuon',          cat: catIds.garden,     mat: [matIds.acacia], env: 'OUTDOOR', room: 'GARDEN',    price: '6500000',  sale: null,       warranty: '24 tháng', desc: 'Xích đu gỗ Tràm dành cho sân vườn, khung chịu lực 200kg, dây xích thép không gỉ. Đã bao gồm đệm ngồi.', color: 'Nâu ấm', finish: 'Dầu lau tự nhiên', rating: 4.4, count: 7, dims: { width: 150, height: 180, depth: 120 }, weight: 45 },
    { sku: 'OUT004', name: 'Chậu Cây Gỗ Teak Vuông Lớn',         slug: 'chau-cay-go-teak-vuong-lon',         cat: catIds.garden,     mat: [matIds.teak],   env: 'OUTDOOR', room: 'GARDEN',    price: '2900000',  sale: null,       warranty: '24 tháng', desc: 'Chậu cây vuông gỗ Teak cao cấp, lót nilon chống thấm bên trong. Phù hợp trang trí sảnh, ban công, sân vườn.', color: 'Vàng nâu', finish: 'Teak oil', rating: 4.2, count: 4, dims: { width: 50, height: 50, depth: 50 }, weight: 15 },
    { sku: 'OUT005', name: 'Bàn Ăn Ngoài Trời Gỗ Acacia 6 Ghế',  slug: 'ban-an-ngoai-troi-go-acacia-6-ghe',  cat: catIds.garden,     mat: [matIds.acacia], env: 'OUTDOOR', room: 'GARDEN',    price: '15500000', sale: '13500000', warranty: '36 tháng', desc: 'Bộ bàn ăn ngoài trời 6 ghế gỗ Acacia, chịu nước và tia UV. Phù hợp cho sân vườn, sân thượng, quán cafe.', color: 'Nâu ấm', finish: 'Dầu lau tự nhiên', rating: 4.7, count: 13, dims: { width: 200, height: 75, depth: 100 }, weight: 50 },
    { sku: 'EX001', name: 'Bàn Trà Gỗ Sồi Hình Chữ Nhật',        slug: 'ban-tra-go-soi-hinh-chu-nhat',        cat: catIds.livingRoom, mat: [matIds.oak],    env: 'INDOOR',  room: 'LIVING_ROOM', price: '6200000',  sale: null,       warranty: '24 tháng', desc: 'Bàn trà gỗ Sồi hình chữ nhật, thiết kế tối giản Bắc Âu, ngăn kéo âm bên hông. Phù hợp phòng khách nhỏ.', color: 'Nâu tự nhiên', finish: 'Lacquer bóng mờ', rating: 4.4, count: 5, dims: { width: 120, height: 45, depth: 60 }, weight: 25 },
    { sku: 'EX002', name: 'Ghế Văn Phòng Gỗ Óc Chó Bọc Da',      slug: 'ghe-van-phong-go-oc-cho-boc-da',      cat: catIds.office,     mat: [matIds.walnut], env: 'INDOOR',  room: 'OFFICE',    price: '4500000',  sale: '3950000',  warranty: '12 tháng', desc: 'Ghế xoay văn phòng khung gỗ Óc Chó, đệm ngồi bọc da PU cao cấp, tay vịn da, chân thép mạ crom.', color: 'Nâu socola + đen', finish: 'Da PU + gỗ Óc Chó', rating: 4.1, count: 3, dims: { width: 65, height: 110, depth: 65 }, weight: 18 },
    { sku: 'EX003', name: 'Kệ Sách Gỗ Thông 5 Tầng',             slug: 'ke-sach-go-thong-5-tang',             cat: catIds.office,     mat: [matIds.pine],   env: 'INDOOR',  room: 'OFFICE',    price: '3200000',  sale: null,       warranty: '12 tháng', desc: 'Kệ sách 5 tầng gỗ Thông tự nhiên, chịu lực tốt, phù hợp phòng đọc sách, văn phòng nhỏ.', color: 'Nâu nhạt', finish: 'Sơn PU mờ', rating: 4.0, count: 2, dims: { width: 80, height: 180, depth: 30 }, weight: 22 },
    { sku: 'EX004', name: 'Bàn Ăn Tròn Gỗ Sồi 4 Ghế',            slug: 'ban-an-tron-go-soi-4-ghe',            cat: catIds.diningRoom, mat: [matIds.oak],    env: 'INDOOR',  room: 'DINING_ROOM', price: '16500000', sale: null,       warranty: '24 tháng', desc: 'Bàn ăn tròn 4 ghế gỗ Sồi nguyên khối, phù hợp phòng ăn gia đình nhỏ. Mặt bàn đường kính 120cm.', color: 'Nâu tự nhiên', finish: 'Lacquer bóng mờ', rating: 4.6, count: 8, dims: { width: 120, height: 75, depth: 120 }, weight: 40 },
    { sku: 'EX005', name: 'Bộ Sofa Gỗ Teak Ngoài Trời 4 Chỗ',    slug: 'bo-sofa-go-teak-ngoai-troi-4-cho',    cat: catIds.garden,     mat: [matIds.teak],   env: 'OUTDOOR', room: 'GARDEN',    price: '36500000', sale: '32500000', warranty: '60 tháng', desc: 'Bộ sofa góc 4 chỗ ngoài trời gỗ Teak, kèm đệm mousse chống thấm nước. Hoàn hảo cho patio hoặc sân thượng lớn.', color: 'Vàng nâu', finish: 'Teak oil', rating: 4.8, count: 16, dims: { width: 240, height: 85, depth: 90 }, weight: 75 },
  ];

  const docs = specs.map((s) => ({
    _id: pid(s.sku),
    sku: s.sku,
    name: s.name,
    slug: s.slug,
    categoryId: s.cat,
    materialIds: s.mat,
    environment: s.env,
    room: s.room,
    dimensions: s.dims,
    weight: s.weight,
    color: s.color,
    finish: s.finish,
    price: D(s.price),
    salePrice: s.sale ? D(s.sale) : null,
    images: [`https://placehold.co/800x600/8B6F47/FFF?text=${encodeURIComponent(s.sku)}`],
    description: s.desc,
    warranty: s.warranty,
    status: 'ACTIVE',
    ratingAverage: s.rating,
    ratingCount: s.count,
    createdAt: daysAgo(20),
    updatedAt: daysAgo(1),
  }));

  return docs;
}

function buildInventories(products) {
  // Đảm bảo OUT001, EX005 tồn kho thấp để test low-stock
  const qtyFor = (sku) => {
    const map = {
      IN001: 12, IN002: 8, IN003: 25, IN004: 18, IN005: 6,
      OUT001: 3, OUT002: 15, OUT003: 10, OUT004: 30, OUT005: 14,
      EX001: 20, EX002: 22, EX003: 18, EX004: 12, EX005: 4,
    };
    return map[sku] != null ? map[sku] : 20;
  };

  return products.map((p) => ({
    _id: newId(),
    productId: p._id,
    quantityOnHand: qtyFor(p.sku),
    quantityReserved: 0,
    lowStockThreshold: 5,
    updatedAt: new Date(),
  }));
}

function buildPromotions() {
  return [
    {
      _id: newId(),
      code: 'WELCOME10',
      type: 'PERCENTAGE',
      value: D('10'),
      minOrderAmount: D('5000000'),
      maxDiscountAmount: D('2000000'),
      startDate: daysAgo(30),
      endDate: daysFromNow(60),
      usageLimit: 100,
      usedCount: 1,
      status: 'ACTIVE',
    },
    {
      _id: newId(),
      code: 'SUMMER2TR',
      type: 'FIXED_AMOUNT',
      value: D('2000000'),
      minOrderAmount: D('20000000'),
      maxDiscountAmount: null,
      startDate: daysAgo(15),
      endDate: daysFromNow(45),
      usageLimit: 50,
      usedCount: 0,
      status: 'ACTIVE',
    },
  ];
}

function buildUsers(usersHashes) {
  // Map mỗi user.demo - tạo 1 address mặc định cho customer1 + customer2
  const customer1Id = newId();
  const customer2Id = newId();
  const adminId = newId();
  const salesId = newId();
  const warehouseId = newId();
  const contentId = newId();

  const findHash = (email) => usersHashes.find((h) => h.email === email).hash;

  const users = [
    { _id: adminId,     email: 'admin@woodfurni.vn',     passwordHash: findHash('admin@woodfurni.vn'),     fullName: 'Nguyễn Văn Admin',     phone: '0900000001', role: 'ADMIN',     addresses: [] },
    { _id: salesId,     email: 'sales@woodfurni.vn',     passwordHash: findHash('sales@woodfurni.vn'),     fullName: 'Trần Thị Sales',       phone: '0900000002', role: 'SALES',     addresses: [] },
    { _id: warehouseId, email: 'warehouse@woodfurni.vn', passwordHash: findHash('warehouse@woodfurni.vn'), fullName: 'Lê Văn Kho',           phone: '0900000003', role: 'WAREHOUSE', addresses: [] },
    { _id: contentId,   email: 'content@woodfurni.vn',   passwordHash: findHash('content@woodfurni.vn'),   fullName: 'Phạm Thị Content',     phone: '0900000004', role: 'CONTENT',   addresses: [] },
    {
      _id: customer1Id, email: 'customer1@woodfurni.vn', passwordHash: findHash('customer1@woodfurni.vn'), fullName: 'Hoàng Minh Khách',     phone: '0901234567', role: 'CUSTOMER',
      addresses: [
        { id: 'addr-c1-default', label: 'Nhà riêng',  line1: '12 Nguyễn Huệ',     ward: 'Phường Bến Nghé',    district: 'Quận 1',        city: 'TP. Hồ Chí Minh', phone: '0901234567', isDefault: true },
        { id: 'addr-c1-office',  label: 'Văn phòng',  line1: 'Tầng 5, 99 Lê Lợi', ward: 'Phường Bến Thành',  district: 'Quận 1',        city: 'TP. Hồ Chí Minh', phone: '0901234567', isDefault: false },
      ],
    },
    {
      _id: customer2Id, email: 'customer2@woodfurni.vn', passwordHash: findHash('customer2@woodfurni.vn'), fullName: 'Đỗ Thị Mua',          phone: '0909876543', role: 'CUSTOMER',
      addresses: [
        { id: 'addr-c2-default', label: 'Nhà riêng',  line1: '45 Trần Phú',       ward: 'Phường Điện Biên',  district: 'Quận Ba Đình',  city: 'Hà Nội',           phone: '0909876543', isDefault: true },
      ],
    },
  ].map((u) => ({ ...u, status: 'ACTIVE', createdAt: daysAgo(20), updatedAt: daysAgo(20) }));

  return { users, ids: { adminId, salesId, warehouseId, contentId, customer1Id, customer2Id } };
}

function buildOrdersAndReviews(products, userIds) {
  // Order 1: DELIVERED - customer1 mua Sofa Oak + Bàn Ăn Pine
  const orderDeliveredId = newId();
  const orderDeliveredItems = [
    { productId: idMap['IN001'], productName: 'Sofa Gỗ Sồi 3 Chỗ',             sku: 'IN001', unitPrice: D('25500000'), quantity: 1, subtotal: D('25500000') },
    { productId: idMap['IN003'], productName: 'Bàn Ăn Gỗ Thông 6 Ghế',          sku: 'IN003', unitPrice: D('11250000'), quantity: 1, subtotal: D('11250000') },
  ];
  const orderDelivered = {
    _id: orderDeliveredId,
    orderNumber: `ORD-${formatDateYMD(daysAgo(15))}-0001`,
    customerId: userIds.customer1Id,
    items: orderDeliveredItems,
    shippingAddress: { label: 'Nhà riêng', line1: '12 Nguyễn Huệ', ward: 'Phường Bến Nghé', district: 'Quận 1', city: 'TP. Hồ Chí Minh', phone: '0901234567' },
    promotionCode: 'SUMMER2TR',
    discountAmount: D('2000000'),
    subtotalAmount: D('36750000'),
    totalAmount: D('34750000'),
    status: 'DELIVERED',
    paymentStatus: 'PAID',
    statusHistory: [
      { status: 'PENDING',    changedAt: daysAgo(15), changedBy: userIds.customer1Id.toString() },
      { status: 'CONFIRMED',  changedAt: daysAgo(14), changedBy: userIds.salesId.toString() },
      { status: 'PROCESSING', changedAt: daysAgo(13), changedBy: userIds.salesId.toString() },
      { status: 'SHIPPING',   changedAt: daysAgo(10), changedBy: userIds.warehouseId.toString() },
      { status: 'DELIVERED',  changedAt: daysAgo(7),  changedBy: userIds.warehouseId.toString() },
    ],
    createdAt: daysAgo(15),
    updatedAt: daysAgo(7),
  };

  const reviews = [
    { _id: newId(), productId: idMap['IN001'], userId: userIds.customer1Id, orderId: orderDeliveredId, rating: 5, comment: 'Sofa rất đẹp, gỗ Sồi chắc chắn, đệm ngồi êm. Giao hàng nhanh, nhân viên lắp đặt chuyên nghiệp.', status: 'PUBLISHED', createdAt: daysAgo(5) },
    { _id: newId(), productId: idMap['IN003'], userId: userIds.customer1Id, orderId: orderDeliveredId, rating: 4, comment: 'Bàn ăn đúng mô tả, gỗ Thông nhẹ và dễ vệ sinh. Trừ 1 sao vì giao hàng trễ 1 ngày.',        status: 'PUBLISHED', createdAt: daysAgo(4) },
  ];

  // Order 2: SHIPPING - customer2 mua Sofa Teak + 2 Ghế Tắm Nắng
  const orderShippingId = newId();
  const orderShippingItems = [
    { productId: idMap['OUT001'], productName: 'Bộ Bàn Ghế Sân Vườn Gỗ Teak',    sku: 'OUT001', unitPrice: D('22500000'), quantity: 1, subtotal: D('22500000') },
    { productId: idMap['OUT002'], productName: 'Ghế Tắm Nắng Gỗ Teak Có Bánh Xe', sku: 'OUT002', unitPrice: D('7350000'),  quantity: 2, subtotal: D('14700000') },
  ];
  const orderShipping = {
    _id: orderShippingId,
    orderNumber: `ORD-${formatDateYMD(daysAgo(3))}-0001`,
    customerId: userIds.customer2Id,
    items: orderShippingItems,
    shippingAddress: { label: 'Nhà riêng', line1: '45 Trần Phú', ward: 'Phường Điện Biên', district: 'Quận Ba Đình', city: 'Hà Nội', phone: '0909876543' },
    promotionCode: null,
    discountAmount: D('0'),
    subtotalAmount: D('37200000'),
    totalAmount: D('37200000'),
    status: 'SHIPPING',
    paymentStatus: 'PAID',
    statusHistory: [
      { status: 'PENDING',    changedAt: daysAgo(3), changedBy: userIds.customer2Id.toString() },
      { status: 'CONFIRMED',  changedAt: daysAgo(2), changedBy: userIds.salesId.toString() },
      { status: 'PROCESSING', changedAt: daysAgo(1), changedBy: userIds.warehouseId.toString() },
      { status: 'SHIPPING',   changedAt: daysAgo(0), changedBy: userIds.warehouseId.toString() },
    ],
    createdAt: daysAgo(3),
    updatedAt: daysAgo(0),
  };

  // Order 3: PENDING - customer1 mua Bàn Làm Việc + 2 Ghế Văn Phòng
  const orderPendingId = newId();
  const orderPendingItems = [
    { productId: idMap['IN004'], productName: 'Bàn Làm Việc Gỗ Sồi Có Ngăn Kéo', sku: 'IN004', unitPrice: D('9800000'), quantity: 1, subtotal: D('9800000') },
    { productId: idMap['EX002'], productName: 'Ghế Văn Phòng Gỗ Óc Chó Bọc Da',   sku: 'EX002', unitPrice: D('3950000'), quantity: 2, subtotal: D('7900000') },
  ];
  const orderPending = {
    _id: orderPendingId,
    orderNumber: `ORD-${formatDateYMD(now)}-0001`,
    customerId: userIds.customer1Id,
    items: orderPendingItems,
    shippingAddress: { label: 'Văn phòng', line1: 'Tầng 5, 99 Lê Lợi', ward: 'Phường Bến Thành', district: 'Quận 1', city: 'TP. Hồ Chí Minh', phone: '0901234567' },
    promotionCode: 'WELCOME10',
    discountAmount: D('1770000'),
    subtotalAmount: D('17700000'),
    totalAmount: D('15930000'),
    status: 'PENDING',
    paymentStatus: 'UNPAID',
    statusHistory: [
      { status: 'PENDING', changedAt: now, changedBy: userIds.customer1Id.toString() },
    ],
    createdAt: now,
    updatedAt: now,
  };

  const orders = [orderDelivered, orderShipping, orderPending];

  const payments = [
    { _id: newId(), orderId: orderDeliveredId, method: 'SANDBOX_CARD',   amount: D('34750000'), status: 'SUCCESS', transactionRef: `TX-${orderDelivered.orderNumber}-CARD`,   paidAt: daysAgo(14), createdAt: daysAgo(15) },
    { _id: newId(), orderId: orderShippingId,  method: 'SANDBOX_WALLET', amount: D('37200000'), status: 'SUCCESS', transactionRef: `TX-${orderShipping.orderNumber}-WALLET`,  paidAt: daysAgo(2),  createdAt: daysAgo(3) },
    { _id: newId(), orderId: orderPendingId,   method: 'COD',            amount: D('15930000'), status: 'PENDING', transactionRef: null,                                     paidAt: null,        createdAt: now },
  ];

  // Inventory adjustments
  // DELIVERED (Order 1) → commit: quantityOnHand - qty (already used to compute qty above)
  // SHIPPING (Order 2) → quantityReserved += qty
  // PENDING (Order 3) → quantityReserved += qty
  const inventoryAdjustments = [
    { productId: idMap['IN001'], onHand: -1, reserved: 0 },
    { productId: idMap['IN003'], onHand: -1, reserved: 0 },
    { productId: idMap['OUT001'], onHand: 0, reserved: 1 },
    { productId: idMap['OUT002'], onHand: 0, reserved: 2 },
    { productId: idMap['IN004'], onHand: 0, reserved: 1 },
    { productId: idMap['EX002'], onHand: 0, reserved: 2 },
  ];

  return { orders, payments, reviews, inventoryAdjustments };
}

function buildNotifications(userIds, orders) {
  const [orderDelivered, orderShipping, orderPending] = orders;
  return [
    { _id: newId(), userId: userIds.adminId,     type: 'ORDER_STATUS', title: 'Đơn hàng mới',           message: `Đơn ${orderPending.orderNumber} vừa được tạo, tổng 15.930.000đ.`, payload: { orderId: orderPending._id.toString(),   orderNumber: orderPending.orderNumber },   isRead: false, createdAt: now },
    { _id: newId(), userId: userIds.warehouseId, type: 'LOW_STOCK',    title: 'Cảnh báo tồn kho thấp',  message: 'Sản phẩm OUT001 chỉ còn 3 sản phẩm.',                              payload: { productId: idMap['OUT001'].toString(), quantityOnHand: 3, threshold: 5 },         isRead: false, createdAt: daysAgo(1) },
    { _id: newId(), userId: userIds.customer1Id, type: 'ORDER_STATUS', title: 'Đơn hàng đã giao',        message: `Đơn ${orderDelivered.orderNumber} đã được giao thành công. Cảm ơn quý khách!`, payload: { orderId: orderDelivered._id.toString(), orderNumber: orderDelivered.orderNumber }, isRead: true,  createdAt: daysAgo(7) },
  ];
}

// =============================================================================
// Main
// =============================================================================
async function main() {
  const client = new MongoClient(MONGODB_URI);
  await client.connect();
  const db = client.db(DB_NAME);

  console.log('==> WOODFURNI seed starting...');
  console.log(`    MongoDB: ${MONGODB_URI}`);
  console.log(`    Database: ${DB_NAME}`);

  // 1. Hash passwords
  console.log('==> Hashing passwords (BCrypt cost=' + BCRYPT_COST + ')...');
  const usersHashes = await Promise.all(
    DEMO_USERS.map(async (u) => ({ email: u.email, hash: await bcrypt.hash(u.password, BCRYPT_COST) }))
  );

  // 2. Reset collections
  console.log('==> Resetting collections...');
  const collections = ['users', 'categories', 'materials', 'products', 'inventories', 'carts', 'orders', 'payments', 'promotions', 'reviews', 'notifications'];
  for (const c of collections) {
    try { await db.collection(c).drop(); } catch (e) { /* ignore */ }
  }

  // 3. Build & insert
  const { ids: catIds, docs: catDocs } = buildCategories();
  await db.collection('categories').insertMany(catDocs);
  console.log(`==> Inserted ${catDocs.length} categories`);

  const { ids: matIds, docs: matDocs } = buildMaterials();
  await db.collection('materials').insertMany(matDocs);
  console.log(`==> Inserted ${matDocs.length} materials`);

  const products = buildProducts(catIds, matIds);
  await db.collection('products').insertMany(products);
  console.log(`==> Inserted ${products.length} products`);

  const inventories = buildInventories(products);
  await db.collection('inventories').insertMany(inventories);
  console.log(`==> Inserted ${inventories.length} inventories`);

  const promotions = buildPromotions();
  await db.collection('promotions').insertMany(promotions);
  console.log(`==> Inserted ${promotions.length} promotions`);

  const { users, ids: userIds } = buildUsers(usersHashes);
  await db.collection('users').insertMany(users);
  console.log(`==> Inserted ${users.length} users`);

  const { orders, payments, reviews, inventoryAdjustments } = buildOrdersAndReviews(products, userIds);
  await db.collection('orders').insertMany(orders);
  console.log(`==> Inserted ${orders.length} orders`);
  await db.collection('payments').insertMany(payments);
  console.log(`==> Inserted ${payments.length} payments`);
  await db.collection('reviews').insertMany(reviews);
  console.log(`==> Inserted ${reviews.length} reviews`);

  // Apply inventory adjustments
  for (const adj of inventoryAdjustments) {
    const u = {};
    if (adj.onHand !== 0) { u.quantityOnHand = adj.onHand; }
    if (adj.reserved !== 0) { u.quantityReserved = adj.reserved; }
    if (Object.keys(u).length > 0) {
      await db.collection('inventories').updateOne({ productId: adj.productId }, { $inc: u, $set: { updatedAt: new Date() } });
    }
  }
  console.log(`==> Adjusted ${inventoryAdjustments.length} inventory entries for orders`);

  const notifications = buildNotifications(userIds, orders);
  await db.collection('notifications').insertMany(notifications);
  console.log(`==> Inserted ${notifications.length} notifications`);

  // 4. Recreate indexes
  console.log('==> Recreating indexes...');
  await Promise.all([
    db.collection('users').createIndex({ email: 1 }, { unique: true, name: 'email_unique' }),
    db.collection('users').createIndex({ role: 1 }, { name: 'role_idx' }),
    db.collection('categories').createIndex({ slug: 1 }, { unique: true, name: 'slug_unique' }),
    db.collection('categories').createIndex({ environment: 1, parentId: 1 }, { name: 'env_parent_idx' }),
    db.collection('materials').createIndex({ name: 1 }, { unique: true, name: 'name_unique' }),
    db.collection('materials').createIndex({ code: 1 }, { unique: true, name: 'code_unique' }),
    db.collection('products').createIndex({ sku: 1 }, { unique: true, name: 'sku_unique' }),
    db.collection('products').createIndex({ slug: 1 }, { unique: true, name: 'slug_unique' }),
    db.collection('products').createIndex({ categoryId: 1, environment: 1, status: 1 }, { name: 'category_env_status_idx' }),
    db.collection('products').createIndex({ environment: 1, room: 1, materialIds: 1, price: 1 }, { name: 'env_room_material_price_idx' }),
    db.collection('products').createIndex({ price: 1 }, { name: 'price_asc' }),
    db.collection('products').createIndex({ status: 1 }, { name: 'status_idx' }),
    db.collection('products').createIndex({ createdAt: -1 }, { name: 'createdAt_desc' }),
    db.collection('products').createIndex({ ratingAverage: -1 }, { name: 'ratingAverage_desc' }),
    db.collection('products').createIndex({ name: 'text', description: 'text' }, { name: 'text_search_idx', weights: { name: 3, description: 1 }, default_language: 'none' }),
    db.collection('inventories').createIndex({ productId: 1 }, { unique: true, name: 'productId_unique' }),
    db.collection('inventories').createIndex({ quantityOnHand: 1 }, { name: 'quantityOnHand_idx' }),
    db.collection('carts').createIndex({ userId: 1 }, { unique: true, name: 'userId_unique' }),
    db.collection('orders').createIndex({ orderNumber: 1 }, { unique: true, name: 'orderNumber_unique' }),
    db.collection('orders').createIndex({ customerId: 1, createdAt: -1 }, { name: 'customer_orders_idx' }),
    db.collection('orders').createIndex({ status: 1 }, { name: 'status_idx' }),
    db.collection('orders').createIndex({ createdAt: -1 }, { name: 'createdAt_desc' }),
    db.collection('payments').createIndex({ orderId: 1 }, { name: 'orderId_idx' }),
    db.collection('promotions').createIndex({ code: 1 }, { unique: true, name: 'code_unique' }),
    db.collection('reviews').createIndex({ productId: 1, createdAt: -1 }, { name: 'productId_createdAt_idx' }),
    db.collection('reviews').createIndex({ userId: 1, productId: 1, orderId: 1 }, { unique: true, name: 'user_product_order_unique_idx' }),
    db.collection('reviews').createIndex({ status: 1 }, { name: 'status_idx' }),
    db.collection('notifications').createIndex({ userId: 1, isRead: 1, createdAt: -1 }, { name: 'userId_isRead_createdAt_idx' }),
  ]);

  // 5. Summary
  console.log('');
  console.log('==> WOODFURNI seed completed.');
  console.log('');
  console.log('Demo accounts (email / password):');
  for (const u of DEMO_USERS) {
    console.log(`  ${u.role.padEnd(10)} ${u.email.padEnd(28)} / ${u.password}`);
  }

  await client.close();
}

main().catch((err) => {
  console.error('Seed failed:', err);
  process.exit(1);
});
