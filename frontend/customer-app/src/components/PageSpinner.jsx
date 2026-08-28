import './PageSpinner.css';

/**
 * Centered spinner for route bootstrap / loading states.
 * No external lib — pure CSS animation.
 */
export default function PageSpinner() {
    return (
        <div className="page-spinner" role="status" aria-label="Loading">
            <span className="page-spinner__dot" />
            <span className="page-spinner__dot" />
            <span className="page-spinner__dot" />
        </div>
    );
}