# WOODFURNI Admin Console

React + Vite SPA for internal staff (ADMIN, SALES, WAREHOUSE, CONTENT).
**Separate codebase** from `customer-app` — only `apiClient.js`, `AuthContext`, and small utilities are intentionally copied (auth tokens live in separate localStorage namespaces so the two apps don't share state).

## Run

```bash
cd frontend/admin-app
cp .env.example .env
npm install
npm run dev
```

App opens at http://localhost:5174.

## Roles

| Role | Sees in sidebar |
|---|---|
| ADMIN | All menus |
| SALES | Dashboard, Orders, Customers, Reports |
| WAREHOUSE | Dashboard, Inventory, Reports |
| CONTENT | Dashboard, Products, Categories, Reviews, Reports |

> **Note:** sidebar filtering is **UX only**. Backend enforces real permissions via `@PreAuthorize` on each controller method.

Login attempts from `CUSTOMER` accounts are rejected at the FE before the API call is even made.

## Folder structure

```
src/
├── assets/          # styles, static assets
├── components/      # Button, Input, ProtectedRoute, PageSpinner, ConfirmDialog
├── pages/           # NotFoundPage, etc.
├── layouts/         # AdminLayout (sidebar + header)
├── hooks/           # useAuth, useRealtime, usePageTitle
├── contexts/        # AuthContext (separate from customer-app)
├── services/        # apiClient, apiAuth, apiReports
├── utils/           # format, validators, roleMeta
├── routes/          # AppRouter + ProtectedRoute (role-aware)
└── features/        # dashboard/, products/, orders/, ...
```