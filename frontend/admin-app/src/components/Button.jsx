import PropTypes from 'prop-types';
import './Button.css';

export default function Button({
    children, variant = 'primary', size = 'md',
    type = 'button', fullWidth = false,
    disabled = false, loading = false,
    onClick, ...rest
}) {
    const cls = [
        'btn',
        `btn--${variant}`,
        `btn--${size}`,
        fullWidth ? 'btn--full' : '',
        loading ? 'btn--loading' : '',
    ].filter(Boolean).join(' ');

    return (
        <button type={type} className={cls} disabled={disabled || loading} onClick={onClick} {...rest}>
            {loading ? <span className="btn__spinner" aria-hidden="true" /> : null}
            <span>{children}</span>
        </button>
    );
}

Button.propTypes = {
    children: PropTypes.node,
    variant: PropTypes.oneOf(['primary', 'ghost', 'outline', 'danger', 'warning']),
    size: PropTypes.oneOf(['sm', 'md', 'lg']),
    type: PropTypes.oneOf(['button', 'submit', 'reset']),
    fullWidth: PropTypes.bool,
    disabled: PropTypes.bool,
    loading: PropTypes.bool,
    onClick: PropTypes.func,
};