import './PageSpinner.css';

export default function PageSpinner() {
    return (
        <div className="page-spinner" role="status" aria-label="Loading">
            <span className="page-spinner__dot" />
            <span className="page-spinner__dot" />
            <span className="page-spinner__dot" />
        </div>
    );
}