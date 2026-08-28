import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { catalogApi } from '../services/apiCatalog.js';

/**
 * useProducts — drives the ProductListPage.
 *
 * Inputs: URL query string. The filter UI writes to the URL, this hook
 * watches the URL and re-fetches. Output: { items, page, size, totalPages,
 * totalElements, loading, error, filters }.
 *
 * Query params (must match WOODFURNI spec Mục 4.1 / backend ProductController):
 *   keyword, category, environment, room, woodType, minPrice, maxPrice, sort, page, size
 *
 * Sorting contract (matches backend's buildSort):
 *   "field,asc" | "field,desc" | "-field"  (default: -createdAt)
 *
 * Why URL-driven filters?
 *   - shareable / bookmarkable
 *   - browser back/forward works
 *   - copy URL to another tab → same filter state
 *   - server-side fetch re-runs on filter change because [searchParams] is in deps
 */
export default function useProducts({ pageSize = 20 } = {}) {
    const [searchParams, setSearchParams] = useSearchParams();

    const filters = readFilters(searchParams, pageSize);

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

    useEffect(() => {
        const myReqId = ++reqIdRef.current;
        setState((prev) => ({ ...prev, loading: true, error: null }));

        const params = readFilters(searchParams, pageSize);

        const apiParams = {
            keyword: params.keyword || undefined,
            category: params.category || undefined,
            environment: params.environment || undefined,
            room: params.room || undefined,
            woodType: params.woodType || undefined,
            minPrice: params.minPrice || undefined,
            maxPrice: params.maxPrice || undefined,
            sort: params.sort,
            page: params.page,
            size: params.size,
        };

        catalogApi
            .searchProducts(apiParams)
            .then((result) => {
                if (reqIdRef.current !== myReqId) return;
                setState({
                    items: result.items || [],
                    page: result.page ?? params.page,
                    size: result.size ?? params.size,
                    totalElements: result.totalElements ?? 0,
                    totalPages: result.totalPages ?? 0,
                    loading: false,
                    error: null,
                });
            })
            .catch((err) => {
                if (reqIdRef.current !== myReqId) return;
                setState((prev) => ({
                    ...prev,
                    loading: false,
                    error: err,
                }));
            });
    }, [searchParams, pageSize]);

    /**
     * Update one or more filter params. Pass `null` or `undefined` for a key
     * to remove that param from the URL (keeps the URL clean).
     *
     * Special case: changing a filter resets `page` to 0 so the user lands
     * on the first page of the new result set.
     */
    const setFilters = useCallback(
        (patch) => {
            setSearchParams(
                (prev) => {
                    const next = new URLSearchParams(prev);
                    let resetPage = false;

                    for (const [k, v] of Object.entries(patch)) {
                        if (v === null || v === undefined || v === '') {
                            next.delete(k);
                            if (k !== 'page' && k !== 'size' && k !== 'sort') {
                                resetPage = true;
                            }
                        } else {
                            next.set(k, String(v));
                            if (k !== 'page' && k !== 'size' && k !== 'sort') {
                                resetPage = true;
                            }
                        }
                    }

                    if (resetPage) next.delete('page');
                    return next;
                },
                { replace: false }
            );
        },
        [setSearchParams]
    );

    const setPage = useCallback(
        (page) => {
            setSearchParams(
                (prev) => {
                    const next = new URLSearchParams(prev);
                    if (page <= 0) {
                        next.delete('page');
                    } else {
                        next.set('page', String(page));
                    }
                    return next;
                },
                { replace: true } // pagination should not pollute history
            );
        },
        [setSearchParams]
    );

    const setSort = useCallback(
        (sort) => setFilters({ sort }),
        [setFilters]
    );

    const resetFilters = useCallback(() => {
        setSearchParams(new URLSearchParams(), { replace: false });
    }, [setSearchParams]);

    return {
        ...state,
        filters,
        setFilters,
        setPage,
        setSort,
        resetFilters,
    };
}

function readFilters(params, defaultSize) {
    const get = (key) => {
        const v = params.get(key);
        return v && v.trim() !== '' ? v : null;
    };
    const getInt = (key, fallback) => {
        const raw = params.get(key);
        const n = parseInt(raw ?? '', 10);
        return Number.isFinite(n) && n >= 0 ? n : fallback;
    };

    return {
        keyword: get('keyword'),
        category: get('category'),
        environment: get('environment'),
        room: get('room'),
        woodType: get('woodType'),
        minPrice: get('minPrice'),
        maxPrice: get('maxPrice'),
        sort: get('sort') || '-createdAt',
        page: getInt('page', 0),
        size: getInt('size', defaultSize),
    };
}
