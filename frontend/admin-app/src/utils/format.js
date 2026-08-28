export const formatCurrency = (amount, currency = 'VND') => {
    if (amount == null) return '—';
    const number = Number(amount);
    if (Number.isNaN(number)) return '—';
    return new Intl.NumberFormat('vi-VN', {
        style: 'currency',
        currency,
        maximumFractionDigits: 0,
    }).format(number);
};

export const formatNumber = (value) => {
    if (value == null) return '—';
    const n = Number(value);
    if (Number.isNaN(n)) return '—';
    return new Intl.NumberFormat('vi-VN').format(n);
};

export const formatDate = (iso) => {
    if (!iso) return '—';
    try {
        return new Date(iso).toLocaleDateString('vi-VN', {
            day: '2-digit', month: '2-digit', year: 'numeric',
        });
    } catch {
        return '—';
    }
};

export const formatDateTime = (iso) => {
    if (!iso) return '—';
    try {
        return new Date(iso).toLocaleString('vi-VN', {
            day: '2-digit', month: '2-digit', year: 'numeric',
            hour: '2-digit', minute: '2-digit',
        });
    } catch {
        return '—';
    }
};

/**
 * Format an ISO datetime into a yyyy-MM-ddTHH:mm value suitable for
 * `<input type="datetime-local">`. Returns '' if input is null/empty.
 */
export const toDateTimeLocalValue = (iso) => {
    if (!iso) return '';
    try {
        const d = new Date(iso);
        const pad = (n) => String(n).padStart(2, '0');
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
    } catch {
        return '';
    }
};

export const formatMonth = (yyyymm) => {
    if (!yyyymm || typeof yyyyymm !== 'string') return '—';
    // Expect "2026-01"
    const [y, m] = yyyyMmSplitter(yyyymm);
    if (!m) return yyyymm;
    return `${m}/${y}`;
};

function yyyyMmSplitter(s) {
    if (s.includes('-')) return s.split('-');
    if (s.includes('/')) return s.split('/');
    return [s];
}