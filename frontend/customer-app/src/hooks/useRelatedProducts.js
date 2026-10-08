import { useEffect, useState, useRef } from 'react';
import { catalogApi } from '../services/apiCatalog.js';

/**
 * useRelatedProducts — fetch and keep a list of "products similar to X".
 *
 * The hook is keyed on `productId` so when the user navigates between
 * product pages we always re-fetch instead of showing the previous list.
 *
 * Returns:
 *   { items, loading, error }
 *     items: ProductResponse[]
 *     loading: boolean (true during the initial fetch)
 *     error: Error | null
 */
export default function useRelatedProducts(productId, limit = 8) {
    const [items, setItems] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const reqIdRef = useRef(0);

    useEffect(() => {
        if (!productId) {
            setItems([]);
            return undefined;
        }

        const myReqId = ++reqIdRef.current;
        setLoading(true);
        setError(null);

        catalogApi
            .getRelatedProducts(productId, limit)
            .then((list) => {
                if (reqIdRef.current !== myReqId) return;
                setItems(Array.isArray(list) ? list : []);
            })
            .catch((err) => {
                if (reqIdRef.current !== myReqId) return;
                setItems([]);
                setError(err);
            })
            .finally(() => {
                if (reqIdRef.current === myReqId) setLoading(false);
            });

        return () => {
            // Mark in-flight requests as stale so they don't override state
            // when the user navigates away before the response arrives.
            reqIdRef.current++;
        };
    }, [productId, limit]);

    return { items, loading, error };
}