import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext.jsx';
import PropTypes from 'prop-types';
import PageSpinner from './PageSpinner.jsx';

/**
 * Route guard.
 *  - while AuthContext is bootstrapping, render a small spinner
 *  - if not authenticated, redirect to /login and remember where the user
 *    wanted to go so we can bounce them back after login
 *  - if `roles` is provided, also enforce role membership
 *
 * Usage:
 *   <Route path="/cart" element={<ProtectedRoute><CartPage/></ProtectedRoute>} />
 *   <Route path="/admin" element={<ProtectedRoute roles={['ADMIN']}><AdminPage/></ProtectedRoute>} />
 */
export default function ProtectedRoute({ children, roles }) {
    const { isAuthenticated, loading, user } = useAuth();
    const location = useLocation();

    if (loading) {
        return <PageSpinner />;
    }

    if (!isAuthenticated) {
        return (
            <Navigate
                to="/login"
                replace
                state={{ from: location.pathname + location.search }}
            />
        );
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