import { useEffect } from 'react';
import useProducts from '../../hooks/useProducts.js';
import useCategories from '../../hooks/useCategories.js';
import usePageTitle from '../../hooks/usePageTitle.js';
import { SORT_OPTIONS } from '../../utils/catalogMeta.js';
import FilterSidebar from './FilterSidebar.jsx';
import ProductGrid from './ProductGrid.jsx';
import Pagination from './Pagination.jsx';
import './ProductListPage.css';

/**
 * ProductListPage — GET /products?keyword=&category=&environment=&room=
 *   &woodType=&minPrice=&maxPrice=&sort=&page=&size=
 *
 * Layout (desktop):
 *   ┌────────────┬──────────────────────────┐
 *   │  sidebar   │   toolbar (sort + count) │
 *   │  (filters) │   grid (4 columns)       │
 *   │  (sticky)  │   pagination             │
 *   └────────────┴──────────────────────────┘
 *
 * Layout (mobile): sidebar collapses to a slide-in drawer triggered by
 * the floating "Bộ lọc" button.
 */
export default function ProductListPage() {
    usePageTitle('Sản phẩm');

    const {
        items,
        totalElements,
        totalPages,
        page,
        loading,
        error,
        filters,
        setFilters,
        setPage,
        setSort,
        resetFilters,
    } = useProducts();

    const { data: categories } = useCategories();

    useEffect(() => {
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }, [page, filters.keyword, filters.environment, filters.room, filters.woodType, filters.minPrice, filters.maxPrice]);

    return (
        <div className="container product-list-page">
            <header className="product-list-page__header">
                <h1 className="product-list-page__title">Sản phẩm</h1>
                <p className="product-list-page__count">
                    {loading ? 'Đang tải…' : `${totalElements} sản phẩm`}
                </p>
            </header>

            <div className="product-list-page__layout">
                <FilterSidebar
                    filters={filters}
                    categories={categories}
                    onChange={setFilters}
                    onReset={resetFilters}
                />

                <section className="product-list-page__main">
                    <div className="product-list-page__toolbar">
                        <label className="product-list-page__sort">
                            <span>Sắp xếp:</span>
                            <select
                                value={filters.sort}
                                onChange={(e) => setSort(e.target.value)}
                            >
                                {SORT_OPTIONS.map((o) => (
                                    <option key={o.value} value={o.value}>
                                        {o.label}
                                    </option>
                                ))}
                            </select>
                        </label>
                    </div>

                    <ProductGrid products={items} loading={loading} error={error} />

                    <Pagination
                        page={page}
                        totalPages={totalPages}
                        onChange={setPage}
                    />
                </section>
            </div>
        </div>
    );
}