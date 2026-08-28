import { Link } from 'react-router-dom';

export default function NotFoundPage() {
    return (
        <div style={{ padding: '64px 24px', textAlign: 'center' }}>
            <h1 style={{ fontSize: 48, margin: 0, color: '#6b4f2a' }}>404</h1>
            <p style={{ marginTop: 8, color: '#6b7280' }}>Trang bạn tìm không tồn tại.</p>
            <Link to="/" style={{ display: 'inline-block', marginTop: 16, color: '#6b4f2a', fontWeight: 600 }}>
                ← Về Dashboard
            </Link>
        </div>
    );
}