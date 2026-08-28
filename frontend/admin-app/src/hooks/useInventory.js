import { useCallback, useEffect, useState } from 'react';
import { adminInventoryApi } from '../services/apiAdminInventory.js';

/**
 * useInventory — list inventory records. Supports the `lowStock` flag
 * which switches to /inventory/low-stock and ignores the threshold input.
 */
export default function useInventory({ pageSize = 20, lowStock = false } = {}) {
    const [items, setItems] = useState([]);
    const [pagination, setPagination] = useState({ page: 0, size: pageSize, totalElements: 0, totalPages: 0 });
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const fetchPage = useCallback(async (page) => {
        setLoading(true);
        setError(null);
        try {
            const fn = lowStock ? adminInventoryApi.listLowStock : adminInventoryApi.list;
            const data = await fn({ page, size: pageSize });
            setItems(data.items || []);
            setPagination({
                page: data.page ?? page,
                size: data.size ?? pageSize,
                totalElements: data.totalElements ?? 0,
                totalPages: data.totalPages ?? 0,
            });
            setLoading(false);
            return data;
        } catch (err) {
            setError(err);
            setLoading(false);
            return null;
        }
    }, [lowStock, pageSize]);

    useEffect(() => {
        fetchPage(0);
    }, [fetchPage]);

    return {
        items, pagination, loading, error,
        loadPage: fetchPage,
        refresh: () => fetchPage(pagination.page),
    };
}