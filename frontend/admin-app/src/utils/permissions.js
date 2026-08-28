/**
 * Permission helpers — small wrappers around the user's role so components
 * can decide which buttons to render.
 *
 * The backend enforces everything via @PreAuthorize. These predicates
 * are for UX only (hide forbidden buttons, etc.) — they don't replace the
 * real check.
 */

export function can(role, action) {
    if (!role) return false;
    const matrix = {
        // PRODUCTS
        'products:read':     ['ADMIN', 'CONTENT', 'SALES', 'WAREHOUSE'],
        'products:create':   ['ADMIN', 'CONTENT'],
        'products:update':   ['ADMIN', 'CONTENT'],
        'products:delete':   ['ADMIN'],
        'products:publish':  ['ADMIN', 'CONTENT'],

        // CATEGORIES
        'categories:read':   ['ADMIN', 'CONTENT', 'SALES', 'WAREHOUSE'],
        'categories:create': ['ADMIN', 'CONTENT'],
        'categories:update': ['ADMIN', 'CONTENT'],
        'categories:delete': ['ADMIN'],

        // INVENTORY
        'inventory:read':    ['ADMIN', 'WAREHOUSE'],
        'inventory:adjust':  ['ADMIN', 'WAREHOUSE'],

        // ORDERS — SALES/WAREHOUSE/ADMIN can change status; everyone
        // staff can read.
        'orders:read':       ['ADMIN', 'SALES', 'WAREHOUSE'],
        'orders:updateStatus': ['ADMIN', 'SALES', 'WAREHOUSE'],
        'orders:sendToWarehouse': ['ADMIN', 'SALES'],
        'orders:markPrepared': ['ADMIN', 'WAREHOUSE'],
        'orders:cancel':     ['ADMIN', 'SALES'],
        'orders:receiveReturn': ['ADMIN', 'SALES'],
        'orders:forceCancelPromo': ['ADMIN'],

        // CUSTOMERS — ADMIN/SALES can browse customers and their orders.
        'customers:read':    ['ADMIN', 'SALES'],

        // PROMOTIONS — ADMIN only.
        'promotions:read':   ['ADMIN'],
        'promotions:write':  ['ADMIN'],

        // REVIEWS — ADMIN/CONTENT can moderate (hide/show).
        'reviews:read':      ['ADMIN', 'CONTENT'],
        'reviews:moderate':  ['ADMIN', 'CONTENT'],
    };
    const allowed = matrix[action];
    if (!allowed) return false;
    return allowed.includes(role);
}
