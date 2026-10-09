import { Link } from 'react-router-dom';
import LogoSvg from '../assets/logo.svg';
import './Footer.css';

/**
 * Footer — global site footer matching the woodfurni brand.
 *
 * Layout (4 columns):
 *   [Brand logo + description + socials]
 *   [Sản phẩm links]
 *   [Liên hệ info]
 *   [Newsletter + Register CTA]
 *
 * Responsive: 2 cols on medium, 1 col on mobile.
 */
export default function Footer() {
    return (
        <footer className="footer" aria-label="Footer">
            <div className="footer__inner">

                {/* ── Col 1: Brand ─────────────────────────────────────── */}
                <div className="footer__brand">
                    <Link to="/" className="footer__logo" aria-label="Woodfurni — Trang chủ">
                        <img
                            src={LogoSvg}
                            alt="Woodfurni"
                            className="footer__logo-img"
                        />
                        <span className="footer__logo-mark">WOOD</span>
                        <span className="footer__logo-text">FURNI</span>
                    </Link>

                    <p className="footer__desc">
                        Mộc Việt Furniture — chuyên cung cấp đồ gỗ nội ngoại thất
                        cao cấp. Sản phẩm được làm từ gỗ tự nhiên, tay nghề thủ công
                        tinh xảo, mang đậm hơi thở Việt Nam.
                    </p>

                    <div className="footer__socials" aria-label="Mạng xã hội">
                        <a
                            href="https://facebook.com"
                            target="_blank"
                            rel="noopener noreferrer"
                            className="footer__social-btn"
                            aria-label="Facebook"
                        >
                            📘
                        </a>
                        <a
                            href="https://instagram.com"
                            target="_blank"
                            rel="noopener noreferrer"
                            className="footer__social-btn"
                            aria-label="Instagram"
                        >
                            📷
                        </a>
                        <a
                            href="https://youtube.com"
                            target="_blank"
                            rel="noopener noreferrer"
                            className="footer__social-btn"
                            aria-label="YouTube"
                        >
                            📺
                        </a>
                    </div>
                </div>

                {/* ── Col 2: Sản phẩm ─────────────────────────────────── */}
                <div>
                    <h3 className="footer__col-title">Sản phẩm</h3>
                    <ul className="footer__list">
                        <li>
                            <Link to="/products?environment=INDOOR">
                                Nội thất
                            </Link>
                        </li>
                        <li>
                            <Link to="/products?environment=OUTDOOR">
                                Ngoại thất
                            </Link>
                        </li>
                        <li>
                            <Link to="/products">
                                Tất cả sản phẩm
                            </Link>
                        </li>
                    </ul>
                </div>

                {/* ── Col 3: Liên hệ ──────────────────────────────────── */}
                <div>
                    <h3 className="footer__col-title">Liên hệ</h3>
                    <ul className="footer__list">
                        <li className="footer__contact-item">
                            <span className="footer__contact-icon" aria-hidden="true">
                                📍
                            </span>
                            <span className="footer__contact-value">
                                123 Đường Nguyễn Trãi,<br />
                                Quận 1, TP. Hồ Chí Minh
                            </span>
                        </li>
                        <li className="footer__contact-item">
                            <span className="footer__contact-icon" aria-hidden="true">
                                📞
                            </span>
                            <a
                                href="tel:02812345678"
                                className="footer__contact-value"
                                style={{ color: 'inherit', textDecoration: 'none' }}
                            >
                                028 1234 5678
                            </a>
                        </li>
                        <li className="footer__contact-item">
                            <span className="footer__contact-icon" aria-hidden="true">
                                ✉️
                            </span>
                            <a
                                href="mailto:contact@woodfurni.vn"
                                className="footer__contact-value"
                                style={{ color: 'inherit', textDecoration: 'none' }}
                            >
                                contact@woodfurni.vn
                            </a>
                        </li>
                        <li className="footer__contact-item">
                            <span className="footer__contact-icon" aria-hidden="true">
                                🕐
                            </span>
                            <span className="footer__contact-value">
                                T2 – T7: 8h00 – 19h00<br />
                                CN: 9h00 – 17h00
                            </span>
                        </li>
                    </ul>
                </div>

                {/* ── Col 4: Đăng ký + Newsletter ───────────────────── */}
                <div className="footer__newsletter-col">
                    <div className="footer__register-cta">
                        <h3 className="footer__register-cta-title">
                            Tham gia WOODFURNI ngay hôm nay
                        </h3>
                        <p className="footer__register-cta-desc">
                            Đăng ký tài khoản để nhận ưu đãi độc quyền, theo dõi đơn
                            hàng và trải nghiệm mua sắm nhanh chóng hơn.
                        </p>
                        <Link
                            to="/register"
                            className="footer__register-btn"
                        >
                            <span>Đăng ký ngay</span>
                            <span aria-hidden="true">→</span>
                        </Link>
                    </div>
                </div>

            </div>

            {/* ── Divider ──────────────────────────────────────────── */}
            <div className="footer__divider" role="separator" />

            {/* ── Bottom bar ───────────────────────────────────────── */}
            <div className="footer__bottom">
                <p className="footer__copyright">
                    © {new Date().getFullYear()} WOODFURNI. Mọi quyền được bảo lưu.
                </p>
                <nav className="footer__legal-links" aria-label="Pháp lý">
                    <a href="/privacy">Chính sách bảo mật</a>
                    <a href="/terms">Điều khoản sử dụng</a>
                    <a href="/shipping">Chính sách vận chuyển</a>
                </nav>
            </div>
        </footer>
    );
}
