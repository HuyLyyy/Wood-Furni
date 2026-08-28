import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams as _useSearchParams } from 'react-router-dom';
import { adminReviewsApi } from '../services/apiAdminReviews.js';

/**
 * useAdminReviews — drives ReviewListPage.
 *
 * URL-synced filters: rating, status, page.
 *
 * The backend exposes `GET /admin/reviews` with optional filters. We don't
 * filter by productId in the URL (no product filter UI yet — easy to add
 * later by extending the form).
 *
 * See useAdminProducts.js for the full hardening rationale.
 */
export default function useAdminReviews({ pageSize = 20 } = {}) {
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

        const params = {
            rating: filters.rating || undefined,
            status: filters.status || undefined,
            page: filters.page,
            size: filters.size,
        };

        adminReviewsApi.list(params)
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

        adminReviewsApi.list({
            rating: filters.rating || undefined,
            status: filters.status || undefined,
            page: filters.page,
            size: filters.size,
        }).then((result) => {
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
        }).catch((err) => {
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
        rating: get('rating'),
        status: get('status'),
        page: getInt('page', 0),
        size: getInt('size', defaultSize),
    };
}
