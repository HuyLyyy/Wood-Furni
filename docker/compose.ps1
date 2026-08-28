# =============================================================================
# WOODFURNI — Docker Compose launcher (Windows PowerShell)
# =============================================================================
# Tại sao cần script này?
# - Docker Compose v2.20+ bật BuildKit/Bake mặc định. Trên một số bản Docker
#   Desktop cho Windows (đặc biệt khi build context nằm trên WSL2/Hyper-V
#   filesystem mount), BuildKit gặp lỗi "EOF" / "read/write on closed pipe"
#   trong khi classic builder vẫn hoạt động bình thường.
# - Script này ép classic builder + Docker CLI builder cho cả session,
#   bypass hoàn toàn BuildKit/Bake.
#
# Dùng:
#   .\compose.ps1 build           # build tất cả services
#   .\compose.ps1 up              # build + start
#   .\compose.ps1 up -d           # background
#   .\compose.ps1 down            # stop + remove containers
#   .\compose.ps1 logs -f gateway # xem log
#   .\compose.ps1 ps              # list containers
#   .\compose.ps1 exec backend sh # exec vào container
# =============================================================================

# ---- Tắt BuildKit & Bake cho toàn bộ session ------------------------------
$env:DOCKER_BUILDKIT = "0"
$env:COMPOSE_DOCKER_CLI_BUILD = "0"
$env:COMPOSE_BAKE = "false"

# Một số bản Compose v2.20+ dùng builder name "default" cho classic
# Bên dưới docker compose v2.24+ có thể cần --builder=default — bật nếu cần
# $env:COMPOSE_BUILDER = "default"

# ---- Forward tất cả args sang docker compose -------------------------------
docker compose @args
