import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
    plugins: [react()],
    resolve: {
        alias: {
            '@': path.resolve(__dirname, 'src'),
            '@assets': path.resolve(__dirname, 'src/assets'),
            '@components': path.resolve(__dirname, 'src/components'),
            '@pages': path.resolve(__dirname, 'src/pages'),
            '@layouts': path.resolve(__dirname, 'src/layouts'),
            '@hooks': path.resolve(__dirname, 'src/hooks'),
            '@contexts': path.resolve(__dirname, 'src/contexts'),
            '@services': path.resolve(__dirname, 'src/services'),
            '@utils': path.resolve(__dirname, 'src/utils'),
            '@routes': path.resolve(__dirname, 'src/routes'),
            '@features': path.resolve(__dirname, 'src/features'),
        },
    },
    server: {
        port: 5173,
        proxy: {
            '/api': {
                target: 'http://localhost:3001',
                changeOrigin: true,
            },
            '/uploads': {
                target: 'http://localhost:3001',
                changeOrigin: true,
            },
        },
    },
});
