// WOODFURNI Node Gateway — entrypoint
// Boots Express + Socket.IO, exposes:
//   - GET  /health
//   - POST /internal/notify/*        (guarded by shared secret — see routes/notification.js)
//   - Socket.IO namespace /realtime  (JWT-protected — see middleware/auth.js)

require('dotenv').config();

const http = require('http');
const express = require('express');
const cors = require('cors');
const { Server: SocketIOServer } = require('socket.io');
const { createProxyMiddleware } = require('http-proxy-middleware');

const { registerRealtimeNamespace } = require('./socket/handlers');
const { buildNotificationRouter } = require('./routes/notification');

const PORT = parseInt(process.env.PORT || '3000', 10);
const CORS_ORIGINS = (process.env.CORS_ORIGINS || 'http://localhost:5173,http://localhost:8083')
    .split(',')
    .map(s => s.trim())
    .filter(Boolean);
const BACKEND_BASE_URL = process.env.BACKEND_BASE_URL || 'http://backend:8080';

// DEBUG: log env vars on startup
console.log('[gateway] BACKEND_BASE_URL =', BACKEND_BASE_URL);
console.log('[gateway] PORT =', PORT);
console.log('[gateway] NODE_ENV =', process.env.NODE_ENV);

const app = express();

// DEBUG: log every incoming request
app.use((req, res, next) => {
    console.log(`[gateway] ${req.method} ${req.originalUrl}`);
    next();
});

app.use(cors({
    origin: CORS_ORIGINS,
    credentials: true,
}));
// API proxy → backend (bearer token forwarded automatically by axios in frontend)
//
// IMPORTANT: the proxy middleware must be registered BEFORE express.json(),
// otherwise body-parser will consume the request stream and the proxy will
// forward an empty body to the backend (and POST /api/v1/auth/login will be
// rejected because Spring Security expects a JSON body).
//
// http-proxy-middleware v3 syntax:
//   - on: { proxyReq: fn }   (top-level onProxyReq is removed)
//   - pathRewrite             (still accepts the object form in v3)
app.use('/api', createProxyMiddleware({
    target: BACKEND_BASE_URL,
    changeOrigin: true,
    pathRewrite: { '^/api': '/api' },
    on: {
        proxyReq: (proxyReq, req) => {
            // Forward Authorization header from browser → backend
            const auth = req.headers['authorization'];
            if (auth) proxyReq.setHeader('Authorization', auth);
        },
    },
    onError: (err, req, res) => {
        console.error('[gateway] proxy error:', err.message);
        res.status(502).json({ success: false, message: 'Gateway error: ' + err.message });
    },
}));

// Proxy /uploads/* → backend (Spring Boot StorageController serves uploaded images)
// Nginx proxies /uploads/ → gateway, gateway forwards to backend
app.use('/uploads', createProxyMiddleware({
    target: BACKEND_BASE_URL,
    changeOrigin: true,
    pathRewrite: { '^/uploads': '/api/v1/storage/uploads' },
    onError: (err, req, res) => {
        console.error('[gateway] uploads proxy error:', err.message);
        res.status(502).json({ success: false, message: 'Gateway error: ' + err.message });
    },
}));

// JSON parser for /internal/notify/* routes only (NOT for /api/* which is proxied).
// We deliberately register it AFTER the proxy so the proxy sees the raw stream.
app.use('/internal', express.json({ limit: '256kb' }));

// HTTP server
const server = http.createServer(app);

// Socket.IO
const io = new SocketIOServer(server, {
    cors: {
        origin: CORS_ORIGINS,
        credentials: true,
    },
    pingTimeout: 60000,
});

const realtime = registerRealtimeNamespace(io);

// Internal notify routes (Spring Boot → gateway)
app.use('/internal', buildNotificationRouter(realtime));

server.listen(PORT, () => {
    console.log(`[gateway] listening on http://localhost:${PORT}`);
    console.log(`[gateway] Socket.IO namespace: /realtime`);
    console.log(`[gateway] CORS origins: ${CORS_ORIGINS.join(', ')}`);
});

// Graceful shutdown
function shutdown(signal) {
    console.log(`[gateway] ${signal} received, shutting down`);
    server.close(() => process.exit(0));
}
process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));