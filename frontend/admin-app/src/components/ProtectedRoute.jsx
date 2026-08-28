import PropTypes from 'prop-types';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext.jsx';
import PageSpinner from './PageSpinner.jsx';

/**
 * Route guard.
 *
 *   <ProtectedRoute>                  → require any authenticated staff user
 *   <ProtectedRoute roles={['ADMIN']}>→ also require role membership
 *
 * Two failure modes:
 *   - not authenticated        → redirect /login
 *   - authenticated but wrong role → redirect /
 *     (the dashboard will render, with whatever sidebar items they have)
 */
export default function ProtectedRoute({ children, roles }) {
    const { isAuthenticated, loading, user } = useAuth();

    if (loading) return <PageSpinner />;
    if (!isAuthenticated) {
        return <Navigate to="/login" replace />;
    }
    if (roles && roles.length > 0 && !roles.includes(user?.role)) {
        return <Navigate to="/" replace />;
    }
    return children;
}

ProtectedRoute.propTypes = {
    children: PropTypes.node,
    roles: PropTypes.arrayOf(PropTypes.string),
};