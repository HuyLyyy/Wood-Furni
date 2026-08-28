import AppRouter from './routes/AppRouter.jsx';

/**
 * Root component. Composition root: only <AppRouter />, since global
 * providers (AuthProvider, Toaster) are mounted in main.jsx.
 */
export default function App() {
    return <AppRouter />;
}