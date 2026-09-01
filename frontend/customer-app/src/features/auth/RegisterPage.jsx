import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { Button, Input } from '../../components/index.js';
import { useAuth } from '../../contexts/AuthContext.jsx';
import { validate, validators } from '../../utils/validators.js';
import { authApi } from '../../services/apiAuth.js';
import OtpVerificationStep from './OtpVerificationStep.jsx';
import './AuthForm.css';

/**
 * RegisterPage — two-step registration flow.
 *
 * Step 1 (form): User fills in name/email/phone/password, clicks
 *   "Gửi mã xác nhận" -> POST /auth/otp/send -> backend emails a 6-digit code.
 *
 * Step 2 (OTP):  User enters the code. Once /auth/otp/verify succeeds
 *   we get back a single-use otpToken, then call POST /auth/register with
 *   { ..., otpToken } -> backend creates the CUSTOMER and returns JWT.
 *
 * On success, AuthContext stores tokens + user and we navigate to "/".
 */
export default function RegisterPage() {
    const { register } = useAuth();
    const navigate = useNavigate();

    const [step, setStep] = useState('form'); // 'form' | 'otp'

    const [form, setForm] = useState({
        email: '',
        password: '',
        confirmPassword: '',
        fullName: '',
        phone: '',
    });
    const [errors, setErrors] = useState({});
    const [submitting, setSubmitting] = useState(false);

    function handleChange(e) {
        const { name, value } = e.target;
        setForm((prev) => ({ ...prev, [name]: value }));
        if (errors[name]) setErrors((prev) => ({ ...prev, [name]: null }));
    }

    async function handleSendOtp(e) {
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
            await authApi.sendRegistrationOtp(form.email.trim());
            toast.success('Đã gửi mã xác nhận đến email của bạn');
            setStep('otp');
        } catch (err) {
            if (err?.errors) setErrors(err.errors);
            if (err?.message) toast.error(err.message);
        } finally {
            setSubmitting(false);
        }
    }

    async function handleOtpVerified(otpToken) {
        setSubmitting(true);
        try {
            await register({
                email: form.email.trim(),
                password: form.password,
                fullName: form.fullName.trim(),
                phone: form.phone.trim() || undefined,
                otpToken,
            });
            toast.success('Đăng ký thành công!');
            navigate('/', { replace: true });
        } catch (err) {
            if (err?.errors) setErrors(err.errors);
            if (err?.message) toast.error(err.message);
            // Send the user back to step 2 to retry the code.
            setStep('otp');
        } finally {
            setSubmitting(false);
        }
    }

    function handleBackToForm() {
        setStep('form');
    }

    if (step === 'otp') {
        return (
            <div className="auth-page">
                <OtpVerificationStep
                    email={form.email.trim()}
                    onVerified={handleOtpVerified}
                    onBack={handleBackToForm}
                />
            </div>
        );
    }

    return (
        <div className="auth-page">
            <div className="auth-card">
                <h1 className="auth-title">Tạo tài khoản</h1>
                <p className="auth-subtitle">
                    Trở thành thành viên WOODFURNI để nhận ưu đãi
                </p>

                <form onSubmit={handleSendOtp} noValidate>
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
                        hint="Mã xác nhận sẽ được gửi đến email này"
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
                        {submitting ? 'Đang gửi mã...' : 'Gửi mã xác nhận'}
                    </Button>
                </form>

                <p className="auth-footer">
                    Đã có tài khoản? <Link to="/login">Đăng nhập</Link>
                </p>
            </div>
        </div>
    );
}
