const express = require('express');

const INTERNAL_SECRET_HEADER = 'x-internal-secret';

/**
 * Express middleware that protects /internal/* routes with a shared secret
 * header. The Spring Boot backend must send the same secret in
 * `X-Internal-Secret` for the call to be accepted. Anonymous traffic is
 * rejected with 401.
 *
 * Why a shared secret (not JWT) here?
 *   - These endpoints are server-to-server (Spring Boot → gateway), no user.
 *   - Keeps the gateway independent from the user auth flow.
 *   - Trivial to rotate.
 */
function internalSecretGuard(req, res, next) {
    const provided = req.header(INTERNAL_SECRET_HEADER);
    const expected = process.env.INTERNAL_SECRET;

    if (!expected) {
        console.error('[internal-guard] INTERNAL_SECRET not configured');
        return res.status(500).json({ error: 'Server misconfigured' });
    }

    if (!provided || provided !== expected) {
        return res.status(401).json({ error: 'Unauthorized: invalid internal secret' });
    }

    next();
}

/**
 * Build the /internal router.
 *
 * Each endpoint accepts a JSON body, validates the minimum required fields,
 * and emits a Socket.IO event on the right room.
 *
 * Required body shapes:
 *   POST /internal/notify/order-created
 *     { orderId, orderNumber, totalAmount }
 *   POST /internal/notify/order-status
 *     { orderId, orderNumber, status, customerId }
 *   POST /internal/notify/low-stock
 *     { productId, productName, quantityOnHand, threshold }
 *   POST /internal/notify/order-sent-to-warehouse
 *     { orderId, orderNumber, itemCount }
 */
function buildNotificationRouter(realtimeNamespace) {
    const router = express.Router();

    router.use(internalSecretGuard);

    // ----------------------------------------------------------------
    // POST /internal/notify/order-created → emit to 'admins' room
    // ----------------------------------------------------------------
    router.post('/notify/order-created', (req, res) => {
        const { orderId, orderNumber, totalAmount } = req.body || {};
        if (!orderId || !orderNumber) {
            return res.status(400).json({ error: 'orderId and orderNumber are required' });
        }

        const payload = {
            orderId,
            orderNumber,
            totalAmount: totalAmount ?? 0,
            createdAt: new Date().toISOString(),
        };

        realtimeNamespace.to('admins').emit('order.created', payload);
        console.log(`[notify] order.created → admins: ${JSON.stringify(payload)}`);

        res.json({ success: true, emittedTo: 'admins' });
    });

    // ----------------------------------------------------------------
    // POST /internal/notify/order-status → emit to user:<customerId>
    // ----------------------------------------------------------------
    router.post('/notify/order-status', (req, res) => {
        const { orderId, orderNumber, status, customerId } = req.body || {};
        if (!orderId || !orderNumber || !status || !customerId) {
            return res.status(400).json({
                error: 'orderId, orderNumber, status and customerId are required',
            });
        }

        const payload = {
            orderId,
            orderNumber,
            status,
            updatedAt: new Date().toISOString(),
        };

        realtimeNamespace.to(`user:${customerId}`).emit('order.status.updated', payload);
        console.log(`[notify] order.status.updated → user:${customerId}: ${JSON.stringify(payload)}`);

        res.json({ success: true, emittedTo: `user:${customerId}` });
    });

    // ----------------------------------------------------------------
    // POST /internal/notify/low-stock → emit to 'warehouse' room
    // ----------------------------------------------------------------
    router.post('/notify/low-stock', (req, res) => {
        const { productId, productName, quantityOnHand, threshold } = req.body || {};
        if (!productId) {
            return res.status(400).json({ error: 'productId is required' });
        }

        const payload = {
            productId,
            productName: productName || null,
            quantityOnHand: quantityOnHand ?? 0,
            threshold: threshold ?? 0,
            detectedAt: new Date().toISOString(),
        };

        realtimeNamespace.to('warehouse').emit('inventory.low_stock', payload);
        console.log(`[notify] inventory.low_stock → warehouse: ${JSON.stringify(payload)}`);

        res.json({ success: true, emittedTo: 'warehouse' });
    });

    // ----------------------------------------------------------------
    // POST /internal/notify/order-sent-to-warehouse → emit to 'warehouse' room
    // ----------------------------------------------------------------
    router.post('/notify/order-sent-to-warehouse', (req, res) => {
        const { orderId, orderNumber, itemCount } = req.body || {};
        if (!orderId || !orderNumber) {
            return res.status(400).json({ error: 'orderId and orderNumber are required' });
        }

        const payload = {
            orderId,
            orderNumber,
            itemCount: itemCount ?? 0,
            createdAt: new Date().toISOString(),
        };

        realtimeNamespace.to('warehouse').emit('order.ready_to_prepare', payload);
        console.log(`[notify] order.ready_to_prepare → warehouse: ${JSON.stringify(payload)}`);

        res.json({ success: true, emittedTo: 'warehouse' });
    });

    return router;
}

module.exports = {
    buildNotificationRouter,
    internalSecretGuard,
};