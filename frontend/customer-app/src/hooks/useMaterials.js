import { useEffect, useState } from 'react';
import { catalogApi } from '../services/apiCatalog.js';

/**
 * useMaterials — fetches the materials (wood types) list once.
 */
export default function useMaterials() {
    const [data, setData] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        let cancelled = false;

        catalogApi
            .getMaterials()
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