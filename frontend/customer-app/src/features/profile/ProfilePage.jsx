import { useState, useEffect } from 'react';
import { useAuth } from '../../contexts/AuthContext.jsx';
import { authApi } from '../../services/apiAuth.js';
import { usersApi } from '../../services/apiUsers.js';
import './ProfilePage.css';

/**
 * ProfilePage — trang hồ sơ cá nhân.
 *
 * Layout:
 *   - Sidebar: tabs (Thông tin, Sổ địa chỉ, Đổi mật khẩu)
 *   - Main content: panel theo tab
 *
 * Tab "Thông tin cá nhân":
 *   - View mode (mặc định): hiển thị họ tên, email, SĐT, địa chỉ mặc định
 *   - Edit mode: form sửa fullName + phone (email không đổi được)
 *
 * Tab "Sổ địa chỉ":
 *   - Danh sách địa chỉ giao hàng của user (qua usersApi)
 *
 * Tab "Đổi mật khẩu":
 *   - Form current + new + confirm, gọi authApi.changePassword
 */
export default function ProfilePage() {
    const { user } = useAuth();
    const [activeTab, setActiveTab] = useState('info');

    if (!user) {
        return (
            <div className="profile-page">
                <p>Đang tải thông tin người dùng...</p>
            </div>
        );
    }

    return (
        <div className="profile-page">
            <div className="container">
                <h1 className="profile-page__title">Tài khoản của tôi</h1>

                <div className="profile-page__layout">
                    <aside className="profile-page__sidebar">
                        <div className="profile-page__avatar-block">
                            <div className="profile-page__avatar-circle">
                                {getInitials(user.fullName || user.email)}
                            </div>
                            <div className="profile-page__avatar-name">
                                {user.fullName || 'Khách hàng'}
                            </div>
                            <div className="profile-page__avatar-email">
                                {user.email}
                            </div>
                        </div>

                        <nav className="profile-page__nav">
                            <button
                                type="button"
                                className={`profile-page__nav-item ${activeTab === 'info' ? 'is-active' : ''}`}
                                onClick={() => setActiveTab('info')}
                            >
                                <span aria-hidden="true">👤</span> Thông tin cá nhân
                            </button>
                            <button
                                type="button"
                                className={`profile-page__nav-item ${activeTab === 'addresses' ? 'is-active' : ''}`}
                                onClick={() => setActiveTab('addresses')}
                            >
                                <span aria-hidden="true">📍</span> Sổ địa chỉ
                            </button>
                            <button
                                type="button"
                                className={`profile-page__nav-item ${activeTab === 'security' ? 'is-active' : ''}`}
                                onClick={() => setActiveTab('security')}
                            >
                                <span aria-hidden="true">🔒</span> Đổi mật khẩu
                            </button>
                        </nav>
                    </aside>

                    <main className="profile-page__content">
                        {activeTab === 'info' && <InfoTab />}
                        {activeTab === 'addresses' && <AddressesTab />}
                        {activeTab === 'security' && <SecurityTab />}
                    </main>
                </div>
            </div>
        </div>
    );
}

// ── Tab: Thông tin cá nhân ──────────────────────────────────────────────────

function InfoTab() {
    const { user, setUser } = useAuth();
    const [isEditing, setIsEditing] = useState(false);
    const [fullName, setFullName] = useState(user?.fullName || '');
    const [phone, setPhone] = useState(user?.phone || '');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState(null);
    const [success, setSuccess] = useState(false);

    // Default address (most recently created)
    const [defaultAddress, setDefaultAddress] = useState(null);
    const [loadingAddr, setLoadingAddr] = useState(true);

    useEffect(() => {
        let cancelled = false;
        (async () => {
            try {
                const list = await usersApi.listAddresses();
                if (!cancelled) {
                    const sorted = [...(list || [])].sort((a, b) => {
                        const at = a.createdAt ? new Date(a.createdAt).getTime() : 0;
                        const bt = b.createdAt ? new Date(b.createdAt).getTime() : 0;
                        return bt - at;
                    });
                    setDefaultAddress(sorted[0] || null);
                }
            } catch {
                // ignore
            } finally {
                if (!cancelled) setLoadingAddr(false);
            }
        })();
        return () => { cancelled = true; };
    }, []);

    const handleEdit = () => {
        setFullName(user?.fullName || '');
        setPhone(user?.phone || '');
        setError(null);
        setSuccess(false);
        setIsEditing(true);
    };

    const handleCancel = () => {
        setIsEditing(false);
        setError(null);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError(null);
        setSuccess(false);

        if (!fullName.trim()) {
            setError('Vui lòng nhập họ tên');
            return;
        }

        setSaving(true);
        try {
            const updated = await authApi.updateProfile({
                fullName: fullName.trim(),
                phone: phone.trim() || null,
            });
            setSuccess(true);
            setIsEditing(false);
            // Refresh auth context state via setUser
            if (typeof setUser === 'function') setUser(updated);
        } catch (err) {
            const msg = err?.response?.data?.message || err?.message || 'Cập nhật thất bại';
            setError(msg);
        } finally {
            setSaving(false);
        }
    };

    return (
        <section className="profile-section">
            <header className="profile-section__header">
                <h2 className="profile-section__title">Thông tin cá nhân</h2>
                {!isEditing && (
                    <button type="button" className="profile-btn profile-btn--ghost" onClick={handleEdit}>
                        Chỉnh sửa
                    </button>
                )}
            </header>

            {success && !isEditing && (
                <div className="profile-alert profile-alert--success">
                    Cập nhật thông tin thành công.
                </div>
            )}

            {!isEditing ? (
                <dl className="profile-info-grid">
                    <InfoItem label="Họ tên" value={user?.fullName || '—'} />
                    <InfoItem label="Email" value={user?.email || '—'} muted="(không thể thay đổi)" />
                    <InfoItem label="Số điện thoại" value={user?.phone || '—'} />
                    <InfoItem
                        label="Địa chỉ giao hàng mặc định"
                        value={
                            loadingAddr
                                ? 'Đang tải...'
                                : defaultAddress
                                    ? formatAddress(defaultAddress)
                                    : 'Chưa có địa chỉ mặc định'
                        }
                        multiline
                    />
                </dl>
            ) : (
                <form className="profile-form" onSubmit={handleSubmit}>
                    {error && (
                        <div className="profile-alert profile-alert--error">{error}</div>
                    )}

                    <div className="profile-form__row">
                        <label className="profile-form__label">Email</label>
                        <input
                            type="email"
                            className="profile-form__input profile-form__input--readonly"
                            value={user?.email || ''}
                            readOnly
                            disabled
                        />
                        <small className="profile-form__hint">Email không thể thay đổi.</small>
                    </div>

                    <div className="profile-form__row">
                        <label className="profile-form__label" htmlFor="fullName">
                            Họ tên <span className="profile-form__required">*</span>
                        </label>
                        <input
                            id="fullName"
                            type="text"
                            className="profile-form__input"
                            value={fullName}
                            onChange={(e) => setFullName(e.target.value)}
                            placeholder="Nhập họ và tên"
                            maxLength={120}
                            required
                        />
                    </div>

                    <div className="profile-form__row">
                        <label className="profile-form__label" htmlFor="phone">
                            Số điện thoại
                        </label>
                        <input
                            id="phone"
                            type="tel"
                            className="profile-form__input"
                            value={phone}
                            onChange={(e) => setPhone(e.target.value)}
                            placeholder="Nhập số điện thoại"
                            maxLength={20}
                        />
                    </div>

                    <div className="profile-form__actions">
                        <button
                            type="button"
                            className="profile-btn profile-btn--ghost"
                            onClick={handleCancel}
                            disabled={saving}
                        >
                            Hủy
                        </button>
                        <button
                            type="submit"
                            className="profile-btn profile-btn--primary"
                            disabled={saving}
                        >
                            {saving ? 'Đang lưu...' : 'Lưu thay đổi'}
                        </button>
                    </div>
                </form>
            )}
        </section>
    );
}

// ── Tab: Sổ địa chỉ ─────────────────────────────────────────────────────────

function AddressesTab() {
    const [addresses, setAddresses] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const loadAddresses = async () => {
        setLoading(true);
        setError(null);
        try {
            const list = await usersApi.listAddresses();
            setAddresses(list || []);
        } catch (err) {
            setError(err?.message || 'Không tải được danh sách địa chỉ');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadAddresses();
    }, []);

    return (
        <section className="profile-section">
            <header className="profile-section__header">
                <h2 className="profile-section__title">Sổ địa chỉ giao hàng</h2>
            </header>

            {error && <div className="profile-alert profile-alert--error">{error}</div>}

            {loading ? (
                <p>Đang tải...</p>
            ) : addresses.length === 0 ? (
                <p className="profile-empty">
                    Bạn chưa có địa chỉ giao hàng nào. Địa chỉ sẽ được lưu khi bạn đặt đơn hàng đầu tiên.
                </p>
            ) : (
                <ul className="profile-address-list">
                    {addresses.map((addr) => (
                        <li key={addr.id} className="profile-address-card">
                            <div className="profile-address-card__head">
                                <strong>{addr.label || 'Địa chỉ'}</strong>
                                {addr.isDefault && (
                                    <span className="profile-address-card__badge">Mặc định</span>
                                )}
                            </div>
                            <div className="profile-address-card__body">
                                <div>{addr.line1 || '—'}</div>
                                <div>
                                    {[addr.ward, addr.district, addr.city]
                                        .filter(Boolean)
                                        .join(', ') || '—'}
                                </div>
                                {addr.phone && (
                                    <div className="profile-address-card__phone">
                                        SĐT: {addr.phone}
                                    </div>
                                )}
                            </div>
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
}

// ── Tab: Đổi mật khẩu ───────────────────────────────────────────────────────

function SecurityTab() {
    const [currentPassword, setCurrentPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmNewPassword, setConfirmNewPassword] = useState('');
    const [showCurrent, setShowCurrent] = useState(false);
    const [showNew, setShowNew] = useState(false);
    const [showConfirm, setShowConfirm] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState(null);
    const [success, setSuccess] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError(null);
        setSuccess(false);

        if (!currentPassword || !newPassword || !confirmNewPassword) {
            setError('Vui lòng điền đầy đủ các trường');
            return;
        }
        if (newPassword.length < 6) {
            setError('Mật khẩu mới phải có ít nhất 6 ký tự');
            return;
        }
        if (newPassword !== confirmNewPassword) {
            setError('Mật khẩu mới và xác nhận mật khẩu không khớp');
            return;
        }
        if (newPassword === currentPassword) {
            setError('Mật khẩu mới phải khác mật khẩu hiện tại');
            return;
        }

        setSaving(true);
        try {
            await authApi.changePassword({
                currentPassword,
                newPassword,
                confirmNewPassword,
            });
            setSuccess(true);
            setCurrentPassword('');
            setNewPassword('');
            setConfirmNewPassword('');
        } catch (err) {
            const msg = err?.response?.data?.message || err?.message || 'Đổi mật khẩu thất bại';
            setError(msg);
        } finally {
            setSaving(false);
        }
    };

    return (
        <section className="profile-section">
            <header className="profile-section__header">
                <h2 className="profile-section__title">Đổi mật khẩu</h2>
            </header>

            {success && (
                <div className="profile-alert profile-alert--success">
                    Đổi mật khẩu thành công. Bạn có thể tiếp tục sử dụng tài khoản với mật khẩu mới.
                </div>
            )}

            {error && <div className="profile-alert profile-alert--error">{error}</div>}

            <form className="profile-form" onSubmit={handleSubmit}>
                <PasswordField
                    id="currentPassword"
                    label="Mật khẩu hiện tại"
                    value={currentPassword}
                    onChange={setCurrentPassword}
                    show={showCurrent}
                    onToggle={() => setShowCurrent(!showCurrent)}
                    required
                />

                <PasswordField
                    id="newPassword"
                    label="Mật khẩu mới"
                    value={newPassword}
                    onChange={setNewPassword}
                    show={showNew}
                    onToggle={() => setShowNew(!showNew)}
                    required
                    hint="Ít nhất 6 ký tự."
                />

                <PasswordField
                    id="confirmNewPassword"
                    label="Xác nhận mật khẩu mới"
                    value={confirmNewPassword}
                    onChange={setConfirmNewPassword}
                    show={showConfirm}
                    onToggle={() => setShowConfirm(!showConfirm)}
                    required
                />

                <div className="profile-form__actions">
                    <button
                        type="submit"
                        className="profile-btn profile-btn--primary"
                        disabled={saving}
                    >
                        {saving ? 'Đang cập nhật...' : 'Đổi mật khẩu'}
                    </button>
                </div>
            </form>
        </section>
    );
}

// ── Helpers ──────────────────────────────────────────────────────────────────

function InfoItem({ label, value, muted, multiline }) {
    return (
        <div className="profile-info-item">
            <dt className="profile-info-item__label">{label}</dt>
            <dd
                className={`profile-info-item__value ${multiline ? 'profile-info-item__value--multiline' : ''}`}
            >
                {value}
                {muted && <span className="profile-info-item__muted"> {muted}</span>}
            </dd>
        </div>
    );
}

function PasswordField({ id, label, value, onChange, show, onToggle, required, hint }) {
    return (
        <div className="profile-form__row">
            <label className="profile-form__label" htmlFor={id}>
                {label} {required && <span className="profile-form__required">*</span>}
            </label>
            <div className="profile-form__password">
                <input
                    id={id}
                    type={show ? 'text' : 'password'}
                    className="profile-form__input"
                    value={value}
                    onChange={(e) => onChange(e.target.value)}
                    required={required}
                    autoComplete="off"
                />
                <button
                    type="button"
                    className="profile-form__toggle"
                    onClick={onToggle}
                    aria-label={show ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                >
                    {show ? '🙈' : '👁'}
                </button>
            </div>
            {hint && <small className="profile-form__hint">{hint}</small>}
        </div>
    );
}

function getInitials(name) {
    if (!name) return '?';
    const parts = String(name).trim().split(/\s+/);
    if (parts.length === 0) return '?';
    if (parts.length === 1) return parts[0].charAt(0).toUpperCase();
    return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
}

function formatAddress(addr) {
    const parts = [
        addr.line1,
        addr.ward,
        addr.district,
        addr.city,
    ].filter(Boolean);
    return parts.join(', ') || '—';
}