import PropTypes from 'prop-types';
import { Link } from 'react-router-dom';
import { formatCurrency } from '../../utils/format.js';
import './ProductCard.css';

/**
 * ProductCard — used on the listing grid and Home featured row.
 *
 * Shows image, name, price (or salePrice + strike-through price), star rating.
 * Whole card is a <Link> to the detail page; the "Xem chi tiết" link is
 * duplicated for keyboard / screen-reader users.
 */
export default function ProductCard({ product }) {
    if (!product) return null;

    const displayPrice =
        product.salePrice != null && Number(product.salePrice) > 0
            ? product.salePrice
            : product.price;

    const hasSale =
        product.salePrice != null &&
        Number(product.salePrice) > 0 &&
        Number(product.salePrice) < Number(product.price);

    const imageUrl =
        product.images && product.images.length > 0
            ? product.images[0]
            : '/placeholder-product.svg';

    const rating = Number(product.ratingAverage) || 0;
    const ratingCount = product.ratingCount || 0;

    return (
        <article className="product-card">
            <Link to={`/products/${product.id}`} className="product-card__media-link">
                <div className="product-card__media">
                    <img
                        src={imageUrl}
                        alt={product.name}
                        loading="lazy"
                        onError={(e) => {
                            e.currentTarget.src = '/placeholder-product.svg';
                        }}
                    />
                    {hasSale && <span className="product-card__badge">SALE</span>}
                    {product.environment && product.environment !== 'BOTH' && (
                        <span className="product-card__env">{product.environment}</span>
                    )}
                </div>
            </Link>

            <div className="product-card__body">
                <div className="product-card__rating">
                    <Stars value={rating} />
                    <span className="product-card__rating-count">
                        {ratingCount > 0 ? `(${ratingCount})` : 'Mới'}
                    </span>
                </div>

                <Link to={`/products/${product.id}`} className="product-card__name">
                    {product.name}
                </Link>

                {product.categoryName && (
                    <p className="product-card__category">{product.categoryName}</p>
                )}

                <div className="product-card__price-row">
                    <span className="product-card__price">{formatCurrency(displayPrice)}</span>
                    {hasSale && (
                        <span className="product-card__price-old">
                            {formatCurrency(product.price)}
                        </span>
                    )}
                </div>

                <Link to={`/products/${product.id}`} className="product-card__cta">
                    Xem chi tiết
                </Link>
            </div>
        </article>
    );
}

ProductCard.propTypes = {
    product: PropTypes.object.isRequired,
};

function Stars({ value }) {
    const filled = Math.round(value);
    return (
        <span className="stars" aria-label={`Đánh giá ${value} / 5`}>
            {[1, 2, 3, 4, 5].map((i) => (
                <span
                    key={i}
                    className={`stars__item ${i <= filled ? 'is-filled' : ''}`}
                    aria-hidden="true"
                >
                    ★
                </span>
            ))}
        </span>
    );
}