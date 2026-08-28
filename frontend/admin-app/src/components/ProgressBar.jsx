import PropTypes from 'prop-types';
import './ProgressBar.css';

/**
 * ProgressBar — generic horizontal progress bar with optional label.
 *
 * <ProgressBar value={42} max={100} label="42/100" tone="ok" />
 *
 * - If `max` is null/0 → renders "Không giới hạn" instead of a bar.
 * - `value` is clamped to [0, max] for display only.
 */
export default function ProgressBar({ value, max, label, tone = 'primary' }) {
    if (max == null || max <= 0) {
        return (
            <div className="progress-bar progress-bar--unlimited">
                <span className="progress-bar__unlimited-text">Không giới hạn</span>
            </div>
        );
    }

    const pct = Math.min(100, Math.max(0, (value / max) * 100));
    const full = value >= max;

    return (
        <div className={`progress-bar progress-bar--${tone} ${full ? 'is-full' : ''}`}>
            <div className="progress-bar__track">
                <div
                    className="progress-bar__fill"
                    style={{ width: `${pct}%` }}
                    role="progressbar"
                    aria-valuenow={value}
                    aria-valuemin={0}
                    aria-valuemax={max}
                />
            </div>
            {label && <div className="progress-bar__label">{label}</div>}
        </div>
    );
}

ProgressBar.propTypes = {
    value: PropTypes.number.isRequired,
    max: PropTypes.number,
    label: PropTypes.string,
    tone: PropTypes.oneOf(['primary', 'ok', 'warn', 'danger']),
};
