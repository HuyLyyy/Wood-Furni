const jwt = require('jsonwebtoken');

const ROLE_ADMINS = new Set(['SALES', 'ADMIN']);
const ROLE_WAREHOUSE = new Set(['WAREHOUSE', 'ADMIN']);

/**
 * Socket.IO authentication middleware.
 *
 * Expects: socket.handshake.auth.token = "<JWT_ACCESS_TOKEN>"
 *
 * The token is signed by Spring Boot's JwtProvider with the SAME HS256 secret
 * (JWT_SECRET env var on the gateway, jwt.secret property on the backend).
 *
 * On success: attaches socket.data = { userId, role, token }, calls next().
 * On failure: rejects with an Error — Socket.IO will fire 'connect_error' on
 * the client with this message.
 *
 * Payload contract (matches com.woodfurni.security.JwtProvider):
 *   {
 *     sub: "<userId>",
 *     userId: "<userId>",
 *     role: "CUSTOMER" | "SALES" | "WAREHOUSE" | "CONTENT" | "ADMIN",
 *     iat, exp
 *   }
 */
function socketAuthMiddleware(socket, next) {
    try {
        const token = socket.handshake.auth && socket.handshake.auth.token;

        if (!token || typeof token !== 'string') {
            return next(new Error('Authentication required: missing token'));
        }

        const secret = process.env.JWT_SECRET;
        if (!secret) {
            console.error('[socket-auth] JWT_SECRET not configured');
            return next(new Error('Server misconfigured: JWT_SECRET missing'));
        }

        let payload;
        try {
            payload = jwt.verify(token, secret);
        } catch (err) {
            return next(new Error(`Invalid or expired token: ${err.message}`));
        }

        const userId = payload.userId || payload.sub;
        const role = payload.role;

        if (!userId || !role) {
            return next(new Error('Invalid token payload: missing userId/role'));
        }

        socket.data.userId = String(userId);
        socket.data.role = String(role);

        next();
    } catch (err) {
        next(new Error(`Authentication failed: ${err.message}`));
    }
}

/**
 * Join role-based rooms:
 *   - SALES / ADMIN        → 'admins'
 *   - WAREHOUSE / ADMIN    → 'warehouse'
 *   - everyone             → 'user:<userId>' (so per-customer events work)
 */
function joinRoleRooms(socket) {
    const { userId, role } = socket.data;
    if (!userId || !role) return;

    if (ROLE_ADMINS.has(role)) {
        socket.join('admins');
        console.log(`[socket] userId=${userId} role=${role} joined 'admins'`);
    }
    if (ROLE_WAREHOUSE.has(role)) {
        socket.join('warehouse');
        console.log(`[socket] userId=${userId} role=${role} joined 'warehouse'`);
    }

    socket.join(`user:${userId}`);
}

module.exports = {
    socketAuthMiddleware,
    joinRoleRooms,
    ROLE_ADMINS,
    ROLE_WAREHOUSE,
};