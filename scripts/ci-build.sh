# =============================================================================
# CI/CD 集成构建脚本 — 统一入口
# =============================================================================
# 用法:
#   bash scripts/ci-build.sh          # 完整 CI 流水线 (安全扫描 → 测试 → 打包)
#   bash scripts/ci-build.sh --skip-test   # 跳过测试
#   bash scripts/ci-build.sh --env prod     # 指定环境
#   bash scripts/ci-build.sh --service auth-server  # 仅构建指定服务
#   bash scripts/ci-build.sh --dry-run        # 仅扫描, 不构建
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# =============================================================================
# 参数解析
# =============================================================================
APP_PROFILE="dev"
APP_VERSION="${APP_VERSION:-1.0-SNAPSHOT}"
SKIP_TEST=false
SKIP_BUILD=false
DRY_RUN=false
SKIP_FRONTEND=false
SERVICE_TARGET=""
BUILD_TIMESTAMP=$(date +%Y%m%d-%H%M%S)

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)      APP_PROFILE="$2";        shift 2 ;;
    --version)  APP_VERSION="$2";        shift 2 ;;
    --skip-test) SKIP_TEST=true;         shift ;;
    --dry-run)  DRY_RUN=true;            shift ;;
    --skip-frontend) SKIP_FRONTEND=true; shift ;;
    --service)  SERVICE_TARGET="$2";     shift 2 ;;
    --help)
      echo "CI/CD Build Script"
      echo "  用法: bash scripts/ci-build.sh [选项]"
      echo "  --env <dev|test|prod>   目标环境 (默认: dev)"
      echo "  --version <v>           版本号 (默认: 1.0-SNAPSHOT)"
      echo "  --skip-test             跳过测试"
      echo "  --skip-frontend         跳过前端构建"
      echo "  --dry-run               仅安全检查, 不构建"
      echo "  --service <name>        仅构建指定微服务"
      exit 0
      ;;
    *) echo "未知参数: $1"; exit 1 ;;
  esac
done

# =============================================================================
# 工具函数
# =============================================================================
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
BOLD='\033[1m'

log_step()  { printf "\n${BLUE}${BOLD}═══ 步骤: %s ═══${NC}\n" "$*"; }
log_ok()    { printf "${GREEN}  ✅ %s${NC}\n" "$*"; }
log_err()   { printf "${RED}  ❌ %s${NC}\n" "$*"; }
log_warn()  { printf "${YELLOW}  ⚠️  %s${NC}\n" "$*"; }
log_info()  { printf "  📋 %s\n" "$*"; }

separator() { printf "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}\n"; }

# =============================================================================
# 环境变量注入
# =============================================================================
export APP_PROFILE APP_VERSION BUILD_TIMESTAMP CI=true

# CI 模式下标记 build-approved (跳过 guard.sh 交互确认)
export BUILD_APPROVED=1

# 微服务模块列表 (按依赖顺序)
ALL_SERVICES=("gateway" "auth-server" "system-server" "auth-flow" "auth-message" "ai-agent-server" "log-server" "knife4j-aggregation")

# 如指定了单个服务, 则只构建该服务
if [ -n "$SERVICE_TARGET" ]; then
  SERVICES=("$SERVICE_TARGET")
else
  SERVICES=("${ALL_SERVICES[@]}")
fi

# =============================================================================
# 流水线开始
# =============================================================================
echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║  🔧 Auth Platform CI/CD Build Pipeline                  ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  环境:     ${APP_PROFILE}"
echo "║  版本:     ${APP_VERSION}"
echo "║  时间:     ${BUILD_TIMESTAMP}"
echo "║  模式:     $(if $DRY_RUN; then echo '仅扫描'; else echo '完整构建'; fi)"
echo "║  测试:     $(if $SKIP_TEST; then echo '跳过'; else echo '运行'; fi)"
echo "║  服务:     ${SERVICE_TARGET:-全部}"
echo "╚══════════════════════════════════════════════════════════╝"

# ────────────────────────────────────────────────────────────
# Step 1/6: 基础设施检查
# ────────────────────────────────────────────────────────────
log_step "1/6 基础设施检查"

# Java 版本
JAVA_VER=$(java -version 2>&1 | head -1 || echo "NOT_FOUND")
log_info "Java:  ${JAVA_VER}"

# Maven 版本
MVN_VER=$(mvn --version 2>/dev/null | head -1 || echo "NOT_FOUND")
log_info "Maven: ${MVN_VER}"

# Node 版本 (前端)
if $SKIP_FRONTEND; then
  log_info "Node:  跳过 (--skip-frontend)"
else
  NODE_VER=$(node --version 2>/dev/null || echo "NOT_FOUND")
  NPM_VER=$(npm --version 2>/dev/null || echo "NOT_FOUND")
  log_info "Node:  ${NODE_VER}, npm: ${NPM_VER}"
fi

# Docker 版本
DOCKER_VER=$(docker --version 2>/dev/null || echo "NOT_FOUND")
log_info "Docker: ${DOCKER_VER}"
echo ""

# ────────────────────────────────────────────────────────────
# Step 2/6: 安全检查 (必过)
# ────────────────────────────────────────────────────────────
log_step "2/6 项目安全检查"

GUARD_BUILD="$ROOT/.codebuddy/hooks/guard-build.sh"
if [ -f "$GUARD_BUILD" ]; then
  log_info "运行 guard-build.sh 安全扫描..."

  SCAN_OUTPUT=$(bash "$GUARD_BUILD" 2>&1) || SCAN_RC=$?
  echo "$SCAN_OUTPUT"

  # 检查是否有严重/高危问题
  if echo "$SCAN_OUTPUT" | grep -q "严重: [1-9]"; then
    log_err "发现严重安全风险, 构建终止!"
    log_err "请首先修复所有严重级别的安全问题后再重试。"
    log_info "提示: 运行 'bash .codebuddy/hooks/guard-build.sh' 查看详细报告"
    exit 1
  fi

  if echo "$SCAN_OUTPUT" | grep -q "⚠️.*包含 [1-9].*个严重问题"; then
    log_err "存在安全风险, 构建终止!"
    exit 1
  fi

  log_ok "安全检查通过"
else
  log_warn "guard-build.sh 不存在, 跳过安全检查"
fi

# ────────────────────────────────────────────────────────────
# Step 3/6: 编译 + 单元测试
# ────────────────────────────────────────────────────────────
log_step "3/6 后端编译与测试"

if $SKIP_TEST; then
  log_info "跳过测试 (--skip-test)"
  TEST_FLAG="-DskipTests"
else
  TEST_FLAG=""
fi

if [ -n "$SERVICE_TARGET" ]; then
  # 单服务构建
  log_info "构建目标: ${SERVICE_TARGET}"
  mvn clean package $TEST_FLAG -pl "${SERVICE_TARGET}" -am -B -P"${APP_PROFILE}" 2>&1 | tail -20
else
  # 全量构建
  log_info "全量构建所有微服务..."
  mvn clean package $TEST_FLAG -B -P"${APP_PROFILE}" 2>&1 | tail -30
fi

BUILD_RC=${PIPESTATUS[0]}
if [ "$BUILD_RC" -ne 0 ]; then
  log_err "Maven 构建失败 (exit code: $BUILD_RC)"
  exit $BUILD_RC
fi
log_ok "后端构建成功"

# ────────────────────────────────────────────────────────────
# Step 4/6: 前端构建 (可选)
# ────────────────────────────────────────────────────────────
if ! $SKIP_FRONTEND && [ -z "$SERVICE_TARGET" ]; then
  log_step "4/6 前端构建"

  if [ -d "$ROOT/frontend-web" ]; then
    log_info "构建 frontend-web (管理后台)..."
    cd "$ROOT/frontend-web"
    npm install --silent 2>&1 | tail -3
    npm run build 2>&1 | tail -10
    cd "$ROOT"
    log_ok "frontend-web 构建完成"
  else
    log_warn "frontend-web 目录不存在, 跳过"
  fi

  if [ -d "$ROOT/frontend-app" ]; then
    log_info "构建 frontend-app (H5)..."
    cd "$ROOT/frontend-app"
    npm install --silent 2>&1 | tail -3
    npm run build:h5 2>&1 | tail -10
    cd "$ROOT"
    log_ok "frontend-app 构建完成"
  else
    log_warn "frontend-app 目录不存在, 跳过"
  fi
else
  log_step "4/6 前端构建 (跳过)"
fi

# ────────────────────────────────────────────────────────────
# Step 5/6: Docker 镜像构建
# ────────────────────────────────────────────────────────────
log_step "5/6 Docker 镜像构建"

DOCKER_IMAGES=()
for svc in "${SERVICES[@]}"; do
  # common-core/subsystem-sdk/resource-server-starter 不需要独立镜像
  case "$svc" in
    common-core|subsystem-sdk|resource-server-starter) continue ;;
  esac

  JAR_DIR="$ROOT/${svc}/target"
  JAR_FILE=$(ls "$JAR_DIR"/*.jar 2>/dev/null | grep -v sources | head -1 || echo "")

  if [ -z "$JAR_FILE" ]; then
    log_warn "${svc}: 未找到 JAR, 跳过 Docker 构建"
    continue
  fi

  IMAGE_NAME="auth-platform/${svc}:${APP_VERSION}"
  log_info "构建镜像: ${IMAGE_NAME}..."

  docker build \
    --build-arg "JAR_FILE=${svc}/target/$(basename "$JAR_FILE")" \
    --build-arg "APP_PROFILE=${APP_PROFILE}" \
    -t "$IMAGE_NAME" \
    -t "auth-platform/${svc}:latest" \
    -f "$ROOT/Dockerfile" \
    "$ROOT" 2>&1 | tail -5

  if [ ${PIPESTATUS[0]} -ne 0 ]; then
    log_err "${svc} Docker 镜像构建失败!"
    exit 1
  fi
  DOCKER_IMAGES+=("$IMAGE_NAME")
done

log_ok "Docker 镜像构建完成 (${#DOCKER_IMAGES[@]} 个)"

# ────────────────────────────────────────────────────────────
# Step 6/6: 构建产物汇总
# ────────────────────────────────────────────────────────────
log_step "6/6 构建产物汇总"
separator

echo "  📦 JAR 包:"
for svc in "${SERVICES[@]}"; do
  case "$svc" in
    common-core|subsystem-sdk|resource-server-starter) continue ;;
  esac
  JAR_FILE=$(ls "$ROOT/${svc}/target"/*.jar 2>/dev/null | grep -v sources | head -1 || echo "")
  if [ -n "$JAR_FILE" ]; then
    JAR_SIZE=$(du -h "$JAR_FILE" | cut -f1)
    echo "    ${svc}: $(basename "$JAR_FILE") (${JAR_SIZE})"
  fi
done

if [ ${#DOCKER_IMAGES[@]} -gt 0 ]; then
  echo ""
  echo "  🐳 Docker 镜像:"
  for img in "${DOCKER_IMAGES[@]}"; do
    echo "    $img"
  done
fi

separator
echo ""
echo "  ${GREEN}${BOLD}✅ CI/CD 构建流水线完成!${NC}"
echo ""
echo "  下一步部署:"
echo "    docker-compose up -d              # 部署全部服务"
echo "    docker-compose up -d ${SERVICE_TARGET:-gateway}  # 部署指定服务"
echo "    bash scripts/deploy.sh --env ${APP_PROFILE}       # 使用部署脚本"
echo ""

exit 0
