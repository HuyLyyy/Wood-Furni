# =============================================================================
# WOODFURNI Customer SPA — React (Vite) + nginx
#   Stage 1: build production bundle với Vite (biến VITE_API_BASE_URL bake
#            vào bundle lúc build — truyền qua docker-compose args).
#   Stage 2: nginx:1.27-alpine serve static dist/ + SPA fallback.
#
# NOTE: VITE_API_BASE_URL được bake vào JS bundle tại build time. Sau khi
# image build, KHÔNG thể đổi URL trừ khi rebuild. Đây là đặc tính của Vite,
# không phải Docker.
# =============================================================================

# ---------- Stage 1: build ----------
FROM node:20-alpine AS build
WORKDIR /app

# Chỉ copy package.json + lock trước để tận dụng Docker layer cache
COPY frontend/customer-app/package*.json ./
RUN npm ci

# Copy source & build
COPY frontend/customer-app/ ./
ARG VITE_API_BASE_URL
ARG VITE_SOCKET_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_SOCKET_URL=$VITE_SOCKET_URL

RUN npm run build

# ---------- Stage 2: runtime ----------
FROM nginx:1.27-alpine
WORKDIR /usr/share/nginx/html

# Copy nginx config riêng (SPA fallback + cache headers)
COPY docker/nginx-frontend.conf /etc/nginx/conf.d/default.conf

# Copy built bundle
COPY --from=build /app/dist ./

# Nginx alpine mặc định chạy user nginx (uid 101); không cần đổi.
EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:80/ > /dev/null || exit 1

CMD ["nginx", "-g", "daemon off;"]