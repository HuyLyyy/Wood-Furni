import { render } from '@testing-library/react';
import { AuthProvider } from '../contexts/AuthContext.jsx';
import { tokenStorage } from '../services/apiClient.js';

function seedUser(preloaded) {
    if (preloaded?.accessToken) tokenStorage.setAccess(preloaded.accessToken);
    if (preloaded?.refreshToken) tokenStorage.setRefresh(preloaded.refreshToken);
    if (preloaded?.user) tokenStorage.setUser(preloaded.user);
}

export function renderWithProviders(ui, { preloaded = null, wrapper } = {}) {
    seedUser(preloaded);
    const AllProviders = ({ children }) => (
        <AuthProvider>{children}</AuthProvider>
    );
    const Wrap = wrapper
        ? ({ children }) => <AllProviders>{wrapper({ children })}</AllProviders>
        : AllProviders;
    return render(ui, { wrapper: Wrap });
}

export * from '@testing-library/react';
