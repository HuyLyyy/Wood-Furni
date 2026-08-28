import { useEffect } from 'react';

export default function usePageTitle(title) {
    useEffect(() => {
        const appName = import.meta.env.VITE_APP_NAME || 'WOODFURNI Admin';
        document.title = title ? `${title} | ${appName}` : appName;
    }, [title]);
}