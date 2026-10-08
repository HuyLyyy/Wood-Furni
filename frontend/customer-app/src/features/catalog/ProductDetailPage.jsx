import { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { catalogApi } from '../../services/apiCatalog.js';
import { reviewsApi } from '../../services/apiReviews.js';
import { formatCurrency } from '../../utils/format.js';
import { envLabel, roomLabel } from '../../utils/catalogMeta.js';
import { Button } from '../../components/index.js';
import { useAuth } from '../../contexts/AuthContext.jsx';
import { useCart } from '../../contexts/CartContext.jsx';
import usePageTitle from '../../hooks/usePageTitle.js';
import useRelatedProducts from '../../hooks/useRelatedProducts.js';
import './ProductDetailPage.css';

/**
 * ProductDetailPage — GET /products/{id} + GET /products/{id}/reviews
 *
 * Sections:
 *   1. Gallery (left, 60%)   +   Info panel (right, 40%) — desktop
 *   2. Description + specs + warranty
 *   3. Reviews (paginated)
 *   4. Add to Cart / Buy Now buttons
 *      (Add-to-cart is wired to a local event the future CartContext will
 *       subscribe to; for now it shows a "Coming soon" toast so the user
 *       can see the click registered.)
 */
export default function ProductDetailPage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const { isAuthenticated } = useAuth();
    const { addItem, actionBusy } = useCart();

    const [product, setProduct] = useState(null);
    const [reviews, setReviews] = useState([]);
    const [ratingStats, setRatingStats] = useState({ average: 0, count: 0 });
    const [activeImage, setActiveImage] = useState(0);
    const [quantity, setQuantity] = useState(1);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    usePageTitle(product?.name || 'Sản phẩm');

    // Fetch related products keyed on the current product id.
    const { items: relatedItems, loading: relatedLoading } = useRelatedProducts(
        product?.id,
        8
    );

    // Review mutation state. editingId: which review is being edited (null if
    // none). pendingDeleteId: which review is waiting for a delete confirm.
    const [editingId, setEditingId] = useState(null);
    const [editForm, setEditForm] = useState({ rating: 5, comment: '' });
    const [reviewBusy, setReviewBusy] = useState(false);
    const [pendingDeleteId, setPendingDeleteId] = useState(null);
    const [reviewError, setReviewError] = useState(null);

    const startEdit = (review) => {
        setReviewError(null);
        setEditingId(review.id);
        setEditForm({
            rating: review.rating || 5,
            comment: review.comment || '',
        });
    };

    const cancelEdit = () => {
        setEditingId(null);
        setEditForm({ rating: 5, comment: '' });
    };

    const submitEdit = async (reviewId) => {
        if (!editForm.rating) {
            toast.error('Vui lòng chọn số sao');
            return;
        }
        if (editForm.comment && editForm.comment.length > 2000) {
            toast.error('Bình luận không được vượt quá 2000 ký tự');
            return;
        }
        setReviewBusy(true);
        try {
            const updated = await reviewsApi.updateReview(reviewId, {
                rating: editForm.rating,
                comment: editForm.comment || '',
            });
            setReviews((prev) => prev.map((r) => (r.id === reviewId ? updated : r)));
            // Refresh rating stats since the rating may have changed.
            setRatingStats((prev) => {
                const oldRating = reviews.find((r) => r.id === reviewId)?.rating || 0;
                const sum = (prev.average || 0) * (prev.count || 0) - oldRating + (updated.rating || 0);
                const count = prev.count || 0;
                const newAverage = count > 0 ? Math.round((sum / count) * 10) / 10 : 0;
                return { ...prev, average: newAverage };
            });
            toast.success('Đã cập nhật đánh giá');
            setEditingId(null);
        } catch (err) {
            // toast already shown by apiClient interceptor
        } finally {
            setReviewBusy(false);
        }
    };

    const confirmDelete = async (reviewId) => {
        setReviewBusy(true);
        try {
            await reviewsApi.deleteReview(reviewId);
            setReviews((prev) => prev.filter((r) => r.id !== reviewId));
            setRatingStats((prev) => ({
                ...prev,
                count: Math.max(0, (prev.count || 1) - 1),
            }));
            toast.success('Đã xoá đánh giá');
            setPendingDeleteId(null);
        } catch (err) {
            // toast already shown by apiClient interceptor
        } finally {
            setReviewBusy(false);
        }
    };

    // -------- fetch product + first page of reviews --------
    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        setError(null);

        // ratingAverage / ratingCount come ONLY from the reviews endpoint.
        // We deliberately do NOT fall back to prod.ratingCount because that
        // field can carry stale seed values (e.g. 12) that have no real
        // reviews behind them, which makes the "(12) đánh giá" badge lie.
        // When there are no published reviews the backend returns
        // { ratingAverage: 0, ratingCount: 0 } and we render "0 đánh giá".
        Promise.all([catalogApi.getProductById(id), reviewsApi.listForProduct(id, { page: 0, size: 5 })])
            .then(([prod, reviewsResp]) => {
                if (cancelled) return;
                setProduct(prod);
                setActiveImage(0);
                setReviews(reviewsResp.reviews || []);
                setRatingStats({
                    average: reviewsResp.ratingAverage ?? 0,
                    count: reviewsResp.ratingCount ?? 0,
                });
                setLoading(false);
            })
            .catch((err) => {
                if (cancelled) return;
                setError(err);
                setLoading(false);
            });

        return () => {
            cancelled = true;
        };
    }, [id]);

    if (loading) {
        return (
            <div className="container product-detail__loading">
                <div className="product-detail__skeleton-image" />
                <div className="product-detail__skeleton-info">
                    <div className="product-detail__skeleton-line" />
                    <div className="product-detail__skeleton-line product-detail__skeleton-line--short" />
                    <div className="product-detail__skeleton-line product-detail__skeleton-line--short" />
                </div>
            </div>
        );
    }

    if (error || !product) {
        return (
            <div className="container product-detail__error">
                <h2>Không tìm thấy sản phẩm</h2>
                <p>{error?.message || 'Sản phẩm không tồn tại hoặc đã bị xoá.'}</p>
                <Link to="/products" className="product-detail__back-link">
                    ← Quay lại danh sách
                </Link>
            </div>
        );
    }

    const displayPrice =
        product.salePrice != null && Number(product.salePrice) > 0
            ? product.salePrice
            : product.price;
    const hasSale =
        product.salePrice != null &&
        Number(product.salePrice) > 0 &&
        Number(product.salePrice) < Number(product.price);

    const imageList =
        product.images && product.images.length > 0
            ? product.images
            : ['/placeholder-product.svg'];

    const handleAddToCart = async () => {
        if (!isAuthenticated) {
            toast.error('Vui lòng đăng nhập để thêm vào giỏ hàng');
            navigate('/login', { state: { from: `/products/${id}` } });
            return;
        }
        try {
            await addItem(product.id, quantity);
            toast.success(`Đã thêm ${quantity} sản phẩm vào giỏ hàng`);
        } catch (err) {
            // toast already shown by apiClient interceptor
        }
    };

    const handleBuyNow = async () => {
        if (!isAuthenticated) {
            toast.error('Vui lòng đăng nhập để mua hàng');
            navigate('/login', { state: { from: `/products/${id}` } });
            return;
        }
        try {
            await addItem(product.id, quantity);
            navigate('/cart');
        } catch {
            // toast already shown
        }
    };

    return (
        <div className="container product-detail">
            <nav className="product-detail__breadcrumb" aria-label="Breadcrumb">
                <Link to="/">Trang chủ</Link> /{' '}
                <Link to="/products">Sản phẩm</Link>
                {product.categoryName && (
                    <>
                        {' / '}
                        <Link
                            to={`/products?category=${product.categoryId}`}
                        >
                            {product.categoryName}
                        </Link>
                    </>
                )}
            </nav>

            <div className="product-detail__top">
                {/* -------- Gallery -------- */}
                <section className="product-detail__gallery" aria-label="Hình ảnh sản phẩm">
                    <div className="product-detail__image-main">
                        <img
                            src={imageList[activeImage]}
                            alt={product.name}
                            onError={(e) => {
                                e.currentTarget.src = '/placeholder-product.svg';
                            }}
                        />
                    </div>
                    {imageList.length > 1 && (
                        <div className="product-detail__thumbs">
                            {imageList.map((src, i) => (
                                <button
                                    key={i}
                                    type="button"
                                    className={`product-detail__thumb ${i === activeImage ? 'is-active' : ''}`}
                                    onClick={() => setActiveImage(i)}
                                    aria-label={`Xem ảnh ${i + 1}`}
                                >
                                    <img src={src} alt="" onError={(e) => {
                                        e.currentTarget.src = '/placeholder-product.svg';
                                    }} />
                                </button>
                            ))}
                        </div>
                    )}
                </section>

                {/* -------- Info -------- */}
                <section className="product-detail__info">
                    <div className="product-detail__badges">
                        {product.environment && (
                            <span className={`product-detail__badge product-detail__badge--env product-detail__badge--${product.environment.toLowerCase()}`}>
                                {envLabel(product.environment)}
                            </span>
                        )}
                        {product.room && (
                            <span className="product-detail__badge">{roomLabel(product.room)}</span>
                        )}
                        {hasSale && (
                            <span className="product-detail__badge product-detail__badge--sale">
                                Giảm giá
                            </span>
                        )}
                    </div>

                    <h1 className="product-detail__name">{product.name}</h1>

                    <div className="product-detail__rating">
                        <Stars value={Number(ratingStats.average) || 0} />
                        <span className="product-detail__rating-text">
                            {(Number(ratingStats.average) || 0).toFixed(1)} · {ratingStats.count || 0} đánh giá
                        </span>
                    </div>

                    <div className="product-detail__price-row">
                        <span className="product-detail__price">{formatCurrency(displayPrice)}</span>
                        {hasSale && (
                            <span className="product-detail__price-old">{formatCurrency(product.price)}</span>
                        )}
                    </div>

                    {/* -------- Wood type -------- */}
                    {product.materialNames && product.materialNames.length > 0 && (
                        <div className="product-detail__spec">
                            <span className="product-detail__spec-label">Loại gỗ:</span>
                            <span className="product-detail__spec-value">
                                {product.materialNames.join(', ')}
                            </span>
                        </div>
                    )}

                    {/* -------- Dimensions -------- */}
                    {product.dimensions && (
                        <div className="product-detail__spec">
                            <span className="product-detail__spec-label">Kích thước:</span>
                            <span className="product-detail__spec-value">
                                {product.dimensions.width}×{product.dimensions.height}×
                                {product.dimensions.depth} cm
                            </span>
                        </div>
                    )}

                    {/* -------- Weight / Color / Finish -------- */}
                    <div className="product-detail__specs-grid">
                        {product.weight != null && (
                            <div className="product-detail__spec">
                                <span className="product-detail__spec-label">Khối lượng:</span>
                                <span className="product-detail__spec-value">{product.weight} kg</span>
                            </div>
                        )}
                        {product.color && (
                            <div className="product-detail__spec">
                                <span className="product-detail__spec-label">Màu:</span>
                                <span className="product-detail__spec-value">{product.color}</span>
                            </div>
                        )}
                        {product.finish && (
                            <div className="product-detail__spec">
                                <span className="product-detail__spec-label">Hoàn thiện:</span>
                                <span className="product-detail__spec-value">{product.finish}</span>
                            </div>
                        )}
                    </div>

                    {/* -------- Stock status -------- */}
                    <div className="product-detail__stock">
                        <span className="product-detail__stock-dot" />
                        <span>
                            {product.status === 'OUT_OF_STOCK'
                                ? 'Hết hàng'
                                : 'Còn hàng'}
                        </span>
                    </div>

                    {/* -------- Actions -------- */}
                    <div className="product-detail__quantity">
                        <label htmlFor="qty">Số lượng:</label>
                        <div className="product-detail__qty-control">
                            <button
                                type="button"
                                onClick={() => setQuantity((q) => Math.max(1, q - 1))}
                                aria-label="Giảm"
                                disabled={quantity <= 1}
                            >
                                −
                            </button>
                            <input
                                id="qty"
                                type="number"
                                min="1"
                                value={quantity}
                                onChange={(e) => {
                                    const n = parseInt(e.target.value, 10);
                                    setQuantity(Number.isFinite(n) && n > 0 ? n : 1);
                                }}
                            />
                            <button
                                type="button"
                                onClick={() => setQuantity((q) => q + 1)}
                                aria-label="Tăng"
                            >
                                +
                            </button>
                        </div>
                    </div>

                    <div className="product-detail__actions">
                        <Button
                            variant="primary"
                            size="lg"
                            fullWidth
                            onClick={handleAddToCart}
                            disabled={product.status === 'OUT_OF_STOCK'}
                            loading={actionBusy === `add:${product.id}`}
                        >
                            🛒 Thêm vào giỏ hàng
                        </Button>
                        <Button
                            variant="outline"
                            size="lg"
                            fullWidth
                            onClick={handleBuyNow}
                            disabled={product.status === 'OUT_OF_STOCK'}
                            loading={actionBusy === `add:${product.id}`}
                        >
                            Mua ngay
                        </Button>
                    </div>

                    {product.warranty && (
                        <div className="product-detail__warranty">
                            <span className="product-detail__warranty-icon">🛡</span>
                            Bảo hành: <strong>{product.warranty}</strong>
                        </div>
                    )}
                </section>
            </div>

            {/* -------- Description -------- */}
            {product.description && (
                <section className="product-detail__section">
                    <h2 className="product-detail__section-title">Mô tả sản phẩm</h2>
                    <p className="product-detail__description">{product.description}</p>
                </section>
            )}

            {/* -------- Reviews -------- */}
            <section className="product-detail__section">
                <h2 className="product-detail__section-title">
                    Đánh giá ({ratingStats.count || 0})
                </h2>

                {reviews.length === 0 ? (
                    <p className="product-detail__no-reviews">
                        Sản phẩm chưa có đánh giá nào.
                    </p>
                ) : (
                    <ul className="product-detail__reviews">
                        {reviews.map((r) => {
                            const isOwner = isAuthenticated && user?.id === r.userId;
                            const isEditing = editingId === r.id;
                            return (
                                <li key={r.id} className="product-detail__review">
                                    <div className="product-detail__review-head">
                                        {isEditing ? (
                                            <ReviewStarPicker
                                                value={editForm.rating}
                                                onChange={(v) => setEditForm((f) => ({ ...f, rating: v }))}
                                            />
                                        ) : (
                                            <Stars value={r.rating || 0} />
                                        )}
                                        <span className="product-detail__review-author">
                                            {r.userDisplayName || 'Khách hàng'}
                                        </span>
                                        <span className="product-detail__review-date">
                                            {r.createdAt
                                                ? new Date(r.createdAt).toLocaleDateString('vi-VN')
                                                : ''}
                                        </span>
                                        {isOwner && !isEditing && (
                                            <div className="product-detail__review-actions">
                                                <button
                                                    type="button"
                                                    className="product-detail__review-action"
                                                    onClick={() => startEdit(r)}
                                                >
                                                    Sửa
                                                </button>
                                                <button
                                                    type="button"
                                                    className="product-detail__review-action product-detail__review-action--danger"
                                                    onClick={() => setPendingDeleteId(r.id)}
                                                >
                                                    Xoá
                                                </button>
                                            </div>
                                        )}
                                    </div>

                                    {isEditing ? (
                                        <div className="product-detail__review-edit">
                                            <textarea
                                                className="product-detail__review-textarea"
                                                value={editForm.comment}
                                                onChange={(e) => setEditForm((f) => ({ ...f, comment: e.target.value }))}
                                                maxLength={2000}
                                                rows={4}
                                                placeholder="Chia sẻ trải nghiệm của bạn về sản phẩm..."
                                            />
                                            <div className="product-detail__review-edit-meta">
                                                {editForm.comment.length}/2000
                                            </div>
                                            <div className="product-detail__review-edit-actions">
                                                <Button
                                                    variant="outline"
                                                    size="sm"
                                                    onClick={cancelEdit}
                                                    disabled={reviewBusy}
                                                >
                                                    Huỷ
                                                </Button>
                                                <Button
                                                    variant="primary"
                                                    size="sm"
                                                    onClick={() => submitEdit(r.id)}
                                                    disabled={reviewBusy}
                                                >
                                                    Lưu thay đổi
                                                </Button>
                                            </div>
                                        </div>
                                    ) : (
                                        r.comment && (
                                            <p className="product-detail__review-comment">{r.comment}</p>
                                        )
                                    )}

                                    {pendingDeleteId === r.id && (
                                        <div className="product-detail__review-confirm">
                                            Bạn có chắc chắn muốn xoá đánh giá này?
                                            <div className="product-detail__review-confirm-actions">
                                                <Button
                                                    variant="outline"
                                                    size="sm"
                                                    onClick={() => setPendingDeleteId(null)}
                                                    disabled={reviewBusy}
                                                >
                                                    Huỷ
                                                </Button>
                                                <Button
                                                    variant="primary"
                                                    size="sm"
                                                    onClick={() => confirmDelete(r.id)}
                                                    disabled={reviewBusy}
                                                >
                                                    Xoá
                                                </Button>
                                            </div>
                                        </div>
                                    )}
                                </li>
                            );
                        })}
                    </ul>
                )}
            </section>

            {/* -------- Related products -------- */}
            {(relatedLoading || relatedItems.length > 0) && (
                <section className="product-detail__section product-detail__related">
                    <h2 className="product-detail__section-title">Sản phẩm liên quan</h2>
                    {relatedLoading && relatedItems.length === 0 ? (
                        <div className="product-detail__related-skeleton">
                            {Array.from({ length: 4 }).map((_, i) => (
                                <div key={i} className="product-detail__related-skeleton-card" />
                            ))}
                        </div>
                    ) : (
                        <div className="product-detail__related-grid">
                            {relatedItems.map((rp) => (
                                <RelatedCard key={rp.id} product={rp} />
                            ))}
                        </div>
                    )}
                </section>
            )}
        </div>
    );
}

function RelatedCard({ product }) {
    const displayPrice =
        product.salePrice != null && Number(product.salePrice) > 0
            ? product.salePrice
            : product.price;
    const hasSale =
        product.salePrice != null &&
        Number(product.salePrice) > 0 &&
        Number(product.salePrice) < Number(product.price);
    const cover =
        product.images && product.images.length > 0
            ? product.images[0]
            : '/placeholder-product.svg';

    return (
        <Link
            to={`/products/${product.id}`}
            className="related-card"
            aria-label={product.name}
        >
            <div className="related-card__media">
                <img
                    src={cover}
                    alt={product.name}
                    onError={(e) => {
                        e.currentTarget.src = '/placeholder-product.svg';
                    }}
                    loading="lazy"
                />
                {hasSale && (
                    <span className="related-card__badge related-card__badge--sale">Giảm giá</span>
                )}
                {product.status === 'OUT_OF_STOCK' && (
                    <span className="related-card__badge related-card__badge--oos">Hết hàng</span>
                )}
            </div>
            <div className="related-card__body">
                {product.categoryName && (
                    <div className="related-card__category">{product.categoryName}</div>
                )}
                <h3 className="related-card__name">{product.name}</h3>
                {product.materialNames && product.materialNames.length > 0 && (
                    <div className="related-card__meta">
                        {product.materialNames.join(', ')}
                    </div>
                )}
                <div className="related-card__price">
                    <span className="related-card__price-current">{formatCurrency(displayPrice)}</span>
                    {hasSale && (
                        <span className="related-card__price-old">{formatCurrency(product.price)}</span>
                    )}
                </div>
            </div>
        </Link>
    );
}

function Stars({ value }) {
    const filled = Math.round(value);
    return (
        <span className="stars" aria-label={`${value} / 5`}>
            {[1, 2, 3, 4, 5].map((i) => (
                <span key={i} className={`stars__item ${i <= filled ? 'is-filled' : ''}`}>
                    ★
                </span>
            ))}
        </span>
    );
}

/**
 * Interactive 5-star picker used inside the inline review-edit form.
 * Click a star to set the rating (1..5). Hover preview is a nice-to-have.
 */
function ReviewStarPicker({ value, onChange }) {
    return (
        <div
            className="review-star-picker"
            role="radiogroup"
            aria-label="Chọn số sao"
        >
            {[1, 2, 3, 4, 5].map((i) => (
                <button
                    key={i}
                    type="button"
                    className={`review-star-picker__star ${i <= value ? 'is-filled' : ''}`}
                    onClick={() => onChange(i)}
                    aria-label={`${i} sao`}
                    aria-checked={i === value}
                    role="radio"
                >
                    ★
                </button>
            ))}
        </div>
    );
}