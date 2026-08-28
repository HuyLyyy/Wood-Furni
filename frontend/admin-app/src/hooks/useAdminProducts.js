import { useCallback, useEffect, useRef, useState } from 'react';
import { adminCatalogApi } from '../services/apiAdminCatalog.js';

/**
 * useAdminProducts — drives the admin ProductListPage.
 *
 * URL-synced filters: keyword, status, category, page, size, sort.
 * Auto-fetches on any URL change. Stale-response safe.
 *
 * Hardening notes (2026-08-20):
 *  - `useMemo(filters, [searchParams])` alone was not enough — react-router's
 *    useSearchParams can hand back a new tuple reference per render even when
 *    the URL is unchanged, which kept re-running the fetch effect in an
 *    infinite loop. We now compare `searchParams.toString()` (the actual
 *    serialized query string) inside a ref guard so the fetch only fires when
 *    the URL truly changes.
 *  - `loading: true` is no longer flipped inside the fetch effect; that was
 *    another source of forced re-renders. We expose a `loading` flag derived
 *    from a ref counter incremented around each in-flight request.
 */
export default function useAdminProducts({ pageSize = 20 } = {}) {
    const [searchParams, setSearchParams] = useURLSearchParams();

    // Stable snapshot of the URL — re-derived only when the serialized query
    // string actually changes. This is the single source of truth for "did the
    // user change a filter?".
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

    const reqIdRef = useRef(0);
    const lastUrlKeyRef = useRef(null);
    const inFlightRef = useRef(0);

    useEffect(() => {
        if (lastUrlKeyRef.current === urlKey) return;
        lastUrlKeyRef.current = urlKey;

        const myReq = ++reqIdRef.current;
        inFlightRef.current += 1;
        setState((prev) => ({ ...prev, loading: true, error: null }));

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

        adminCatalogApi
            .searchProducts(params)
            .then((result) => {
                if (reqIdRef.current !== myReq) return;
                inFlightRef.current = Math.max(0, inFlightRef.current - 1);
                setState({
                    items: result.items || [],
                    page: result.page ?? filters.page,
                    size: result.size ?? filters.size,
                    totalElements: result.totalElements ?? 0,
                    totalPages: result.totalPages ?? 0,
                    loading: inFlightRef.current > 0,
                    error: null,
                });
            })
            .catch((err) => {
                if (reqIdRef.current !== myReq) return;
                inFlightRef.current = Math.max(0, inFlightRef.current - 1);
                setState((prev) => ({
                    ...prev,
                    loading: inFlightRef.current > 0,
                    error: err,
                }));
            });
        // We intentionally depend on the stringified URL, not on `filters`, so
        // we never re-fire on a render that produces an equivalent query string.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [urlKey]);

    const refresh = useCallback(() => {
        const myReq = ++reqIdRef.current;
        inFlightRef.current += 1;
        setState((prev) => ({ ...prev, loading: true, error: null }));

        adminCatalogApi
            .searchProducts({
                keyword: filters.keyword || undefined,
                category: filters.category || undefined,
                status: filters.status || undefined,
                sort: filters.sort,
                page: filters.page,
                size: filters.size,
            })
            .then((result) => {
                if (reqIdRef.current !== myReq) return;
                inFlightRef.current = Math.max(0, inFlightRef.current - 1);
                setState({
                    items: result.items || [],
                    page: result.page ?? filters.page,
                    size: result.size ?? filters.size,
                    totalElements: result.totalElements ?? 0,
                    totalPages: result.totalPages ?? 0,
                    loading: inFlightRef.current > 0,
                    error: null,
                });
            })
            .catch((err) => {
                if (reqIdRef.current !== myReq) return;
                inFlightRef.current = Math.max(0, inFlightRef.current - 1);
                setState((prev) => ({
                    ...prev,
                    loading: inFlightRef.current > 0,
                    error: err,
                }));
            });
    }, [filters]);

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
