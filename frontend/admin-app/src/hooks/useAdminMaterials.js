import { useEffect, useState } from 'react';
import { adminCatalogApi } from '../services/apiAdminCatalog.js';

export default function useMaterials() {
    const [data, setData] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        let cancelled = false;
        adminCatalogApi
            .getMaterials()
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

export function useAdminMaterials() {
    return useMaterials();
}