import { useEffect, useState } from 'react';
import { adminCatalogApi } from '../services/apiAdminCatalog.js';

function useCategories() {
    const [data, setData] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        let cancelled = false;
        adminCatalogApi
            .getCategoriesTree()
            .then((items) => {
                if (cancelled) return;
                setData(items || []);
                setLoading(false);
            })
            .catch((err) => {
                if (cancelled) return;
                setError(err);
                setLoading(false);
            });
        return () => { cancelled = true; };
    }, []);

    return { data, loading, error };
}

// Named export for tree categories (used by CategoryPage).
export { useCategories };

// Default export kept for backwards-compat with any existing default-import callers.
export default useCategories;

export function useCategoriesFlat() {
    const [data, setData] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        let cancelled = false;
        adminCatalogApi
            .getCategoriesFlat()
            .then((items) => {
                if (cancelled) return;
                setData(items || []);
                setLoading(false);
            })
            .catch((err) => {
                if (cancelled) return;
                setError(err);
                setLoading(false);
            });
        return () => { cancelled = true; };
    }, []);

    return { data, loading, error };
}