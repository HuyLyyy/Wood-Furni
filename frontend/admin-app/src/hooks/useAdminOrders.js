import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams as _useSearchParams } from 'react-router-dom';
import { adminOrdersApi } from '../services/apiAdminOrders.js';
import { toIsoBusinessDay } from '../utils/format.js';

/**
 * useAdminOrders — drives the admin OrderListPage.
 *
 * URL-synced filters: orderNumber, status, customerId, createdFrom, createdTo, page.
 *
 * Date inputs on the FE are `<input type="date">` (yyyy-MM-dd). Each bound
 * is treated as a full calendar day in BUSINESS_TIMEZONE (Asia/Ho_Chi_Minh):
 *   - createdFrom → start-of-day in VN (00:00:00.000 +07:00)
 *   - createdTo   → end-of-day   in VN (23:59:59.999 +07:00)
 *
 * This keeps the bounds stable regardless of where the browser is running
 * and avoids the off-by-7-hours mismatch that made some orders show up under
 * the wrong calendar day.
 *
 * Either bound may be left empty by the user; in that case the backend just
 * applies the remaining bound (open on the other side).
 *
 * IMPORTANT: param keys in the URL MUST match what readFilters() reads and
 * what the API params are named. All keys use the full `createdFrom` /
 * `createdTo` naming to match the backend @RequestParam names.
 *
 * See useAdminProducts.js for the full hardening rationale — short version:
 * we depend on the serialized URL string and a ref guard, never on a freshly
 * computed filters object.
 */
export default function useAdminOrders({ pageSize = 20 } = {}) {
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

    const reqIdRef = useRef(0);
    const lastUrlKeyRef = useRef(null);
    const inFlightRef = useRef(0);

    useEffect(() => {
        if (lastUrlKeyRef.current === urlKey) return;
        lastUrlKeyRef.current = urlKey;

        const myReq = ++reqIdRef.current;
        inFlightRef.current += 1;
        setState((prev) => ({ ...prev, loading: true, error: null }));

        const params = buildOrderParams(filters);
        adminOrdersApi
            .list(params)
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
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [urlKey]);

    const refresh = useCallback(() => {
        const myReq = ++reqIdRef.current;
        inFlightRef.current += 1;
        setState((prev) => ({ ...prev, loading: true, error: null }));

        const params = buildOrderParams(filters);
        adminOrdersApi.list(params)
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
                if (k !== 'page' && k !== 'size') next.delete('page');
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

/**
 * Build the query-param object sent to GET /orders.
 *
 * Date logic: a `<input type="date">` value is interpreted as a calendar day
 * in BUSINESS_TIMEZONE (Asia/Ho_Chi_Minh). We convert to UTC instants at
 * 00:00 and 23:59:59.999 LOCAL VN so a same-day filter returns exactly the
 * orders created during that day in Vietnam time.
 *
 * Either bound can be omitted — the backend then leaves that side open.
 */
function buildOrderParams(filters) {
    const params = {
        status: filters.status || undefined,
        customerId: filters.customerId || undefined,
        orderNumber: filters.orderNumber || undefined,
        page: filters.page,
        size: filters.size,
    };
    if (filters.createdFrom) {
        const iso = toIsoBusinessDay(filters.createdFrom, 'start');
        if (iso) params.createdFrom = iso;
    }
    if (filters.createdTo) {
        const iso = toIsoBusinessDay(filters.createdTo, 'end');
        if (iso) params.createdTo = iso;
    }
    return params;
}

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
        status: get('status'),
        customerId: get('customerId'),
        orderNumber: get('orderNumber'),
        createdFrom: get('createdFrom'),
        createdTo: get('createdTo'),
        page: getInt('page', 0),
        size: getInt('size', defaultSize),
    };
}
