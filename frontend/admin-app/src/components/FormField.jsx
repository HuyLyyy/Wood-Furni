import PropTypes from 'prop-types';
import './FormField.css';

/**
 * FormField — thin wrapper around <Input> / <Select> / <textarea> that
 * provides consistent label + error rendering.
 *
 *   <FormField label="..." error="..." required>
 *       <input ... />
 *   </FormField>
 */
export default function FormField({ label, error, required, hint, htmlFor, children, className = '' }) {
    return (
        <div className={`form-field ${error ? 'form-field--error' : ''} ${className}`}>
            {label && (
                <label className="form-field__label" htmlFor={htmlFor}>
                    {label}{required && <span className="form-field__required">*</span>}
                </label>
            )}
            {children}
            {hint && !error && <p className="form-field__hint">{hint}</p>}
            {error && <p className="form-field__error">{error}</p>}
        </div>
    );
}

FormField.propTypes = {
    label: PropTypes.string,
    error: PropTypes.string,
    required: PropTypes.bool,
    hint: PropTypes.string,
    htmlFor: PropTypes.string,
    children: PropTypes.node,
    className: PropTypes.string,
};