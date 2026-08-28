import { useState } from 'react';
import { Outlet, NavLink, useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext.jsx';
import { visibleMenu, roleLabel } from '../utils/roleMeta.js';
import './AdminLayout.css';

/**
 * AdminLayout — dark sidebar + content area.
 *
 *   ┌──────────┬─────────────────────────────────┐
 *   │          │  topbar (user menu, logout)     │
 *   │ sidebar  ├─────────────────────────────────┤
 *   │ (filterd ├                                 │
 *   │  by role)│  <Outlet />  ← page content     │
 *   │          │                                 │
 *   └──────────┴─────────────────────────────────┘
 *
 * The sidebar menu is filtered through `visibleMenu(role)` — see
 * utils/roleMeta.js for the role → menu-items mapping.
 *
 * Mobile: sidebar collapses into a slide-in drawer toggled from the topbar.
 */
export default function AdminLayout() {
    const { user, logout } = useAuth();
    const navigate = useNavigate();
    const [mobileOpen, setMobileOpen] = useState(false);

    const handleLogout = async () => {
        await logout();
        navigate('/login', { replace: true });
    };

    const items = visibleMenu(user?.role);

    return (
        <div className="admin-layout">
            <aside className={`admin-sidebar ${mobileOpen ? 'is-open' : ''}`}>
                <div className="admin-sidebar__brand">
                    <Link to="/" className="admin-sidebar__brand-link">
                        <span className="admin-sidebar__brand-mark">W</span>
                        <span className="admin-sidebar__brand-text">WOODFURNI</span>
                        <span className="admin-sidebar__brand-suffix">Admin</span>
                    </Link>
                </div>

                <nav className="admin-sidebar__nav" aria-label="Sidebar">
                    {items.map((item) => (
                        <NavLink
                            key={item.id}
                            to={item.path}
                            end={item.path === '/'}
                            className={({ isActive }) =>
                                `admin-sidebar__link ${isActive ? 'is-active' : ''}`
                            }
                            onClick={() => setMobileOpen(false)}
                        >
                            <span className="admin-sidebar__icon" aria-hidden="true">{item.icon}</span>
                            <span>{item.label}</span>
                        </NavLink>
                    ))}
                </nav>

                <div className="admin-sidebar__foot">
                    <p className="admin-sidebar__foot-hint">
                        Quyền thật được backend kiểm tra. Menu chỉ để UX.
                    </p>
                </div>
            </aside>

            {mobileOpen && <div className="admin-sidebar__backdrop" onClick={() => setMobileOpen(false)} />}

            <div className="admin-main">
                <header className="admin-topbar">
                    <button
                        type="button"
                        className="admin-topbar__menu-btn"
                        onClick={() => setMobileOpen((s) => !s)}
                        aria-label="Mở menu"
                    >
                        ☰
                    </button>
                    <div className="admin-topbar__title">
                        <span className="admin-topbar__role-pill">{roleLabel(user?.role)}</span>
                    </div>
                    <div className="admin-topbar__user">
                        <span className="admin-topbar__user-name">
                            {user?.fullName || user?.email}
                        </span>
                        <button type="button" className="admin-topbar__logout" onClick={handleLogout}>
                            Đăng xuất
                        </button>
                    </div>
                </header>

                <main className="admin-content">
                    <Outlet />
                </main>
            </div>
        </div>
    );
}