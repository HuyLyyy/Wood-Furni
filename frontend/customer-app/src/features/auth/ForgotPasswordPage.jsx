import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { Button, Input } from '../../components/index.js';
import OtpVerificationStep from './OtpVerificationStep.jsx';
import { authApi } from '../../services/apiAuth.js';
import { validate, validators } from '../../utils/validators.js';
import './AuthForm.css';

/**
 * ForgotPasswordPage — 3-step forgot-password flow.
 *
 * Step 1 (email):  POST /auth/otp/forgot-password/send
 *                   → user receives 6-digit code via email.
 * Step 2 (otp):    POST /auth/otp/forgot-password/verify
 *                   → server returns single-use otpToken.
 * Step 3 (new pw): POST /auth/password/reset  body: { otpToken, newPassword, confirmNewPassword }
 *                   → password replaced server-side. User is redirected to /login.
 *
 * Step 2 reuses the existing OtpVerificationStep component but plugs in a
 * different send/verify pair so the OTP purpose is correctly FORGOT_PASSWORD.
 */
export default function ForgotPasswordPage() {
    const navigate = useNavigate();
    const [step, setStep] = useState('email'); // 'email' | 'otp' | 'reset'

    const [email, setEmail] = useState('');
    const [otpToken, setOtpToken] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmNewPassword, setConfirmNewPassword] = useState('');

    const [errors, setErrors] = useState({});
    const [submitting, setSubmitting] = useState(false);

    // -------- step 1: email --------
    async function handleSendOtp(e) {
        e.preventDefault();

        const validationErrors = validate({ email }, { email: validators.email });
        if (Object.keys(validationErrors).length > 0) {
            setErrors(validationErrors);
            return;
        }
        setErrors({});
        setSubmitting(true);

        try {
            await authApi.sendForgotPasswordOtp(email.trim());
            toast.success('Đã gửi mã xác nhận đến email của bạn');
            setStep('otp');
        } catch (err) {
            if (err?.errors) setErrors(err.errors);
            if (err?.message) toast.error(err.message);
        } finally {
            setSubmitting(false);
        }
    }

    // -------- step 2: otp --------
    async function handleOtpVerified(token) {
        setOtpToken(token);
        setStep('reset');
    }

    // -------- step 3: new password --------
    async function handleResetPassword(e) {
        e.preventDefault();

        const validationErrors = validate(
            { newPassword, confirmNewPassword },
            {
                newPassword: validators.password,
                confirmNewPassword: validators.password,
            },
        );
        if (newPassword !== confirmNewPassword) {
            validationErrors.confirmNewPassword = 'Mật khẩu nhập lại không khớp';
        }
        if (Object.keys(validationErrors).length > 0) {
            setErrors(validationErrors);
            return;
        }
        setErrors({});
        setSubmitting(true);

        try {
            await authApi.resetPassword({
                otpToken,
                newPassword,
                confirmNewPassword,
            });
            toast.success('Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại.');
            navigate('/login', { replace: true });
        } catch (err) {
            if (err?.errors) setErrors(err.errors);
            if (err?.message) toast.error(err.message);
        } finally {
            setSubmitting(false);
        }
    }

    function handleField(field, value) {
        if (field === 'newPassword') setNewPassword(value);
        if (field === 'confirmNewPassword') setConfirmNewPassword(value);
        if (errors[field]) setErrors((prev) => ({ ...prev, [field]: null }));
    }

    // ──────────────── render per step ────────────────

    if (step === 'otp') {
        return (
            <div className="auth-page">
                <OtpVerificationStep
                    email={email.trim()}
                    onVerified={handleOtpVerified}
                    onBack={() => setStep('email')}
                    onResend={() => { /* OtpVerificationStep handles resend itself */ }}
                    sendOtp={authApi.sendForgotPasswordOtp}
                    verifyOtp={authApi.verifyForgotPasswordOtp}
                />
            </div>
        );
    }

    if (step === 'reset') {
        return (
            <div className="auth-page">
                <div className="auth-card">
                    <h1 className="auth-title">Đặt lại mật khẩu</h1>
                    <p className="auth-subtitle">
                        Nhập mật khẩu mới cho tài khoản{' '}
                        <strong>{email}</strong>.
                    </p>

                    <form onSubmit={handleResetPassword} noValidate>
                        <Input
                            id="newPassword"
                            name="newPassword"
                            type="password"
                            label="Mật khẩu mới"
                            placeholder="Ít nhất 8 ký tự"
                            value={newPassword}
                            onChange={(e) => handleField('newPassword', e.target.value)}
                            error={errors.newPassword}
                            required
                            autoComplete="new-password"
                            hint="Ít nhất 8 ký tự"
                        />
                        <Input
                            id="confirmNewPassword"
                            name="confirmNewPassword"
                            type="password"
                            label="Nhập lại mật khẩu mới"
                            placeholder="Nhập lại mật khẩu mới"
                            value={confirmNewPassword}
                            onChange={(e) => handleField('confirmNewPassword', e.target.value)}
                            error={errors.confirmNewPassword}
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
                            {submitting ? 'Đang đặt lại...' : 'Đặt lại mật khẩu'}
                        </Button>
                    </form>

                    <p className="auth-footer">
                        <button
                            type="button"
                            className="otp-back"
                            onClick={() => setStep('otp')}
                            style={{ background: 'none', border: 'none', padding: 0 }}
                        >
                            ← Quay lại nhập mã
                        </button>
                    </p>
                </div>
            </div>
        );
    }

    // step === 'email'
    return (
        <div className="auth-page">
            <div className="auth-card">
                <h1 className="auth-title">Quên mật khẩu</h1>
                <p className="auth-subtitle">
                    Nhập email đã đăng ký — chúng tôi sẽ gửi mã xác nhận để đặt lại mật khẩu.
                </p>

                <form onSubmit={handleSendOtp} noValidate>
                    <Input
                        id="email"
                        name="email"
                        type="email"
                        label="Email"
                        placeholder="you@example.com"
                        value={email}
                        onChange={(e) => {
                            setEmail(e.target.value);
                            if (errors.email) setErrors((prev) => ({ ...prev, email: null }));
                        }}
                        error={errors.email}
                        required
                        autoComplete="email"
                        hint="Mã xác nhận sẽ được gửi đến email này"
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
                    <Link to="/login">← Quay lại đăng nhập</Link>
                </p>
            </div>
        </div>
    );
}
