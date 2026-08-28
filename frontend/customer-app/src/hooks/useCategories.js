import { useEffect, useState } from 'react';
import { catalogApi } from '../services/apiCatalog.js';

/**
 * useCategories — fetches the full category tree (Indoor/Outdoor → Room).
 * Returns the tree as-is from the backend (already nested).
 */
export default function useCategories() {
    const [data, setData] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        let cancelled = false;

        catalogApi
            .getCategories()
            .then((items) => {
                if (!cancelled) {
                    setData(items || []);
                    setLoading(false);
                }
            })
            .catch((err) => {
                if (!cancelled) {
                    setError(err);
                    setLoading(false);
                }
            });

        return () => {
            cancelled = true;
        };
    }, []);

    return { data, loading, error };
}