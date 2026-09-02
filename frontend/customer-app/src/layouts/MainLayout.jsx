import { Outlet, Link, NavLink, useNavigate, useLocation } from 'react-router-dom';
import { useSearchParams } from 'react-router-dom';
import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../contexts/AuthContext.jsx';
import { useCart } from '../contexts/CartContext.jsx';
import { useSearchHistory } from '../hooks/useSearchHistory.js';
import LogoSvg from '../assets/logo.svg';
import './MainLayout.css';

/**
 * MainLayout — global shell for the storefront.
 *
 *   ┌─────────────────────────────────────────────┐
 *   │ Header (logo, nav, cart, user menu)         │
 *   ├─────────────────────────────────────────────┤
 *   │                                             │
 *   │  <Outlet />  ← page renders here            │
 *   │                                             │
 *   ├─────────────────────────────────────────────┤
 *   │ Footer                                      │
 *   └─────────────────────────────────────────────┘
 */
export default function MainLayout() {
    const { isAuthenticated, user, logout } = useAuth();
    const navigate = useNavigate();

    const handleLogout = async () => {
        await logout();
        navigate('/', { replace: true });
    };

    return (
        <div className="main-layout">
            <Header
                isAuthenticated={isAuthenticated}
                user={user}
                onLogout={handleLogout}
            />
            <main className="main-layout__content">
                <Outlet />
            </main>
            <Footer />
        </div>
    );
}

function Header({ isAuthenticated, user, onLogout }) {
    const [searchParams, setSearchParams] = useSearchParams();
    const location = useLocation();
    const isOnProducts = location.pathname === '/products';
    const inputRef = useRef(null);
    const searchContainerRef = useRef(null);
    const { history, addTerm, removeTerm, clear } = useSearchHistory();
    const [isHistoryOpen, setIsHistoryOpen] = useState(false);

    // Sync input value when navigating back to home page
    useEffect(() => {
        if (!isOnProducts && inputRef.current) {
            inputRef.current.value = searchParams.get('keyword') || '';
        }
    }, [isOnProducts, searchParams]);

    // Close history dropdown when clicking outside the search box.
    useEffect(() => {
        if (!isHistoryOpen) return;
        const onDocClick = (e) => {
            if (!searchContainerRef.current?.contains(e.target)) {
                setIsHistoryOpen(false);
            }
        };
        document.addEventListener('mousedown', onDocClick);
        return () => document.removeEventListener('mousedown', onDocClick);
    }, [isHistoryOpen]);

    const navigateToKeyword = (q) => {
        const trimmed = (q ?? '').trim();
        if (!trimmed) {
            if (isOnProducts) {
                const next = new URLSearchParams(searchParams);
                next.delete('keyword');
                setSearchParams(next, { replace: false });
            } else {
                window.location.href = '/products';
            }
            return;
        }
        if (isOnProducts) {
            setSearchParams({ keyword: trimmed }, { replace: false });
        } else {
            window.location.href = '/products?keyword=' + encodeURIComponent(trimmed);
        }
    };

    const handleSearch = (e) => {
        // Read the keyword from the input element, not from the event target.
        // When the event is fired by clicking the search button (or its
        // inner icon), `e.target` is the button, which has no `value`
        // property, so `e.target.value.trim()` would throw TypeError and
        // the search would silently no-op. Reading from `inputRef.current`
        // works for both key-down on the input and click on the button.
        const q = (inputRef.current?.value ?? '').trim();
        if (e.key === 'Enter' || e.type === 'click') {
            navigateToKeyword(q);
            if (q) addTerm(q);
            setIsHistoryOpen(false);
        }
    };

    const handleHistoryClick = (term) => {
        if (inputRef.current) inputRef.current.value = term;
        navigateToKeyword(term);
        addTerm(term);
        setIsHistoryOpen(false);
    };

    // ── nav active helpers ─────────────────────────────────────────────
    // Three nav links all live under /products but with different query
    // strings. React Router's NavLink matches by default only on pathname,
    // so all three would receive .active at once. We override `isActive`
    // to compare the full URL (pathname + search) so only the link whose
    // query string matches the current URL is highlighted.
    const currentEnv = searchParams.get('environment');
    const matchExactEnv = (env) =>
        Boolean(isOnProducts) && (env == null || env === '' || currentEnv === env);

    return (
        <header className="header">
            <div className="container header__inner">
                <Link to="/" className="header__logo" aria-label="Woodfurni — Trang chủ">
                    <img src={LogoSvg} alt="Woodfurni" className="header__logo-img" />
                    <span className="header__logo-mark">WOOD</span>
                    <span className="header__logo-text">FURNI</span>
                </Link>

                <nav className="header__nav" aria-label="Primary">
                    <NavLink
                        to="/products?environment=INDOOR"
                        className="header__nav-link"
                        isActive={() => matchExactEnv('INDOOR')}
                    >
                        Nội thất
                    </NavLink>
                    <NavLink
                        to="/products?environment=OUTDOOR"
                        className="header__nav-link"
                        isActive={() => matchExactEnv('OUTDOOR')}
                    >
                        Ngoại thất
                    </NavLink>
                    <NavLink
                        to="/products"
                        end
                        className="header__nav-link"
                        isActive={() => matchExactEnv(null)}
                    >
                        Tất cả sản phẩm
                    </NavLink>
                </nav>

                <div className="header__search" ref={searchContainerRef}>
                    <input
                        type="search"
                        className="header__search-input"
                        placeholder="Tìm sản phẩm..."
                        ref={inputRef}
                        onKeyDown={handleSearch}
                        onFocus={() => setIsHistoryOpen(true)}
                        autoComplete="off"
                    />
                    <button
                        type="button"
                        className="header__search-btn"
                        onClick={handleSearch}
                        aria-label="Tìm kiếm"
                    >
                        🔍
                    </button>

                    {isHistoryOpen && history.length > 0 && (
                        <div className="header__search-history" role="listbox" aria-label="Lịch sử tìm kiếm">
                            <div className="header__search-history-header">
                                <span>Lịch sử tìm kiếm</span>
                                <button
                                    type="button"
                                    className="header__search-history-clear"
                                    onClick={clear}
                                    aria-label="Xóa tất cả lịch sử"
                                >
                                    Xóa tất cả
                                </button>
                            </div>
                            <ul className="header__search-history-list">
                                {history.map((term) => (
                                    <li key={term} className="header__search-history-item">
                                        <button
                                            type="button"
                                            className="header__search-history-term"
                                            onClick={() => handleHistoryClick(term)}
                                            role="option"
                                            aria-selected="false"
                                        >
                                            <span aria-hidden="true" className="header__search-history-icon">🕘</span>
                                            <span className="header__search-history-text">{term}</span>
                                        </button>
                                        <button
                                            type="button"
                                            className="header__search-history-remove"
                                            onClick={(ev) => {
                                                ev.stopPropagation();
                                                removeTerm(term);
                                            }}
                                            aria-label={`Xóa "${term}" khỏi lịch sử`}
                                        >
                                            ×
                                        </button>
                                    </li>
                                ))}
                            </ul>
                        </div>
                    )}
                </div>

                <div className="header__actions">
                    <CartLink />

                    {isAuthenticated ? (
                        <div className="header__user-menu">
                            <span className="header__user-name">
                                {user?.fullName || user?.email || 'Tài khoản'}
                            </span>
                            <Link to="/orders" className="header__menu-link">Đơn hàng</Link>
                            <button type="button" className="header__menu-button" onClick={onLogout}>
                                Đăng xuất
                            </button>
                        </div>
                    ) : (
                        <div className="header__auth">
                            <Link to="/login" className="header__menu-link">Đăng nhập</Link>
                            <Link to="/register" className="header__menu-link header__menu-link--cta">
                                Đăng ký
                            </Link>
                        </div>
                    )}
                </div>
            </div>
        </header>
    );
}

function CartLink() {
    const { itemCount } = useCart();
    return (
        <Link to="/cart" className="header__icon" aria-label={`Giỏ hàng${itemCount > 0 ? ` (${itemCount})` : ''}`}>
            <span className="header__cart-icon">🛒</span>
            {itemCount > 0 && (
                <span className="header__cart-badge" aria-hidden="true">
                    {itemCount > 99 ? '99+' : itemCount}
                </span>
            )}
        </Link>
    );
}

function Footer() {
    return (
        <footer className="footer">
            <div className="container footer__inner">
                <div className="footer__col">
                    <h4 className="footer__title">WOODFURNI</h4>
                    <p className="footer__text">
                        Mộc Việt Furniture — đồ gỗ nội ngoại thất chất lượng cao.
                    </p>
                </div>
                <div className="footer__col">
                    <h4 className="footer__title">Liên kết</h4>
                    <ul className="footer__list">
                        <li><Link to="/products?environment=INDOOR">Nội thất</Link></li>
                        <li><Link to="/products?environment=OUTDOOR">Ngoại thất</Link></li>
                        <li><Link to="/orders">Đơn hàng của tôi</Link></li>
                    </ul>
                </div>
                <div className="footer__col">
                    <h4 className="footer__title">Hỗ trợ</h4>
                    <ul className="footer__list">
                        <li>Hotline: 1900-xxxx</li>
                        <li>Email: support@woodfurni.vn</li>
                    </ul>
                </div>
            </div>
            <div className="footer__bottom">
                <div className="container">
                    © {new Date().getFullYear()} WOODFURNI. All rights reserved.
                </div>
            </div>
        </footer>
    );
}