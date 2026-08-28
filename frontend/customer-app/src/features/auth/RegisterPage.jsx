import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { Button, Input } from '../../components/index.js';
import { useAuth } from '../../contexts/AuthContext.jsx';
import { validate, validators } from '../../utils/validators.js';
import './AuthForm.css';

/**
 * RegisterPage — POST /auth/register
 * Body: { email, password, fullName, phone } (matches backend dto/RegisterRequest)
 * Backend creates the user with role=CUSTOMER (default) and returns AuthResponse.
 */
export default function RegisterPage() {
    const { register } = useAuth();
    const navigate = useNavigate();

    const [form, setForm] = useState({
        email: '',
        password: '',
        confirmPassword: '',
        fullName: '',
        phone: '',
    });
    const [errors, setErrors] = useState({});
    const [submitting, setSubmitting] = useState(false);

    const handleChange = (e) => {
        const { name, value } = e.target;
        setForm((prev) => ({ ...prev, [name]: value }));
        if (errors[name]) setErrors((prev) => ({ ...prev, [name]: null }));
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        const validationErrors = validate(form, {
            email: validators.email,
            password: validators.password,
            fullName: validators.fullName,
            phone: validators.phone,
        });
        if (form.password !== form.confirmPassword) {
            validationErrors.confirmPassword = 'Mật khẩu nhập lại không khớp';
        }
        if (Object.keys(validationErrors).length > 0) {
            setErrors(validationErrors);
            return;
        }

        setSubmitting(true);
        try {
            await register({
                email: form.email.trim(),
                password: form.password,
                fullName: form.fullName.trim(),
                phone: form.phone.trim() || undefined,
            });
            toast.success('Đăng ký thành công!');
            navigate('/', { replace: true });
        } catch (err) {
            if (err?.errors) setErrors(err.errors);
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="auth-page">
            <div className="auth-card">
                <h1 className="auth-title">Tạo tài khoản</h1>
                <p className="auth-subtitle">
                    Trở thành thành viên WOODFURNI để nhận ưu đãi
                </p>

                <form onSubmit={handleSubmit} noValidate>
                    <Input
                        id="fullName"
                        name="fullName"
                        label="Họ và tên"
                        placeholder="Nguyễn Văn A"
                        value={form.fullName}
                        onChange={handleChange}
                        error={errors.fullName}
                        required
                        autoComplete="name"
                    />
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
                        id="phone"
                        name="phone"
                        type="tel"
                        label="Số điện thoại"
                        placeholder="(tuỳ chọn)"
                        value={form.phone}
                        onChange={handleChange}
                        error={errors.phone}
                        autoComplete="tel"
                    />
                    <Input
                        id="password"
                        name="password"
                        type="password"
                        label="Mật khẩu"
                        placeholder="Ít nhất 8 ký tự"
                        value={form.password}
                        onChange={handleChange}
                        error={errors.password}
                        required
                        autoComplete="new-password"
                        hint="Ít nhất 8 ký tự"
                    />
                    <Input
                        id="confirmPassword"
                        name="confirmPassword"
                        type="password"
                        label="Nhập lại mật khẩu"
                        placeholder="Nhập lại mật khẩu"
                        value={form.confirmPassword}
                        onChange={handleChange}
                        error={errors.confirmPassword}
                        required
                        autoComplete="new-password"
                    />

                    <Button
                        type="submit"
                        variant="primary"
                        size="lg"
                        fullWidth
                        loading={submitting}
                    >
                        {submitting ? 'Đang tạo tài khoản...' : 'Đăng ký'}
                    </Button>
                </form>

                <p className="auth-footer">
                    Đã có tài khoản? <Link to="/login">Đăng nhập</Link>
                </p>
            </div>
        </div>
    );
}