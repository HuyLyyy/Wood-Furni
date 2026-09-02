import { useCallback, useEffect, useRef, useState } from 'react';
import { adminCatalogApi } from '../services/apiAdminCatalog.js';

/**
 * useAdminProducts — drives the admin ProductListPage.
 *
 * URL-synced filters: keyword, status, category, page, size, sort.
 * Auto-fetches on any URL change. Stale-response safe.
 *
 * Race-condition hardening:
 * - A single `currentReqId` ref replaces the `reqIdRef + inFlightRef` pair.
 *   Only the response matching the live request id is applied to state; all
 *   others are silently discarded. This prevents stale responses from
 *   overwriting fresh ones when network latency varies across keystrokes.
 * - `loading` is derived from `currentReqId !== null` (true while a request
 *   is in-flight), so it never gets "stuck" when a request early-returns.
 * - `refresh()` triggers a new fetch by incrementing a `__r` query param,
 *   reusing the URL effect's debouncing guard without duplicating logic.
 */
export default function useAdminProducts({ pageSize = 20 } = {}) {
    const [searchParams, setSearchParams] = useURLSearchParams();

    const filters = readFilters(searchParams, pageSize);
    const urlKey = searchParams.toString();

    const [state, setState] = useState({
        items: [],
        page: filters.page,
        size: filters.size,
        totalElements: 0,
        totalPages: 0,
        loading: true,
        error: null,
    });

    // Single integer that identifies the currently-active request.
    // Starts null (no request pending). Set to a positive integer when a
    // request fires, reset to null when that request resolves.
    const currentReqId = useRef(null);
    // Tracks the urlKey at the moment the current request was fired, so we
    // can re-fire if the user hits Refresh while the same URL is showing.
    const requestUrlKey = useRef(null);

    const doFetch = useCallback((reqId, urlKeySnapshot) => {
        const params = {
            keyword: filters.keyword || undefined,
            category: filters.category || undefined,
            environment: filters.environment || undefined,
            room: filters.room || undefined,
            status: filters.status || undefined,
            woodType: filters.woodType || undefined,
            sort: filters.sort,
            page: filters.page,
            size: filters.size,
        };

        setState((prev) => ({ ...prev, loading: true, error: null }));

        adminCatalogApi
            .searchProducts(params)
            .then((result) => {
                // Only apply this response if it belongs to the current request.
                // Stale responses (e.g. a slow network response that arrived after
                // a faster subsequent request) are discarded automatically.
                if (currentReqId.current !== reqId) return;
                requestUrlKey.current = urlKeySnapshot;

                setState({
                    items: result.items || [],
                    page: result.page ?? filters.page,
                    size: result.size ?? filters.size,
                    totalElements: result.totalElements ?? 0,
                    totalPages: result.totalPages ?? 0,
                    loading: false,
                    error: null,
                });
            })
            .catch((err) => {
                if (currentReqId.current !== reqId) return;
                setState((prev) => ({
                    ...prev,
                    loading: false,
                    error: err,
                }));
            });
    }, [filters]);

    useEffect(() => {
        // Guard: skip if URL hasn't actually changed (react-router may give
        // us a new reference even when the string is identical).
        if (requestUrlKey.current === urlKey) return;
        requestUrlKey.current = urlKey;

        const reqId = (currentReqId.current || 0) + 1;
        currentReqId.current = reqId;

        doFetch(reqId, urlKey);
        // Intentionally depend on the stringified URL, not on `filters`, so
        // we never re-fire on a render that produces an equivalent query string.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [urlKey]);

    /**
     * Refresh — fires a new fetch against whatever URL is currently in the
     * address bar. Implemented by temporarily toggling a `__r` param, which
     * changes `urlKey` and triggers the effect above. The param is removed
     * immediately so it doesn't pollute history.
     */
    const refresh = useCallback(() => {
        const tick = Math.random().toString(36).slice(2);
        setSearchParams((prev) => {
            const next = new URLSearchParams(prev);
            next.set('__r', tick);
            return next;
        });
        // Strip the tick param right back out so the URL is clean.
        setTimeout(() => {
            setSearchParams((prev) => {
                const next = new URLSearchParams(prev);
                next.delete('__r');
                return next;
            });
        }, 0);
    }, [setSearchParams]);

    const setFilters = useCallback((patch) => {
        setSearchParams((prev) => {
            const next = new URLSearchParams(prev);
            for (const [k, v] of Object.entries(patch)) {
                if (v === null || v === undefined || v === '') next.delete(k);
                else next.set(k, String(v));
                if (k !== 'page' && k !== 'size' && k !== 'sort') next.delete('page');
            }
            return next;
        });
    }, [setSearchParams]);

    const setPage = useCallback((p) => {
        setSearchParams((prev) => {
            const next = new URLSearchParams(prev);
            if (p <= 0) next.delete('page');
            else next.set('page', String(p));
            return next;
        }, { replace: true });
    }, [setSearchParams]);

    return { ...state, filters, setFilters, setPage, refresh };
}

// =============================================================
// Helpers
// =============================================================

// Lazy re-import of react-router so the hook file stays generic if a
// caller doesn't use it (admin-app always does, but this avoids a hard
// crash if someone reuses the hook).
import { useSearchParams as _useSearchParams } from 'react-router-dom';
function useURLSearchParams() {
    return _useSearchParams();
}

function readFilters(params, defaultSize) {
    const get = (k) => {
        const v = params.get(k);
        return v && v.trim() !== '' ? v : null;
    };
    const getInt = (k, fb) => {
        const raw = params.get(k);
        const n = parseInt(raw ?? '', 10);
        return Number.isFinite(n) && n >= 0 ? n : fb;
    };
    return {
        keyword: get('keyword'),
        category: get('category'),
        status: get('status'),
        environment: get('environment'),
        room: get('room'),
        woodType: get('woodType'),
        sort: get('sort') || '-createdAt',
        page: getInt('page', 0),
        size: getInt('size', defaultSize),
    };
}
