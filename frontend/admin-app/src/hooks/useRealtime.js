import { io } from 'socket.io-client';
import { useEffect, useRef, useState } from 'react';
import { tokenStorage } from '../services/apiClient.js';

const GATEWAY_URL = import.meta.env.VITE_WS_URL || '';

/**
 * Admin-app realtime connection.
 *
 * The gateway fans out events on the `/realtime` namespace. The gateway
 * decides what each connected client sees based on its JWT role:
 *   - CUSTOMER  → only its own order.status.updated events
 *   - ADMIN     → everything on the admin namespace, including order.created
 *
 * Singleton per app instance. Components subscribe via the returned
 * socket ref + connected flag.
 *
 * Events relevant to the admin dashboard:
 *   - order.created  → fired when a customer completes checkout
 *   - order.status.updated → already subscribed from the customer app pattern
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
            console.warn('[admin-realtime] socket error:', err?.message || err);
        };

        s.on('connect', onConnect);
        s.on('disconnect', onDisconnect);
        s.on('connect_error', onError);

        if (s.connected) setConnected(true);
        return () => {
            s.off('connect', onConnect);
            s.off('disconnect', onDisconnect);
            s.off('connect_error', onError);
        };
    }, [isAuthenticated]);

    return { socket: socketRef.current, connected };
}

export function useRealtimeEvent(event, handler, isAuthenticated = true) {
    const { socket, connected } = useRealtime(isAuthenticated);
    useEffect(() => {
        if (!socket || !connected || typeof handler !== 'function') return undefined;
        socket.on(event, handler);
        return () => socket.off(event, handler);
    }, [socket, connected, event, handler]);
}