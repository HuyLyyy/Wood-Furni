# =============================================================================
# WOODFURNI Admin SPA — React (Vite) + nginx
# =============================================================================

# ---------- Stage 1: build ----------
FROM node:20-alpine AS build
WORKDIR /app

COPY frontend/admin-app/package*.json ./
RUN npm ci

COPY frontend/admin-app/ ./
ARG VITE_API_BASE_URL
ARG VITE_SOCKET_URL
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_SOCKET_URL=$VITE_SOCKET_URL

RUN npm run build

# ---------- Stage 2: runtime ----------
FROM nginx:1.27-alpine
WORKDIR /usr/share/nginx/html

COPY docker/nginx-frontend.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist ./

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:80/ > /dev/null || exit 1

CMD ["nginx", "-g", "daemon off;"]