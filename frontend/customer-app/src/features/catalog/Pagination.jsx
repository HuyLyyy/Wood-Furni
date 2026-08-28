import PropTypes from 'prop-types';
import './Pagination.css';

/**
 * Pagination — page-first 1..N (matches the URL convention where page=0
 * is the first page, but we show 1..N to the user).
 *
 * Props:
 *   page        current page (0-indexed)
 *   totalPages  total number of pages (>=1)
 *   onChange    (page:number) => void
 */
export default function Pagination({ page, totalPages, onChange }) {
    if (totalPages <= 1) return null;

    const current = page + 1; // human-friendly
    const pages = buildPageList(current, totalPages);

    return (
        <nav className="pagination" aria-label="Pagination">
            <button
                type="button"
                className="pagination__btn"
                disabled={page <= 0}
                onClick={() => onChange(page - 1)}
                aria-label="Trang trước"
            >
                ‹
            </button>

            {pages.map((p, i) =>
                p === '…' ? (
                    <span key={`ellipsis-${i}`} className="pagination__ellipsis">…</span>
                ) : (
                    <button
                        key={p}
                        type="button"
                        className={`pagination__btn ${p === current ? 'is-active' : ''}`}
                        onClick={() => onChange(p - 1)}
                        aria-current={p === current ? 'page' : undefined}
                    >
                        {p}
                    </button>
                )
            )}

            <button
                type="button"
                className="pagination__btn"
                disabled={page >= totalPages - 1}
                onClick={() => onChange(page + 1)}
                aria-label="Trang sau"
            >
                ›
            </button>
        </nav>
    );
}

Pagination.propTypes = {
    page: PropTypes.number.isRequired,
    totalPages: PropTypes.number.isRequired,
    onChange: PropTypes.func.isRequired,
};

// Always show first, last, current, and 1 sibling on each side. Use '…' to
// indicate hidden ranges.
function buildPageList(current, total) {
    const pages = [];
    const window_ = 1;
    const set = new Set();

    for (let i = 1; i <= total; i++) {
        if (
            i === 1 ||
            i === total ||
            (i >= current - window_ && i <= current + window_)
        ) {
            set.add(i);
        }
    }

    const sorted = Array.from(set).sort((a, b) => a - b);
    let prev = 0;
    for (const p of sorted) {
        if (prev && p - prev > 1) pages.push('…');
        pages.push(p);
        prev = p;
    }
    return pages;
}