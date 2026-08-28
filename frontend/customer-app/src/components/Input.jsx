import PropTypes from 'prop-types';
import { useState } from 'react';
import './Input.css';

/**
 * Form input with label, error message, and password reveal toggle.
 * Pure presentational — no validation logic. Pass an `error` string and
 * it will be shown below the input.
 */
export default function Input({
    id,
    label,
    type = 'text',
    value,
    onChange,
    placeholder,
    error,
    required = false,
    disabled = false,
    autoComplete,
    name,
    hint,
}) {
    const [showPassword, setShowPassword] = useState(false);
    const isPassword = type === 'password';
    const inputType = isPassword && showPassword ? 'text' : type;

    return (
        <div className={`input-wrap ${error ? 'input-wrap--error' : ''}`}>
            {label && (
                <label htmlFor={id} className="input-label">
                    {label}{required && <span className="input-required">*</span>}
                </label>
            )}
            <div className="input-shell">
                <input
                    id={id}
                    name={name}
                    type={inputType}
                    value={value}
                    onChange={onChange}
                    placeholder={placeholder}
                    disabled={disabled}
                    required={required}
                    autoComplete={autoComplete}
                    className="input-control"
                />
                {isPassword && (
                    <button
                        type="button"
                        className="input-toggle"
                        onClick={() => setShowPassword((s) => !s)}
                        aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                    >
                        {showPassword ? 'Ẩn' : 'Hiện'}
                    </button>
                )}
            </div>
            {error && <p className="input-error">{error}</p>}
            {hint && !error && <p className="input-hint">{hint}</p>}
        </div>
    );
}

Input.propTypes = {
    id: PropTypes.string,
    label: PropTypes.string,
    type: PropTypes.string,
    value: PropTypes.string,
    onChange: PropTypes.func,
    placeholder: PropTypes.string,
    error: PropTypes.string,
    required: PropTypes.bool,
    disabled: PropTypes.bool,
    autoComplete: PropTypes.string,
    name: PropTypes.string,
    hint: PropTypes.string,
};