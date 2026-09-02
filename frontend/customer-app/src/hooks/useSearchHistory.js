import { useCallback, useEffect, useState } from 'react';

const STORAGE_KEY = 'woodfurni.searchHistory';
const MAX_ITEMS = 8;

/**
 * Manages a list of recent search keywords for the storefront header.
 *
 * Persists the history to localStorage so it survives reloads and is
 * shared across browser tabs. The list is ordered newest-first and is
 * de-duplicated case-insensitively.
 *
 * @returns {{ history: string[], addTerm: (q: string) => void, removeTerm: (q: string) => void, clear: () => void }}
 */
export function useSearchHistory() {
    const [history, setHistory] = useState(() => {
        if (typeof window === 'undefined') return [];
        try {
            const raw = window.localStorage.getItem(STORAGE_KEY);
            const parsed = raw ? JSON.parse(raw) : [];
            return Array.isArray(parsed) ? parsed.filter((x) => typeof x === 'string') : [];
        } catch {
            return [];
        }
    });

    // Cross-tab sync: react to storage events fired from other tabs.
    useEffect(() => {
        const onStorage = (e) => {
            if (e.key !== STORAGE_KEY) return;
            try {
                const parsed = e.newValue ? JSON.parse(e.newValue) : [];
                setHistory(Array.isArray(parsed) ? parsed : []);
            } catch {
                /* ignore corrupt payloads */
            }
        };
        window.addEventListener('storage', onStorage);
        return () => window.removeEventListener('storage', onStorage);
    }, []);

    const persist = useCallback((next) => {
        setHistory(next);
        try {
            window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
        } catch {
            // localStorage might be full or disabled (e.g. private mode); fail silently.
        }
    }, []);

    const addTerm = useCallback(
        (raw) => {
            const term = (raw ?? '').trim();
            if (!term) return;
            const lower = term.toLowerCase();
            // De-duplicate (case-insensitive) while preserving the new casing.
            const next = [
                term,
                ...history.filter((h) => h.toLowerCase() !== lower),
            ].slice(0, MAX_ITEMS);
            persist(next);
        },
        [history, persist],
    );

    const removeTerm = useCallback(
        (raw) => {
            const term = (raw ?? '').trim();
            if (!term) return;
            const lower = term.toLowerCase();
            const next = history.filter((h) => h.toLowerCase() !== lower);
            persist(next);
        },
        [history, persist],
    );

    const clear = useCallback(() => {
        persist([]);
    }, [persist]);

    return { history, addTerm, removeTerm, clear };
}
