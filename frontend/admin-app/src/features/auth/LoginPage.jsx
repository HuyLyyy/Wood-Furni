import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { Button, Input } from '../../components/index.js';
import { useAuth } from '../../contexts/AuthContext.jsx';
import './LoginPage.css';

/**
 * LoginPage — admin-app variant.
 *
 * Important: rejects CUSTOMER-role accounts at the FE. The AuthContext.login()
 * also enforces this; we show the same error here if /auth/login somehow
 * returns a CUSTOMER account (defence in depth — though the backend should
 * already accept any role).
 */
export default function LoginPage() {
    const { login } = useAuth();
    const navigate = useNavigate();

    const [form, setForm] = useState({ email: '', password: '' });
    const [errors, setErrors] = useState({});
    const [submitting, setSubmitting] = useState(false);
    const [topError, setTopError] = useState(null);

    const handleChange = (e) => {
        const { name, value } = e.target;
        setForm((prev) => ({ ...prev, [name]: value }));
        if (errors[name]) setErrors((prev) => ({ ...prev, [name]: null }));
        if (topError) setTopError(null);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        const next = {};
        if (!form.email) next.email = 'Email là bắt buộc';
        else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) next.email = 'Email không hợp lệ';
        if (!form.password) next.password = 'Mật khẩu là bắt buộc';
        if (Object.keys(next).length > 0) {
            setErrors(next);
            return;
        }

        setSubmitting(true);
        try {
            await login(form.email.trim(), form.password);
            toast.success('Đăng nhập thành công');
            navigate('/', { replace: true });
        } catch (err) {
            // Most common path: CUSTOMER-role account trying to use admin app
            const msg = err?.message || 'Đăng nhập thất bại';
            if (
                msg.toLowerCase().includes('quyền truy cập') ||
                msg.toLowerCase().includes('unauthor') ||
                msg.toLowerCase().includes('forbidden') ||
                msg.toLowerCase().includes('customer')
            ) {
                setTopError('Tài khoản này không có quyền truy cập Admin Console. Vui lòng dùng tài khoản nhân viên.');
            } else {
                setTopError(msg);
            }
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="login-page">
            <div className="login-card">
                <div className="login-card__brand">
                    <span className="login-card__brand-mark">W</span>
                    <h1 className="login-card__title">WOODFURNI Admin</h1>
                    <p className="login-card__sub">Dành cho nhân viên — Admin, Sales, Warehouse, Content</p>
                </div>

                {topError && (
                    <div className="login-card__alert" role="alert">
                        {topError}
                    </div>
                )}

                <form onSubmit={handleSubmit} noValidate>
                    <Input
                        id="email" name="email" type="email" label="Email"
                        placeholder="staff@woodfurni.vn"
                        value={form.email} onChange={handleChange}
                        error={errors.email} required autoComplete="email"
                    />
                    <Input
                        id="password" name="password" type="password" label="Mật khẩu"
                        placeholder="••••••••"
                        value={form.password} onChange={handleChange}
                        error={errors.password} required autoComplete="current-password"
                    />

                    <Button
                        type="submit" variant="primary" size="lg"
                        fullWidth loading={submitting}
                    >
                        {submitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
                    </Button>
                </form>

                <p className="login-card__hint">
                    Bạn là khách hàng? Truy cập{' '}
                    <a href="http://localhost:5173">WOODFURNI Store</a>.
                </p>

                <p className="login-card__legal">
                    <Link to="/help">Quên mật khẩu?</Link>
                </p>
            </div>
        </div>
    );
}