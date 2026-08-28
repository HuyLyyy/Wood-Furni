import { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import toast from 'react-hot-toast';
import { Button, Input } from '../../components/index.js';
import { useAuth } from '../../contexts/AuthContext.jsx';
import { validate, validators } from '../../utils/validators.js';
import './AuthForm.css';

/**
 * LoginPage — POST /auth/login
 * Body: { email, password } (matches backend dto/LoginRequest)
 * On success: AuthContext stores tokens + user, then we redirect to
 * `state.from` (the protected route the user originally wanted) or '/'.
 */
export default function LoginPage() {
    const { login } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();

    const [form, setForm] = useState({ email: '', password: '' });
    const [errors, setErrors] = useState({});
    const [submitting, setSubmitting] = useState(false);

    const handleChange = (e) => {
        const { name, value } = e.target;
        setForm((prev) => ({ ...prev, [name]: value }));
        // Clear field error on edit
        if (errors[name]) setErrors((prev) => ({ ...prev, [name]: null }));
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        const validationErrors = validate(form, {
            email: validators.email,
            password: validators.password,
        });
        if (Object.keys(validationErrors).length > 0) {
            setErrors(validationErrors);
            return;
        }

        setSubmitting(true);
        try {
            await login(form.email.trim(), form.password);
            toast.success('Đăng nhập thành công');

            const redirectTo = location.state?.from || '/';
            navigate(redirectTo, { replace: true });
        } catch (err) {
            // Error message already toasts via the apiClient interceptor;
            // map field-specific errors when backend returns them.
            if (err?.errors) {
                setErrors(err.errors);
            }
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="auth-page">
            <div className="auth-card">
                <h1 className="auth-title">Đăng nhập</h1>
                <p className="auth-subtitle">Chào mừng bạn quay lại WOODFURNI</p>

                <form onSubmit={handleSubmit} noValidate>
                    <Input
                        id="email"
                        name="email"
                        type="email"
                        label="Email"
                        placeholder="you@example.com"
                        value={form.email}
                        onChange={handleChange}
                        error={errors.email}
                        required
                        autoComplete="email"
                    />
                    <Input
                        id="password"
                        name="password"
                        type="password"
                        label="Mật khẩu"
                        placeholder="Nhập mật khẩu"
                        value={form.password}
                        onChange={handleChange}
                        error={errors.password}
                        required
                        autoComplete="current-password"
                    />

                    <Button
                        type="submit"
                        variant="primary"
                        size="lg"
                        fullWidth
                        loading={submitting}
                    >
                        {submitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
                    </Button>
                </form>

                <p className="auth-footer">
                    Chưa có tài khoản? <Link to="/register">Đăng ký ngay</Link>
                </p>
            </div>
        </div>
    );
}