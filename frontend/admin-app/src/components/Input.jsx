import PropTypes from 'prop-types';
import './Input.css';

export default function Input({
    id, label, type = 'text', value, onChange,
    placeholder, error, required = false, disabled = false,
    autoComplete, name, hint,
}) {
    return (
        <div className={`input-wrap ${error ? 'input-wrap--error' : ''}`}>
            {label && (
                <label htmlFor={id} className="input-label">
                    {label}{required && <span className="input-required">*</span>}
                </label>
            )}
            <input
                id={id} name={name} type={type}
                value={value} onChange={onChange}
                placeholder={placeholder} disabled={disabled}
                required={required} autoComplete={autoComplete}
                className="input-control"
            />
            {error && <p className="input-error">{error}</p>}
            {hint && !error && <p className="input-hint">{hint}</p>}
        </div>
    );
}

Input.propTypes = {
    id: PropTypes.string, label: PropTypes.string, type: PropTypes.string,
    value: PropTypes.string, onChange: PropTypes.func,
    placeholder: PropTypes.string, error: PropTypes.string,
    required: PropTypes.bool, disabled: PropTypes.bool,
    autoComplete: PropTypes.string, name: PropTypes.string,
    hint: PropTypes.string,
};