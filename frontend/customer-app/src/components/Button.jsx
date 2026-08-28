import PropTypes from 'prop-types';
import './Button.css';

/**
 * Primary button — wood-brown background, no external UI library.
 * Variants: 'primary' | 'ghost' | 'outline'
 * Sizes:    'sm' | 'md' | 'lg'
 */
export default function Button({
    children,
    variant = 'primary',
    size = 'md',
    type = 'button',
    fullWidth = false,
    disabled = false,
    loading = false,
    onClick,
    ...rest
}) {
    const classes = [
        'btn',
        `btn--${variant}`,
        `btn--${size}`,
        fullWidth ? 'btn--full' : '',
        loading ? 'btn--loading' : '',
    ].filter(Boolean).join(' ');

    return (
        <button
            type={type}
            className={classes}
            disabled={disabled || loading}
            onClick={onClick}
            {...rest}
        >
            {loading ? <span className="btn__spinner" aria-hidden="true" /> : null}
            <span>{children}</span>
        </button>
    );
}

Button.propTypes = {
    children: PropTypes.node,
    variant: PropTypes.oneOf(['primary', 'ghost', 'outline']),
    size: PropTypes.oneOf(['sm', 'md', 'lg']),
    type: PropTypes.oneOf(['button', 'submit', 'reset']),
    fullWidth: PropTypes.bool,
    disabled: PropTypes.bool,
    loading: PropTypes.bool,
    onClick: PropTypes.func,
};