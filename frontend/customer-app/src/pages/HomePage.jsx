import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { catalogApi } from '@services/apiCatalog.js';
import usePageTitle from '@hooks/usePageTitle.js';
import ProductGrid from '@features/catalog/ProductGrid.jsx';
import './HomePage.css';

/**
 * HomePage
 *
 *   - Hero banner (real product image + gradient overlay + text shadow)
 *   - Category shortcuts (Indoor / Outdoor) with real-image backgrounds
 *   - Featured products (top-rated) + new arrivals (latest)
 *
 * Sections fall back to skeletons while loading so the page never flashes
 * empty space.
 */
export default function HomePage() {
    usePageTitle('Trang chủ');

    const [featured, setFeatured] = useState({ items: [], loading: true, error: null });
    const [latest, setLatest] = useState({ items: [], loading: true, error: null });
    const [categories, setCategories] = useState({ data: [], loading: true });

    useEffect(() => {
        catalogApi
            .searchProducts({ sort: '-ratingAverage', page: 0, size: 8 })
            .then((result) => {
                const items = result.items || [];
                setFeatured({
                    items: items.length > 0 ? items : [],
                    loading: false,
                    error: null,
                });
            })
            .catch((err) =>
                setFeatured({ items: [], loading: false, error: err })
            );

        catalogApi
            .searchProducts({ sort: '-createdAt', page: 0, size: 8 })
            .then((result) =>
                setLatest({ items: result.items || [], loading: false, error: null })
            )
            .catch((err) =>
                setLatest({ items: [], loading: false, error: err })
            );

        catalogApi
            .getCategories()
            .then((data) => setCategories({ data: data || [], loading: false }))
            .catch(() => setCategories({ data: [], loading: false }));
    }, []);

    const indoorRoots = categories.data.filter((c) => c.environment === 'INDOOR');
    const outdoorRoots = categories.data.filter((c) => c.environment === 'OUTDOOR');

    return (
        <div className="home-page">
            {/* ===== Hero ===== */}
            <section className="hero">
                <div className="hero__bg" aria-hidden="true" />
                <div className="container hero__inner">
                    <div className="hero__copy">
                        <span className="hero__eyebrow">Mộc Việt Furniture</span>
                        <h1 className="hero__title">
                            Đồ gỗ <em>nội ngoại thất</em><br />
                            cho không gian sống hiện đại
                        </h1>
                        <p className="hero__sub">
                            Khám phá bộ sưu tập bàn ghế, tủ kệ, giường từ các loại gỗ tự nhiên
                            chất lượng cao: Sồi, Óc chó, Tếch, Tràm, Thông — được tuyển chọn và
                            gia công tinh xảo cho tổ ấm của bạn.
                        </p>
                        <div className="hero__cta">
                            <Link
                                to="/products?environment=INDOOR"
                                className="hero__btn hero__btn--primary"
                            >
                                Khám phá Nội thất
                            </Link>
                            <Link
                                to="/products?environment=OUTDOOR"
                                className="hero__btn hero__btn--outline"
                            >
                                Ngoại thất
                            </Link>
                        </div>
                    </div>

                    <aside className="hero__art" aria-hidden="true">
                        <div className="hero__art-card">
                            <div className="hero__art-stat">
                                <span className="hero__art-stat-num">15+</span>
                                <span className="hero__art-stat-label">
                                    năm kinh nghiệm<br />trong ngành gỗ
                                </span>
                            </div>
                        </div>
                        <div className="hero__art-card hero__art-card--materials">
                            <span className="hero__chip">Gỗ Sồi</span>
                            <span className="hero__chip">Óc chó</span>
                            <span className="hero__chip">Tếch</span>
                            <span className="hero__chip">Tràm</span>
                            <span className="hero__chip">Thông</span>
                        </div>
                        <div className="hero__art-card hero__art-card--quote">
                            "Sản phẩm chắc tay, vân gỗ tự nhiên, giao hàng nhanh."
                        </div>
                    </aside>
                </div>
            </section>

            {/* ===== Category shortcuts ===== */}
            <section className="container home-section">
                <div className="home-section__head">
                    <div>
                        <h2 className="home-section__title">Khám phá theo không gian</h2>
                        <p className="home-section__sub">
                            Chọn không gian phù hợp với phong cách ngôi nhà của bạn
                        </p>
                    </div>
                </div>

                <div className="home-cats">
                    <CategoryTile
                        modifier="indoor"
                        imageUrl="https://images.unsplash.com/photo-1616486338812-3dadae4b4ace?auto=format&fit=crop&w=900&q=80"
                        title="Nội thất"
                        eyebrow="Living · Bedroom · Dining"
                        subtitle="Phòng khách, phòng ngủ, phòng ăn — cho tổ ấm sang trọng"
                        to="/products?environment=INDOOR"
                        roots={indoorRoots}
                    />
                    <CategoryTile
                        modifier="outdoor"
                        imageUrl="https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=900&q=80"
                        title="Ngoại thất"
                        eyebrow="Garden · Patio · Balcony"
                        subtitle="Sân vườn, ban công, patio — bền bỉ với thời tiết"
                        to="/products?environment=OUTDOOR"
                        roots={outdoorRoots}
                    />
                </div>
            </section>

            {/* ===== Featured ===== */}
            <section className="container home-section">
                <div className="home-section__head">
                    <div>
                        <h2 className="home-section__title">Sản phẩm nổi bật</h2>
                        <p className="home-section__sub">
                            Được đánh giá cao nhất bởi khách hàng
                        </p>
                    </div>
                    <Link to="/products?sort=-ratingAverage" className="home-section__link">
                        Xem tất cả
                    </Link>
                </div>
                <ProductGrid
                    products={featured.items}
                    loading={featured.loading}
                    error={featured.error}
                />
            </section>

            {/* ===== Latest ===== */}
            <section className="container home-section">
                <div className="home-section__head">
                    <div>
                        <h2 className="home-section__title">Hàng mới về</h2>
                        <p className="home-section__sub">
                            Những thiết kế mới nhất vừa cập bến showroom
                        </p>
                    </div>
                    <Link to="/products?sort=-createdAt" className="home-section__link">
                        Xem tất cả
                    </Link>
                </div>
                <ProductGrid
                    products={latest.items}
                    loading={latest.loading}
                    error={latest.error}
                />
            </section>
        </div>
    );
}

function CategoryTile({ modifier, imageUrl, title, eyebrow, subtitle, to, roots }) {
    return (
        <div className={`home-cat home-cat--${modifier || 'default'}`}>
            <Link to={to} className="home-cat__banner">
                {/* Real-image background — consistent with hero section */}
                <div
                    className="home-cat__banner-bg"
                    style={{ backgroundImage: `url('${imageUrl}')` }}
                />
                <span className="home-cat__eyebrow">{eyebrow}</span>
                <h3>{title}</h3>
                <p>{subtitle}</p>
            </Link>

            {roots && roots.length > 0 && (
                <ul className="home-cat__children">
                    {roots.flatMap((root) => [
                        <li key={root.id}>
                            <Link to={`/products?category=${root.slug || root.id}`}>
                                {root.name}
                            </Link>
                            {root.children && root.children.length > 0 && (
                                <ul className="home-cat__grandchildren">
                                    {root.children.map((c) => (
                                        <li key={c.id}>
                                            <Link
                                                to={`/products?category=${c.slug || c.id}`}
                                            >
                                                {c.name}
                                            </Link>
                                        </li>
                                    ))}
                                </ul>
                            )}
                        </li>,
                    ])}
                </ul>
            )}
        </div>
    );
}
