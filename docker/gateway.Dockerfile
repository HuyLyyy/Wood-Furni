# =============================================================================
# WOODFURNI Gateway — Node.js + Nginx Reverse Proxy
#
# Architecture:
#   nginx (:3000) → proxies:
#     /api/*        → backend Spring Boot (:8080)
#     /socket.io/*  → Node.js Socket.IO server (:3001)
#     /internal/*   → Node.js notification routes (:3001)
#     /health       → Node.js health check (:3001)
#
# Stage 1: install Node.js dependencies
# Stage 2: runtime with nginx frontend proxying to node app
# =============================================================================

# ---------- Stage 1: deps ----------
FROM node:20-alpine AS deps
WORKDIR /app
COPY gateway/node-gateway/package*.json ./
RUN npm install --include=dev

# ---------- Stage 2: runtime ----------
FROM node:20-alpine
WORKDIR /app

ENV NODE_ENV=production

# Cài nginx + gettext (for envsubst)
RUN apk add --no-cache nginx gettext

# Tạo nginx dirs + user
RUN mkdir -p /var/cache/nginx/client_temp \
             /var/cache/nginx/proxy_temp \
             /var/log/nginx \
             /run

RUN addgroup -S -g 1001 woodfurni \
    && adduser -S -u 1001 -G woodfurni woodfurni

# Copy node_modules + source
COPY --from=deps /app/node_modules ./node_modules
COPY gateway/node-gateway/ ./

# Node app chạy trên internal port 3001, nginx chuyển tiếp vào đây
ENV PORT=3001
ENV BACKEND_BASE_URL=http://backend:8080

# Nginx reverse-proxy config — copy vào container
COPY docker/gateway-nginx.conf /etc/nginx/http.d/default.conf

# Fix permissions
RUN touch /run/nginx.pid \
    && chown woodfurni:woodfurni /run/nginx.pid /var/log/nginx \
    && chmod 755 /var/log/nginx

# Expose cổng nginx (3000)
EXPOSE 3000

# Healthcheck trên nginx (nginx health endpoint)
HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=15s \
    CMD wget -qO- http://localhost:3000/health > /dev/null || exit 1

# Start nginx (foreground) + node app
CMD ["sh", "-c", "nginx -g 'daemon off;' & exec node src/app.js"]
