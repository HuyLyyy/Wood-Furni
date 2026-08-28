# WOODFURNI Customer App

React + Vite SPA for the public storefront.

## Run

```bash
cd frontend/customer-app
cp .env.example .env
npm install
npm run dev
```

App opens at http://localhost:5173. Proxies `/api/*` to the gateway at `http://localhost:3000`.

## Folder structure

```
src/
├── assets/          # static images, SVG logo
├── components/      # pure presentational UI (Button, Input, Toast)
├── pages/           # top-level pages not tied to a feature
├── layouts/         # MainLayout (Header + Footer)
├── hooks/           # useAuth (re-export), usePageTitle, ...
├── contexts/        # AuthContext (provider + global state)
├── services/        # apiClient (axios), apiAuth.js, apiProducts.js, ...
├── utils/           # formatDate, validators, storage helpers
├── routes/          # AppRouter + ProtectedRoute
└── features/        # feature-based: auth/, catalog/, cart/, order/, review/
```

## Environment variables

| Var | Default | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:3000/api/v1` | Backend base URL |
| `VITE_APP_NAME` | `WOODFURNI` | App display name |