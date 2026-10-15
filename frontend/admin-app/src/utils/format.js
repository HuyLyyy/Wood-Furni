/**
 * BUSINESS_TIMEZONE — Vietnam (UTC+7).
 *
 * Used by all "calendar day" filters: the user expects "2026-09-02" to mean
 * the whole of 2 Sep in Vietnam time, not the whole of 2 Sep in UTC. Picking
 * UTC causes orders created between 07:00–23:59 SGT on 2 Sep to be wrongly
 * attributed to 3 Sep, and orders created between 00:00–06:59 SGT on 2 Sep
 * to be wrongly attributed to 1 Sep.
 *
 * CŨNG được dùng cho formatDate/formatDateTime để tránh phụ thuộc timezone
 * máy admin (admin ở nước khác sẽ thấy giờ lệch ±7h).
 */
export const BUSINESS_TIMEZONE = 'Asia/Ho_Chi_Minh';

// BUILD: 2026-10-15T13:10Z v3 — force Vercel bundle hash bump
export const BUILD_VERSION = 'v3-2026-10-15-fix-vn-tz';

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
        // LUÔN dùng timezone VN — không phụ thuộc máy user.
        return new Date(iso).toLocaleDateString('vi-VN', {
            day: '2-digit', month: '2-digit', year: 'numeric',
            timeZone: BUSINESS_TIMEZONE,
        });
    } catch {
        return '—';
    }
};

export const formatDateTime = (iso) => {
    if (!iso) return '—';
    try {
        // LUÔN dùng timezone VN — không phụ thuộc máy user.
        // Nếu không có timeZone, browser sẽ dùng local timezone máy admin,
        // khiến admin ở nước khác thấy giờ/ngày sai lệch ±7h.
        return new Date(iso).toLocaleString('vi-VN', {
            day: '2-digit', month: '2-digit', year: 'numeric',
            hour: '2-digit', minute: '2-digit',
            timeZone: BUSINESS_TIMEZONE,
        });
    } catch {
        return '—';
    }
};

/**
 * Format an ISO datetime into a yyyy-MM-ddTHH:mm value suitable for
 * `<input type="datetime-local">`. Returns '' if input is null/empty.
 *
 * NOTE: this is overridden later in the file by a timezone-aware
 * version that handles explicit +07:00 offsets correctly.
 */
function toDateTimeLocalValueLegacy(iso) {
    if (!iso) return '';
    try {
        const d = new Date(iso);
        const pad = (n) => String(n).padStart(2, '0');
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
    } catch {
        return '';
    }
}
// Keep legacy unused to avoid shadowing the new export below.
// (Removed from final build: see `toDateTimeLocalValue` defined further down.)
void toDateTimeLocalValueLegacy;

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
 * Vietnam has no daylight savings, so the offset is a fixed -7h.
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

/**
 * Convert a datetime-local string ("yyyy-MM-ddTHH:mm", a VN wall-clock value)
 * to an ISO-8601 instant with an explicit +07:00 offset so the backend
 * deserialises it correctly regardless of how the server's JVM parses it.
 *
 * IMPORTANT: Do NOT use new Date(str).toISOString() here because the browser
 * interprets a bare "yyyy-MM-ddTHH:mm" string as local time, which depends on
 * the admin's machine timezone and will shift the instant by ±7 hours.
 */
export function toIsoVnDateTimeLocal(dtLocal) {
    if (!dtLocal) return null;
    // Parse components directly from the string — always in VN wall-clock.
    const [datePart, timePart] = dtLocal.split('T');
    const [y, mo, d] = datePart.split('-').map(Number);
    const [h, mi] = timePart.split(':').map(Number);
    // Convert VN wall-clock to UTC by subtracting the VN offset.
    const localAsUtcMs = Date.UTC(y, mo - 1, d, h, mi, 0, 0) - VN_OFFSET_MS;
    return new Date(localAsUtcMs).toISOString();
}

/**
 * Convert an ISO-8601 datetime string (with explicit offset, e.g.
 * "2026-10-10T17:00:00+07:00") to a datetime-local "yyyy-MM-ddTHH:mm" value.
 *
 * Trước đây dùng `d.getHours()/getDate()` (LOCAL time của browser) → admin
 * ở nước khác sẽ thấy ngày/giờ sai. Fix: dùng `toLocaleString` với
 * `timeZone: BUSINESS_TIMEZONE` để LUÔN lấy giờ VN.
 */
export function toDateTimeLocalValue(iso) {
    if (!iso) return '';
    try {
        const d = new Date(iso);
        if (Number.isNaN(d.getTime())) return '';
        const pad = (n) => String(n).padStart(2, '0');
        // Lấy ngày/giờ theo timezone VN (UTC+7) — không phụ thuộc browser.
        const parts = new Intl.DateTimeFormat('en-CA', {
            timeZone: BUSINESS_TIMEZONE,
            year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit', hour12: false,
        }).formatToParts(d);
        const get = (type) => parts.find((p) => p.type === type)?.value || '00';
        // en-CA đôi khi trả hour = 24 thay vì 00 cho 00:00.
        let hour = get('hour');
        if (hour === '24') hour = '00';
        return `${get('year')}-${get('month')}-${get('day')}T${hour}:${get('minute')}`;
    } catch {
        return '';
    }
}