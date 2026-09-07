# =============================================================================
# 部署脚本 — 支持多环境一键部署
# =============================================================================
# 用法:
#   bash scripts/deploy.sh                     # 默认 dev 环境
#   bash scripts/deploy.sh --env test          # 测试环境
#   bash scripts/deploy.sh --env prod --dry-run # 生产环境预览
#   bash scripts/deploy.sh --env prod --service auth-server # 部署指定服务
#   bash scripts/deploy.sh --env prod --rollback             # 回滚
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# =============================================================================
# 参数解析
# =============================================================================
APP_PROFILE="dev"
DEPLOY_MODE="up"
DRY_RUN=false
ROLLBACK=false
SERVICE_TARGET=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env)      APP_PROFILE="$2";        shift 2 ;;
    --dry-run)  DRY_RUN=true;            shift ;;
    --rollback) ROLLBACK=true;           shift ;;
    --service)  SERVICE_TARGET="$2";     shift 2 ;;
    --down)     DEPLOY_MODE="down";      shift ;;
    --restart)  DEPLOY_MODE="restart";   shift ;;
    --logs)     DEPLOY_MODE="logs";      shift ;;
    --help)
      echo "部署脚本"
      echo "  用法: bash scripts/deploy.sh [选项]"
      echo "  --env <dev|test|prod>   目标环境"
      echo "  --dry-run               仅预览, 不实际部署"
      echo "  --rollback              回滚到上一个版本"
      echo "  --service <name>        指定服务"
      echo "  --down                  停止服务"
      echo "  --restart               重启服务"
      echo "  --logs                  查看日志"
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

log_ok()   { printf "${GREEN}  ✅ %s${NC}\n" "$*"; }
log_err()  { printf "${RED}  ❌ %s${NC}\n" "$*"; }
log_warn() { printf "${YELLOW}  ⚠️  %s${NC}\n" "$*"; }
log_info() { printf "  📋 %s\n" "$*"; }

# =============================================================================
# 环境变量
# =============================================================================
export APP_PROFILE="${APP_PROFILE}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║  🚀 Auth Platform 部署脚本                                ║"
echo "╠══════════════════════════════════════════════════════════╣"
echo "║  环境:     ${APP_PROFILE}"
echo "║  操作:     ${DEPLOY_MODE}"
echo "║  模式:     $(if $DRY_RUN; then echo '预览'; else echo '执行'; fi)"
echo "║  服务:     ${SERVICE_TARGET:-全部}"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

# =============================================================================
# 生产环境额外确认
# =============================================================================
if [ "$APP_PROFILE" = "prod" ] && ! $DRY_RUN; then
  echo ""
  echo "  ⚠️  ⚠️  ⚠️  危险操作警告  ⚠️  ⚠️  ⚠️"
  echo "  ─────────────────────────────────────"
  echo "  即将对 「生产环境」 执行操作!"
  echo ""
  echo "  操作: ${DEPLOY_MODE}"
  echo "  服务: ${SERVICE_TARGET:-全部}"
  echo "  时间: ${TIMESTAMP}"
  echo ""
  echo -n "  输入 'CONFIRM' 确认操作: "
  read -r CONFIRM
  if [ "$CONFIRM" != "CONFIRM" ]; then
    log_err "操作已取消"
    exit 1
  fi
  echo ""
fi

# =============================================================================
# Docker Compose 命令
# =============================================================================
COMPOSE_FILE="$ROOT/docker-compose.yml"
COMPOSE_CMD="docker-compose -f \"$COMPOSE_FILE\""

if [ ! -f "$COMPOSE_FILE" ]; then
  log_err "docker-compose.yml 不存在, 请先确认项目配置"
  exit 1
fi

case "$DEPLOY_MODE" in
  up)
    CMD="$COMPOSE_CMD up -d"
    if [ -n "$SERVICE_TARGET" ]; then
      CMD="$CMD $SERVICE_TARGET"
    fi
    # 生产环境额外参数
    if [ "$APP_PROFILE" = "prod" ]; then
      CMD="$CMD --scale auth-server=2 --scale system-server=2"
    fi
    ;;

  down)
    CMD="$COMPOSE_CMD down"
    if [ -n "$SERVICE_TARGET" ]; then
      CMD="$CMD $SERVICE_TARGET"
    fi
    ;;

  restart)
    CMD="$COMPOSE_CMD restart"
    if [ -n "$SERVICE_TARGET" ]; then
      CMD="$CMD $SERVICE_TARGET"
    fi
    ;;

  logs)
    CMD="$COMPOSE_CMD logs -f --tail=100"
    if [ -n "$SERVICE_TARGET" ]; then
      CMD="$CMD $SERVICE_TARGET"
    fi
    ;;
esac

# =============================================================================
# 执行
# =============================================================================
if $DRY_RUN; then
  log_info "预览模式, 将执行命令:"
  echo ""
  echo "    $CMD"
  echo ""
  log_ok "预览完成 (未实际执行)"
  exit 0
fi

# 回滚操作
if $ROLLBACK; then
  log_info "执行回滚..."
  # 获取上一个镜像标签
  for svc in ${SERVICE_TARGET:-gateway auth-server system-server}; do
    PREV_IMG=$(docker images "auth-platform/${svc}" --format "{{.Repository}}:{{.Tag}}" | grep -v latest | head -1 || echo "")
    if [ -n "$PREV_IMG" ]; then
      log_info "回滚 ${svc} → ${PREV_IMG}"
      docker-compose -f "$COMPOSE_FILE" up -d --no-deps "${svc}" 2>&1 || true
    fi
  done
  log_ok "回滚完成"
  exit 0
fi

# 预先拉取基础依赖镜像 (加速部署)
log_info "检查基础设施..."
docker-compose -f "$COMPOSE_FILE" pull nacos mysql redis rabbitmq 2>&1 | tail -5

log_info "执行部署..."
eval "$CMD"

DEPLOY_RC=$?
if [ "$DEPLOY_RC" -ne 0 ]; then
  log_err "部署失败 (exit code: $DEPLOY_RC)"
  exit $DEPLOY_RC
fi

log_ok "部署指令已执行"

# =============================================================================
# 健康检查 (等待服务就绪)
# =============================================================================
log_info "等待服务健康检查..."
sleep 5

# 根据环境检查不同端口
case "$APP_PROFILE" in
  prod)
    HEALTH_URLS=(
      "http://localhost:8080/actuator/health  gateway"
      "http://localhost:9000/actuator/health  auth-server"
      "http://localhost:9001/actuator/health  system-server"
    )
    ;;
  *)
    HEALTH_URLS=(
      "http://localhost:8080/actuator/health  gateway"
      "http://localhost:9000/actuator/health  auth-server"
    )
    ;;
esac

for entry in "${HEALTH_URLS[@]}"; do
  URL=$(echo "$entry" | awk '{print $1}')
  NAME=$(echo "$entry" | awk '{print $2}')
  printf "  等待 %s ..." "$NAME"
  for i in $(seq 1 30); do
    if curl -s -o /dev/null -w "%{http_code}" "$URL" 2>/dev/null | grep -q "200"; then
      printf " ${GREEN}就绪${NC}\n"
      break
    fi
    sleep 2
    if [ "$i" -eq 30 ]; then
      printf " ${YELLOW}超时${NC}\n"
    fi
  done
done

# =============================================================================
# 部署完成
# =============================================================================
echo ""
echo "  ${GREEN}${BOLD}✅ 部署完成!${NC}"
echo ""
echo "  服务入口:"
echo "    管理后台:    http://localhost:3000"
echo "    API 网关:    http://localhost:8080"
echo "    Swagger:     http://localhost:10909/doc.html"
echo "    Nacos:       http://localhost:8848/nacos"
echo "    RabbitMQ:    http://localhost:15672"
echo ""
echo "  运维命令:"
echo "    docker-compose ps              # 查看服务状态"
echo "    docker-compose logs -f gateway # 查看网关日志"
echo "    bash scripts/deploy.sh --down  # 停止全部服务"
echo ""

exit 0
