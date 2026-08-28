/**
 * Role metadata. Source of truth for:
 *   - which roles are allowed to log into the admin app at all (any
 *     non-CUSTOMER role)
 *   - sidebar menu filtering (which roles see which menu items)
 *
 * Real authorisation is enforced by backend @PreAuthorize on every
 * controller method. The role gates here are a UX nicety.
 */

export const ALL_ROLES = ['ADMIN', 'SALES', 'WAREHOUSE', 'CONTENT'];

/**
 * CUSTOMER is explicitly NOT in this list — see LoginPage which rejects
 * any account whose role === 'CUSTOMER' before it ever reaches the
 * dashboard.
 */
export const STAFF_ROLES = ['ADMIN', 'SALES', 'WAREHOUSE', 'CONTENT'];

export function isStaffRole(role) {
    return role !== 'CUSTOMER' && !!role;
}

/**
 * Sidebar menu definitions, in display order. Each item declares which
 * roles are allowed to see it.
 *
 * `path` is the React Router path. `roles: null` means "all staff roles".
 */
export const MENU = [
    { id: 'dashboard', label: 'Dashboard', path: '/', icon: '📊', roles: null },
    { id: 'products', label: 'Sản phẩm', path: '/products', icon: '🪑', roles: ['ADMIN', 'CONTENT'] },
    { id: 'categories', label: 'Danh mục', path: '/categories', icon: '📁', roles: ['ADMIN', 'CONTENT'] },
    { id: 'inventory', label: 'Kho hàng', path: '/inventory', icon: '📦', roles: ['ADMIN', 'WAREHOUSE'] },
    { id: 'orders', label: 'Đơn hàng', path: '/orders', icon: '🧾', roles: ['ADMIN', 'SALES'] },
    { id: 'prepare-orders', label: 'Chuẩn bị đơn', path: '/prepare-orders', icon: '📦', roles: ['ADMIN', 'WAREHOUSE'] },
    { id: 'customers', label: 'Khách hàng', path: '/customers', icon: '👥', roles: ['ADMIN', 'SALES'] },
    { id: 'promotions', label: 'Khuyến mãi', path: '/promotions', icon: '🎟️', roles: ['ADMIN'] },
    { id: 'reviews', label: 'Đánh giá', path: '/reviews', icon: '⭐', roles: ['ADMIN', 'CONTENT'] },
];

export function visibleMenu(role) {
    return MENU.filter((item) => item.roles === null || item.roles.includes(role));
}

export function roleLabel(role) {
    switch (role) {
        case 'ADMIN': return 'Quản trị viên';
        case 'SALES': return 'Nhân viên bán hàng';
        case 'WAREHOUSE': return 'Nhân viên kho';
        case 'CONTENT': return 'Biên tập viên';
        case 'CUSTOMER': return 'Khách hàng';
        default: return role;
    }
}