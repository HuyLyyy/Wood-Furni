import { useEffect, useRef, useState } from 'react';
import { Button } from '../../components/index.js';
import { authApi } from '../../services/apiAuth.js';
import './AuthForm.css';

const CODE_LENGTH = 6;

/**
 * Step 2 of the registration flow: 6-digit OTP entry.
 *
 * - Auto-focuses the first input and the next empty input on change.
 * - Supports paste of the full code.
 * - Auto-submits once all 6 digits are present.
 * - Resend button is disabled while {@code cooldown} > 0.
 */
export default function OtpVerificationStep({ email, onVerified, onBack, onResent, devOtpCode }) {
    const [digits, setDigits] = useState(() => Array(CODE_LENGTH).fill(''));
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');
    const [info, setInfo] = useState('');
    const [cooldown, setCooldown] = useState(60);
    const inputsRef = useRef([]);

    useEffect(() => {
        inputsRef.current[0]?.focus();
    }, []);

    // When the backend returns a dev OTP code (no SMTP configured), auto-fill
    // it so the user can complete registration on Render deployments that
    // don't have MAIL_USERNAME/MAIL_PASSWORD set.
    useEffect(() => {
        if (devOtpCode && /^\d+$/.test(String(devOtpCode))) {
            const code = String(devOtpCode).slice(0, CODE_LENGTH).split('');
            const next = Array(CODE_LENGTH).fill('');
            for (let i = 0; i < code.length; i += 1) next[i] = code[i];
            setDigits(next);
            setInfo('Mã xác nhận (chế độ dev): vui lòng dùng mã dưới đây');
        }
    }, [devOtpCode]);

    // Cooldown countdown
    useEffect(() => {
        if (cooldown <= 0) return undefined;
        const t = setInterval(() => {
            setCooldown((c) => (c > 0 ? c - 1 : 0));
        }, 1000);
        return () => clearInterval(t);
    }, [cooldown]);

    const code = digits.join('');
    const isComplete = code.length === CODE_LENGTH && digits.every((d) => d !== '');

    useEffect(() => {
        if (isComplete && !submitting) {
            handleVerify(code);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isComplete]);

    function handleChange(index, value) {
        // Only digits
        const cleaned = value.replace(/\D/g, '');
        if (!cleaned) {
            setDigits((prev) => {
                const next = [...prev];
                next[index] = '';
                return next;
            });
            return;
        }

        // If user types/pastes multiple chars into a single box, distribute.
        if (cleaned.length > 1) {
            setDigits((prev) => {
                const next = [...prev];
                let i = index;
                for (const ch of cleaned) {
                    if (i >= CODE_LENGTH) break;
                    next[i] = ch;
                    i += 1;
                }
                return next;
            });
            const lastFilled = Math.min(index + cleaned.length, CODE_LENGTH) - 1;
            inputsRef.current[Math.min(lastFilled + 1, CODE_LENGTH - 1)]?.focus();
            return;
        }

        setDigits((prev) => {
            const next = [...prev];
            next[index] = cleaned[0];
            return next;
        });
        if (index < CODE_LENGTH - 1) {
            inputsRef.current[index + 1]?.focus();
        }
    }

    function handleKeyDown(index, e) {
        if (e.key === 'Backspace' && !digits[index] && index > 0) {
            inputsRef.current[index - 1]?.focus();
        }
        if (e.key === 'ArrowLeft' && index > 0) {
            inputsRef.current[index - 1]?.focus();
        }
        if (e.key === 'ArrowRight' && index < CODE_LENGTH - 1) {
            inputsRef.current[index + 1]?.focus();
        }
    }

    function handlePaste(e) {
        e.preventDefault();
        const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, CODE_LENGTH);
        if (!pasted) return;
        setDigits((prev) => {
            const next = [...prev];
            for (let i = 0; i < CODE_LENGTH; i += 1) {
                next[i] = pasted[i] || '';
            }
            return next;
        });
        const focusIdx = Math.min(pasted.length, CODE_LENGTH - 1);
        inputsRef.current[focusIdx]?.focus();
    }

    async function handleVerify(codeToVerify) {
        setError('');
        setInfo('');
        setSubmitting(true);
        try {
            const data = await authApi.verifyRegistrationOtp(email, codeToVerify);
            if (!data?.otpToken) {
                setError('Mã xác nhận không hợp lệ, vui lòng thử lại');
                return;
            }
            onVerified(data.otpToken);
        } catch (err) {
            setError(err?.message || 'Mã xác nhận không đúng');
            // Clear inputs so the user can try again.
            setDigits(Array(CODE_LENGTH).fill(''));
            inputsRef.current[0]?.focus();
        } finally {
            setSubmitting(false);
        }
    }

    async function handleResend() {
        if (cooldown > 0) return;
        setError('');
        setInfo('');
        try {
            const data = await authApi.sendRegistrationOtp(email);
            const wait = data?.cooldownSeconds ?? 60;
            setCooldown(wait || 60);
            setInfo('Đã gửi lại mã xác nhận. Vui lòng kiểm tra email.');
            onResent?.(data);
        } catch (err) {
            setError(err?.message || 'Không thể gửi lại mã. Vui lòng thử lại sau.');
        }
    }

    return (
        <div className="auth-card otp-card">
            <h1 className="auth-title">Xác nhận email</h1>
            <p className="auth-subtitle">
                Chúng tôi đã gửi mã xác nhận 6 số đến <strong>{email}</strong>.<br />
                Vui lòng nhập mã để hoàn tất đăng ký.
            </p>

            {devOtpCode && (
                <div className="otp-dev-banner" role="status">
                    <strong>Chế độ phát triển (SMTP chưa cấu hình trên server).</strong>
                    <span> Mã xác nhận của bạn là </span>
                    <code className="otp-dev-code">{devOtpCode}</code>
                </div>
            )}

            <div className="otp-inputs" onPaste={handlePaste}>
                {digits.map((digit, i) => (
                    <input
                        key={i}
                        ref={(el) => {
                            inputsRef.current[i] = el;
                        }}
                        type="text"
                        inputMode="numeric"
                        pattern="[0-9]*"
                        maxLength={1}
                        value={digit}
                        onChange={(e) => handleChange(i, e.target.value)}
                        onKeyDown={(e) => handleKeyDown(i, e)}
                        className={`otp-input ${error ? 'otp-input--error' : ''}`}
                        aria-label={`Số thứ ${i + 1}`}
                        disabled={submitting}
                        autoComplete="one-time-code"
                    />
                ))}
            </div>

            {error && <p className="input-error otp-error">{error}</p>}
            {info && !error && <p className="otp-info">{info}</p>}

            <div className="otp-actions">
                <Button
                    type="button"
                    variant="primary"
                    size="lg"
                    fullWidth
                    loading={submitting}
                    disabled={!isComplete || submitting}
                    onClick={() => handleVerify(code)}
                >
                    {submitting ? 'Đang xác nhận...' : 'Xác nhận'}
                </Button>

                <div className="otp-resend">
                    {cooldown > 0 ? (
                        <span className="otp-resend__countdown">
                            Gửi lại mã sau {cooldown}s
                        </span>
                    ) : (
                        <button
                            type="button"
                            className="otp-resend__btn"
                            onClick={handleResend}
                        >
                            Gửi lại mã
                        </button>
                    )}
                    <button
                        type="button"
                        className="otp-back"
                        onClick={onBack}
                    >
                        Đổi email
                    </button>
                </div>
            </div>
        </div>
    );
}
