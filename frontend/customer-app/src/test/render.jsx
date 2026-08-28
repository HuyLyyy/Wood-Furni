/**
 * Convenience renderer for context-aware tests.
 *
 * Usage:
 *   renderWithProviders(<ComponentUnderTest />);
 *   renderWithProviders(<ComponentUnderTest />, { wrapper: CustomWrp });
 *
 * Wraps the UI with AuthProvider + CartProvider so tests can rely on the
 * real context shape. Use the `preloaded` option to seed localStorage
 * (e.g. simulate an already-authenticated user) before mount.
 */
import { render } from '@testing-library/react';
import { AuthProvider } from '../contexts/AuthContext.jsx';
import { CartProvider } from '../contexts/CartContext.jsx';
import { tokenStorage } from '../services/apiClient.js';

function seedUser(preloaded) {
    if (preloaded?.accessToken) tokenStorage.setAccess(preloaded.accessToken);
    if (preloaded?.refreshToken) tokenStorage.setRefresh(preloaded.refreshToken);
    if (preloaded?.user) tokenStorage.setUser(preloaded.user);
}

export function renderWithProviders(ui, { preloaded = null, wrapper } = {}) {
    seedUser(preloaded);
    const AllProviders = ({ children }) => (
        <AuthProvider>
            <CartProvider>{children}</CartProvider>
        </AuthProvider>
    );
    const Wrap = wrapper
        ? ({ children }) => <AllProviders>{wrapper({ children })}</AllProviders>
        : AllProviders;
    return render(ui, { wrapper: Wrap });
}

export * from '@testing-library/react';
