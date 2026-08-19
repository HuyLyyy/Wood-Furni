# WOODFURNI - E-Commerce Platform for Wooden Furniture

> **WOODFURNI** là hệ thống thương mại điện tử kinh doanh đồ gỗ nội thất trong nhà & ngoài trời, phát triển bởi *Mộc Việt Furniture*.

---

## Architecture Overview

```
┌───────────────┐
│    ReactJS    │  Customer Website + Admin Portal
│   Frontend    │
└───────┬───────┘
        │  HTTPS / JSON
        ▼
┌────────────▼────────┐
│  Node.js Gateway    │  Express + Socket.IO
│  (Auth / WS)       │
└───────┬────────────┘
        │
        ▼
┌─────────────┼────────────────┐
│             │                │
▼             ▼                ▼
Authentication    Realtime       REST API
/ Rate Limit     Notification    (Spring Boot)
│
┌────▼─────┐
│ Spring   │  Modular Monolith
│ Boot     │
└────┬─────┘
     │
     └─────────────────────────────┐
             ▼                     ▼
        MongoDB              10 Business Modules
                               (M01-M10)

Modules: Auth · Catalog · Inventory · Cart · Order · Payment · Promotion · Review · Reporting
```

---

## Tech Stack

| Technology | Role |
|---|---|
| **ReactJS (Vite)** | Customer Website + Admin Dashboard |
| **Node.js + Express** | API Gateway, WebSocket, Notification |
| **Spring Boot 3.x** | Business Logic (Modular Monolith) |
| **Spring Security + JWT** | Authentication / Authorization (RBAC) |
| **Spring Data MongoDB** | Data Access Layer |
| **MongoDB** | Primary Database |
| **Socket.IO** | Realtime Notifications |
| **Docker Compose** | Containerization |
| **Swagger/OpenAPI** | API Documentation |
| **JUnit + Mockito** | Backend Testing |
| **React Testing Library** | Frontend Testing |

---

## Project Structure (Monorepo)

```
woodfurni/
├── frontend/
│   ├── customer-app/          # React — Customer Website
│   │   ├── src/
│   │   │   ├── assets/
│   │   │   ├── components/     # Pure UI components
│   │   │   ├── pages/          # Home, ProductList, ProductDetail, Cart, Checkout...
│   │   │   ├── layouts/
│   │   │   ├── hooks/          # useCart, useAuth, useProducts...
│   │   │   ├── contexts/      # AuthContext, CartContext
│   │   │   ├── services/       # Axios instance + API modules
│   │   │   ├── utils/
│   │   │   ├── routes/
│   │   │   └── features/       # auth/, catalog/, cart/, order/, review/
│   │   └── package.json
│   │
│   └── admin-app/              # React — Admin Portal
│       └── src/
│
├── gateway/
│   └── node-gateway/
│       ├── src/
│       │   ├── config/
│       │   ├── middleware/     # Auth verify, rate-limit
│       │   ├── routes/         # Reverse proxy to Spring Boot
│       │   ├── socket/         # Namespace order, inventory
│       │   ├── notification/   # Publisher/subscriber
│       │   └── app.js
│       └── package.json
│
├── backend/
│   └── furniture-api/
│       ├── src/main/java/com/woodfurni/
│       │   ├── config/         # MongoConfig, SecurityConfig, SwaggerConfig
│       │   ├── common/         # ApiResponse, PageResponse, GlobalExceptionHandler
│       │   ├── auth/           # Authentication module
│       │   ├── catalog/        # Product, Category, Material
│       │   ├── inventory/
│       │   ├── cart/
│       │   ├── order/
│       │   ├── payment/
│       │   ├── promotion/
│       │   ├── review/
│       │   ├── reporting/
│       │   ├── notification/
│       │   ├── security/       # JwtFilter, JwtProvider, UserDetailsServiceImpl
│       │   └── FurnitureApiApplication.java
│       ├── src/main/resources/
│       ├── src/test/java/com/woodfurni/
│       └── pom.xml
│
├── database/
│   ├── sample-data/            # Seed data JSON
│   ├── indexes/                # MongoDB index scripts
│   └── backup/
│
├── docker/
│   └── docker-compose.yml
│
├── docs/
│   ├── ai-specs/               # AI Development Specifications
│   ├── requirements/
│   ├── uml/
│   ├── api/
│   ├── testing/
│   └── thesis/
│
├── postman/
│   └── WOODFURNI.postman_collection.json
│
├── README.md
├── .gitignore
└── .cursorrules
```

---

## Role-Based Access Control (RBAC)

| Role | Permissions |
|---|---|
| `CUSTOMER` | Browse products, manage cart, place orders, review |
| `SALES` | View and process orders |
| `WAREHOUSE` | Manage inventory |
| `CONTENT` | Manage products, categories, materials |
| `ADMIN` | Full access + Dashboard |

---

## 10 Business Modules

| Module | Description |
|---|---|
| **M01 - Authentication** | Register, login, JWT, refresh token |
| **M02 - Product Catalog** | CRUD products with full wood industry attributes |
| **M03 - Category/Material** | Hierarchical categories, wood types |
| **M04 - Search & Filter** | Multi-criteria search (environment, room, woodType, price) |
| **M05 - Cart** | Persistent shopping cart |
| **M06 - Order** | Checkout flow with state machine |
| **M07 - Inventory** | Real-time stock management |
| **M08 - Promotion** | Vouchers (percentage, fixed amount) |
| **M09 - Review** | Product reviews with rating aggregation |
| **M10 - Reporting** | Admin dashboard with MongoDB aggregation |

---

## 12 MongoDB Collections

`users` · `roles` · `products` · `categories` · `materials` · `inventories` · `carts` · `orders` · `payments` · `promotions` · `reviews` · `notifications`

---

## Getting Started

### Prerequisites

- **Java 17+** (JDK)
- **Node.js 18+** (LTS)
- **Maven 3.8+**
- **MongoDB 6+**
- **Docker & Docker Compose** (optional)

### Quick Start (Development)

> **TODO**: Update with actual commands after Phase 14

```bash
# 1. Clone the repository
git clone <repo-url>
cd woodfurni

# 2. Start MongoDB
# Option A: Docker
docker run -d -p 27017:27017 --name woodfurni-mongo mongo:7

# Option B: Local MongoDB
# Ensure MongoDB is running on localhost:27017

# 3. Backend (Spring Boot)
cd backend/furniture-api
# Copy and configure environment
cp src/main/resources/application.yml.example src/main/resources/application.yml
# Edit application.yml with your MongoDB URI
mvn spring-boot:run
# Backend runs at http://localhost:8080/api/v1

# 4. Gateway (Node.js)
cd gateway/node-gateway
npm install
# Copy and configure environment
cp .env.example .env
npm run dev
# Gateway runs at http://localhost:3000

# 5. Customer Frontend
cd frontend/customer-app
npm install
npm run dev
# Customer app runs at http://localhost:5173

# 6. Admin Frontend
cd frontend/admin-app
npm install
npm run dev
# Admin app runs at http://localhost:5174
```

### Docker Compose (Production)

> **TODO**: Configure after Phase 14

```bash
cd docker
cp .env.example .env
# Edit .env with your configuration
docker compose up --build
```

---

## API Documentation

- **Swagger UI**: `http://localhost:8080/api/v1/swagger-ui.html` (Backend)
- **Postman Collection**: `postman/WOODFURNI.postman_collection.json`

---

## Environment Variables

### Backend (Spring Boot)

| Variable | Default | Description |
|---|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017/woodfurni` | MongoDB connection string |
| `JWT_SECRET` | `your-secret-key-min-32-chars` | JWT signing key |
| `SERVER_PORT` | `8080` | Server port |

### Gateway (Node.js)

| Variable | Default | Description |
|---|---|---|
| `JWT_SECRET` | `your-secret-key-min-32-chars` | JWT verification key (same as backend) |
| `BACKEND_URL` | `http://localhost:8080` | Spring Boot backend URL |
| `PORT` | `3000` | Gateway port |
| `INTERNAL_SECRET` | `internal-secret` | Secret for internal API calls |

### Frontend (Customer App)

| Variable | Default | Description |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:3000` | API Gateway URL |

### Frontend (Admin App)

| Variable | Default | Description |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:3000` | API Gateway URL |

---

## Development Phases

| Phase | Description | Status |
|---|---|---|
| 0 | Monorepo Setup | ✅ **Current** |
| 1 | Spring Boot Foundation | ⬜ |
| 2 | Authentication (M01) | ⬜ |
| 3 | Catalog - Category, Material, Product (M02-M04) | ⬜ |
| 4 | Inventory (M07) | ⬜ |
| 5 | Cart (M05) | ⬜ |
| 6 | Promotion (M08) | ⬜ |
| 7 | Order + Payment (M06) | ⬜ |
| 8 | Review (M09) | ⬜ |
| 9 | Administration / Reporting (M10) | ⬜ |
| 10 | Node.js Gateway & Realtime | ⬜ |
| 11 | React Customer Website | ⬜ |
| 12 | React Admin Portal | ⬜ |
| 13 | Testing | ⬜ |
| 14 | Docker & Deployment | ⬜ |

---

## Key Features

### Product-Specific Attributes
- Wood types (Oak, Walnut, Pine, Acacia, Teak, etc.)
- Environment suitability (Indoor/Outdoor/Both)
- Room categorization (Living Room, Bedroom, Dining Room, Office, Garden, etc.)
- Dimensions (W × H × D in cm) and weight (kg)
- Finish type and color

### Realtime Features
- Order status notifications via WebSocket
- Low-stock alerts for warehouse staff
- Live dashboard updates for admins

### Smart Inventory
- Atomic reservation during checkout
- Automatic stock release on order cancellation
- Low-stock threshold alerts

---

## License

This project is developed for academic purposes (KLTN - Khóa Luận Tốt Nghiệp).

---

## Contact

**Mộc Việt Furniture** - Woodfurni E-Commerce Platform
