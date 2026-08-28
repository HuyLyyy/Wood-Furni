import { io } from 'socket.io-client';
import { useEffect, useRef, useState } from 'react';
import { tokenStorage } from '../services/apiClient.js';

const GATEWAY_URL = import.meta.env.VITE_WS_URL || 'http://localhost:3001';

/**
 * Real-time gateway socket. Singleton — only one connection is opened
 * for the entire app, regardless of how many components are mounted.
 *
 * Behaviour:
 *   - isAuthenticated=true → connect with JWT, namespace /realtime
 *   - token rotates (login/refresh) → disconnect & reconnect
 *   - isAuthenticated=false → disconnect
 *   - auto-reconnect with backoff (socket.io default)
 *
 * The hook exposes a `socket` (stable reference) + `connected` flag so any
 * component can subscribe to events:
 *
 *   useEffect(() => {
 *     if (!socket || !connected) return;
 *     const onStatus = (p) => { ... };
 *     socket.on('order.status.updated', onStatus);
 *     return () => socket.off('order.status.updated', onStatus);
 *   }, [socket, connected]);
 */
let socket = null;

function teardown() {
    if (socket) {
        socket.removeAllListeners();
        socket.disconnect();
        socket = null;
    }
}

function ensureConnected(token) {
    if (socket && socket.connected) return socket;
    if (socket && !socket.connected) {
        // Re-attach auth if the token changed
        socket.auth = { token };
        socket.connect();
        return socket;
    }

    teardown();
    socket = io(`${GATEWAY_URL}/realtime`, {
        auth: { token },
        transports: ['websocket', 'polling'],
        reconnection: true,
        reconnectionAttempts: Infinity,
        reconnectionDelay: 1000,
        reconnectionDelayMax: 5000,
        autoConnect: true,
    });
    return socket;
}

/**
 * Subscribe to the singleton realtime socket while the user is
 * authenticated. Returns the socket + a connected boolean.
 *
 * NOTE: components should NOT store the socket in state — use the returned
 * ref-style object, or just access `socket` directly in effects.
 */
export function useRealtime(isAuthenticated) {
    const [connected, setConnected] = useState(false);
    const socketRef = useRef(null);

    useEffect(() => {
        if (!isAuthenticated) {
            teardown();
            socketRef.current = null;
            setConnected(false);
            return undefined;
        }

        const token = tokenStorage.getAccess();
        if (!token) {
            setConnected(false);
            return undefined;
        }

        const s = ensureConnected(token);
        socketRef.current = s;

        const onConnect = () => setConnected(true);
        const onDisconnect = () => setConnected(false);
        const onError = (err) => {
            // Auth failures → gateway will fire connect_error with message
            // like "Invalid or expired token". We log and rely on the
            // existing 401 handler in apiClient to redirect.
            console.warn('[realtime] socket error:', err?.message || err);
        };

        s.on('connect', onConnect);
        s.on('disconnect', onDisconnect);
        s.on('connect_error', onError);

        // Pick up the current state if the socket is already connected
        if (s.connected) setConnected(true);

        return () => {
            s.off('connect', onConnect);
            s.off('disconnect', onDisconnect);
            s.off('connect_error', onError);
        };
    }, [isAuthenticated]);

    return { socket: socketRef.current, connected };
}

/**
 * Helper for components that just want to subscribe to an event and
 * unsubscribe on unmount, with the auth check built in.
 *
 *   useRealtimeEvent('order.status.updated', (payload) => { ... });
 */
export function useRealtimeEvent(event, handler, isAuthenticated = true) {
    const { socket, connected } = useRealtime(isAuthenticated);

    useEffect(() => {
        if (!socket || !connected || typeof handler !== 'function') return undefined;
        socket.on(event, handler);
        return () => socket.off(event, handler);
    }, [socket, connected, event, handler]);
}

/**
 * Force a fresh connection — useful after a token refresh.
 */
export function reconnectRealtime() {
    if (socket) {
        const token = tokenStorage.getAccess();
        socket.auth = { token };
        socket.disconnect();
        socket.connect();
    }
}