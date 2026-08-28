import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
    plugins: [react()],
    test: {
        environment: 'jsdom',
        globals: true,
        setupFiles: ['./src/test/setup.js'],
        pool: 'forks',
        include: ['src/**/*.test.{js,jsx}'],
        css: false,
        env: {
            VITE_API_BASE_URL: 'http://localhost:3000/api/v1',
        },
    },
});