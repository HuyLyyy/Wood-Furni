import PropTypes from 'prop-types';
import { useState } from 'react';
import { Input } from '../../components/index.js';
import {
    ENVIRONMENTS,
    ROOMS,
    WOOD_TYPES,
} from '../../utils/catalogMeta.js';
import './FilterSidebar.css';

/**
 * FilterSidebar — drives useProducts via URL.
 *
 * Every change writes to the URL (via `setFilters`), which causes useProducts
 * to re-fetch. This is the source of truth — there's no local copy of state
 * beyond the controlled input values, which read from the URL.
 *
 * Mobile behaviour: renders inside a slide-in drawer that opens via a
 * floating button at the top of the listing page. Pure CSS — no JS lib.
 */
export default function FilterSidebar({
    filters,
    categories = [],
    onChange,
    onReset,
}) {
    const [mobileOpen, setMobileOpen] = useState(false);

    const handle = (key) => (e) => {
        const v = e.target.value;
        onChange({ [key]: v === '' ? null : v });
    };

    const body = (
        <div className="filter-sidebar__body">
            <div className="filter-sidebar__head">
                <h3 className="filter-sidebar__title">Bộ lọc</h3>
                <button
                    type="button"
                    className="filter-sidebar__reset"
                    onClick={onReset}
                >
                    Xoá hết
                </button>
            </div>

            <Input
                id="flt-keyword"
                label="Từ khoá"
                placeholder="Tìm theo tên/mô tả…"
                value={filters.keyword || ''}
                onChange={handle('keyword')}
            />

            <Select
                id="flt-category"
                label="Danh mục"
                value={filters.category || ''}
                onChange={handle('category')}
                placeholder="Tất cả danh mục"
                options={categories.map((c) => ({
                    value: c.slug || c.id,
                    label: c.name,
                }))}
            />

            <Select
                id="flt-environment"
                label="Môi trường"
                value={filters.environment || ''}
                onChange={handle('environment')}
                placeholder="Tất cả"
                options={ENVIRONMENTS}
            />

            <Select
                id="flt-room"
                label="Phòng / Khu vực"
                value={filters.room || ''}
                onChange={handle('room')}
                placeholder="Tất cả"
                options={ROOMS}
            />

            <Select
                id="flt-woodType"
                label="Loại gỗ"
                value={filters.woodType || ''}
                onChange={handle('woodType')}
                placeholder="Tất cả"
                options={WOOD_TYPES}
            />

            <fieldset className="filter-sidebar__group">
                <legend className="filter-sidebar__legend">Khoảng giá (VND)</legend>
                <div className="filter-sidebar__range">
                    <input
                        id="flt-minPrice"
                        type="number"
                        placeholder="Từ"
                        value={filters.minPrice || ''}
                        onChange={handle('minPrice')}
                        min="0"
                        className="filter-sidebar__range-input"
                    />
                    <span className="filter-sidebar__range-sep">—</span>
                    <input
                        id="flt-maxPrice"
                        type="number"
                        placeholder="Đến"
                        value={filters.maxPrice || ''}
                        onChange={handle('maxPrice')}
                        min="0"
                        className="filter-sidebar__range-input"
                    />
                </div>
            </fieldset>
        </div>
    );

    return (
        <>
            <button
                type="button"
                className="filter-sidebar__toggle"
                onClick={() => setMobileOpen(true)}
                aria-label="Mở bộ lọc"
            >
                ☰ Bộ lọc
            </button>

            {/* Desktop sidebar */}
            <aside className="filter-sidebar filter-sidebar--desktop">
                {body}
            </aside>

            {/* Mobile drawer */}
            {mobileOpen && (
                <div className="filter-drawer" role="dialog" aria-modal="true">
                    <div
                        className="filter-drawer__backdrop"
                        onClick={() => setMobileOpen(false)}
                    />
                    <div className="filter-drawer__panel">
                        <button
                            type="button"
                            className="filter-drawer__close"
                            onClick={() => setMobileOpen(false)}
                            aria-label="Đóng"
                        >
                            ✕
                        </button>
                        {body}
                        <button
                            type="button"
                            className="filter-drawer__apply"
                            onClick={() => setMobileOpen(false)}
                        >
                            Áp dụng
                        </button>
                    </div>
                </div>
            )}
        </>
    );
}

FilterSidebar.propTypes = {
    filters: PropTypes.object.isRequired,
    categories: PropTypes.array,
    onChange: PropTypes.func.isRequired,
    onReset: PropTypes.func.isRequired,
};

function Select({ id, label, value, onChange, options, placeholder }) {
    return (
        <div className="filter-sidebar__field">
            <label htmlFor={id} className="filter-sidebar__label">{label}</label>
            <select id={id} value={value} onChange={onChange} className="filter-sidebar__select">
                <option value="">{placeholder}</option>
                {options.map((o) => (
                    <option key={o.value} value={o.value}>{o.label}</option>
                ))}
            </select>
        </div>
    );
}