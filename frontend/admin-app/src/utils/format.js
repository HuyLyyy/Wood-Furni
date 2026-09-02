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

/**
 * BUSINESS_TIMEZONE — Vietnam (UTC+7).
 *
 * Used by all "calendar day" filters: the user expects "2026-09-02" to mean
 * the whole of 2 Sep in Vietnam time, not the whole of 2 Sep in UTC. Picking
 * UTC causes orders created between 07:00–23:59 SGT on 2 Sep to be wrongly
 * attributed to 3 Sep, and orders created between 00:00–06:59 SGT on 2 Sep
 * to be wrongly attributed to 1 Sep.
 */
export const BUSINESS_TIMEZONE = 'Asia/Ho_Chi_Minh';

// Vietnam has no daylight savings, so the offset is a fixed -7h.
// To convert a "wall-clock time in VN" (e.g. 00:00:00 on 2026-09-02)
// to its UTC instant, subtract 7 hours:
//   instant = Date.UTC(y,m,d,H,M,S) - 7 * 3600 * 1000
// And stringify that instant with `.toISOString()`.
const VN_OFFSET_MS = 7 * 60 * 60 * 1000;

/**
 * Convert a `<input type="date">` value (yyyy-MM-dd, treated as a calendar
 * day in {@link BUSINESS_TIMEZONE}) to an ISO-8601 instant that represents
 * either the start or end of that local day.
 *
 * For example, on 2026-09-02:
 *   toIsoBusinessDay('2026-09-02', 'start') → '2026-09-01T17:00:00.000Z'
 *   toIsoBusinessDay('2026-09-02', 'end')   → '2026-09-02T16:59:59.999Z'
 *
 * Returns null for null/invalid input.
 */
export function toIsoBusinessDay(yyyyMmDd, edge) {
    if (!yyyyMmDd || typeof yyyyMmDd !== 'string') return null;
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(yyyyMmDd);
    if (!m) return null;
    const y = +m[1];
    const mo = +m[2];
    const d = +m[3];
    const isEnd = edge === 'end';
    const hour = isEnd ? 23 : 0;
    const minute = isEnd ? 59 : 0;
    const second = isEnd ? 59 : 0;
    const millis = isEnd ? 999 : 0;
    // Build a UTC instant of the desired VN wall-clock and subtract the
    // fixed VN offset. We use Date.UTC() (not `new Date(y,mo,d)`) so the
    // computation doesn't depend on the host machine's timezone.
    const localAsUtcMs = Date.UTC(y, mo - 1, d, hour, minute, second, millis);
    return new Date(localAsUtcMs - VN_OFFSET_MS).toISOString();
}