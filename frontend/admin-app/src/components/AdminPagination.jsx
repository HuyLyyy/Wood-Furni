import PropTypes from 'prop-types';
import './AdminPagination.css';

/**
 * AdminPagination — page-first 1..N buttons. Matches the customer-app
 * Pagination but slightly heavier visual style for the admin context.
 */
export default function AdminPagination({ page, totalPages, onChange }) {
    if (totalPages <= 1) return null;
    const current = page + 1;
    const pages = buildPageList(current, totalPages);

    return (
        <nav className="admin-pagination" aria-label="Pagination">
            <button
                type="button"
                className="admin-pagination__btn"
                disabled={page <= 0}
                onClick={() => onChange(page - 1)}
            >‹</button>

            {pages.map((p, i) =>
                p === '…' ? (
                    <span key={`ell-${i}`} className="admin-pagination__ellipsis">…</span>
                ) : (
                    <button
                        key={p}
                        type="button"
                        className={`admin-pagination__btn ${p === current ? 'is-active' : ''}`}
                        onClick={() => onChange(p - 1)}
                    >{p}</button>
                )
            )}

            <button
                type="button"
                className="admin-pagination__btn"
                disabled={page >= totalPages - 1}
                onClick={() => onChange(page + 1)}
            >›</button>
        </nav>
    );
}

AdminPagination.propTypes = {
    page: PropTypes.number.isRequired,
    totalPages: PropTypes.number.isRequired,
    onChange: PropTypes.func.isRequired,
};

function buildPageList(current, total) {
    const pages = [];
    const set = new Set();
    for (let i = 1; i <= total; i++) {
        if (i === 1 || i === total || (i >= current - 1 && i <= current + 1)) set.add(i);
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