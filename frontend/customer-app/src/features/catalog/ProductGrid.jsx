import PropTypes from 'prop-types';
import ProductCard from './ProductCard.jsx';
import './ProductGrid.css';

/**
 * Responsive product grid:
 *   - mobile  (<=640px):  2 columns
 *   - tablet  (641–1024): 3 columns
 *   - desktop (>=1025):   4 columns
 */
export default function ProductGrid({ products, loading, error }) {
    if (loading) {
        return (
            <div className="product-grid product-grid--skeleton">
                {Array.from({ length: 8 }).map((_, i) => (
                    <div key={i} className="product-grid__skeleton" />
                ))}
            </div>
        );
    }

    if (error) {
        return (
            <div className="product-grid__empty product-grid__empty--error">
                <p>Không thể tải sản phẩm. Vui lòng thử lại.</p>
                <p className="product-grid__empty-hint">
                    {error?.message || 'Đã xảy ra lỗi'}
                </p>
            </div>
        );
    }

    if (!products || products.length === 0) {
        return (
            <div className="product-grid__empty">
                <p>Không có sản phẩm nào phù hợp với bộ lọc hiện tại.</p>
            </div>
        );
    }

    return (
        <div className="product-grid">
            {products.map((p) => (
                <ProductCard key={p.id} product={p} />
            ))}
        </div>
    );
}

ProductGrid.propTypes = {
    products: PropTypes.array,
    loading: PropTypes.bool,
    error: PropTypes.any,
};