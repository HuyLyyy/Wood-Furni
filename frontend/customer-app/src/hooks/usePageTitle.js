import { useEffect } from 'react';

/**
 * Sets document.title on mount. Pairs with a simple, readable title suffix.
 */
export default function usePageTitle(title) {
    useEffect(() => {
        const appName = import.meta.env.VITE_APP_NAME || 'WOODFURNI';
        document.title = title ? `${title} | ${appName}` : appName;
    }, [title]);
}