#!/bin/bash
# =============================================================================
# Mongo container entrypoint — chạy sau khi mongod sẵn sàng.
# Áp dụng toàn bộ scripts trong /docker-entrypoint-initdb.d/indexes/*.js
# để tạo index MongoDB (Mongo chạy script theo thứ tự alphabet).
#
# Idempotent: createIndex với cùng spec là no-op.
# =============================================================================
set -e

echo "[mongo-entrypoint] waiting for mongod to be ready..."
until mongosh --quiet --eval "db.adminCommand('ping').ok" >/dev/null 2>&1; do
    sleep 1
done

echo "[mongo-entrypoint] applying /docker-entrypoint-initdb.d/*.js ..."
for f in /docker-entrypoint-initdb.d/*.js; do
    if [ -f "$f" ]; then
        echo "[mongo-entrypoint] -> $f"
        mongosh "$MONGO_INITDB_DATABASE" --quiet "$f" || \
            echo "[mongo-entrypoint] WARN: $f failed (continuing)"
    fi
done

echo "[mongo-entrypoint] done."