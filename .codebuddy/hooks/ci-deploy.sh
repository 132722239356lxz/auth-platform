#!/usr/bin/env bash
# =============================================================================
# CodeBuddy CI/CD 安全部署钩子
# =============================================================================
# 集成到 guard.sh 的 CI/CD 模式, 在部署命令执行前进行最终安全确认
# 仅当 APP_PROFILE=prod 或被识别为部署到外部服务器时触发额外检查
# =============================================================================
set -uo pipefail

INPUT="$(cat)"
ROOT="${CODEBUDDY_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null || echo '')}"

if [ -z "$ROOT" ]; then
  exit 0
fi

# 大小写不敏感匹配
matches() { echo "$INPUT" | grep -Eqi "$1"; }

# ────────────────────────────────────────────────────────────
# 识别 CI/CD 部署命令
# ────────────────────────────────────────────────────────────
is_deploy_command() {
  matches '(docker-compose\s+up|docker\s+stack\s+deploy|kubectl\s+apply'`
`'|helm\s+upgrade|bash\s+scripts/deploy\.sh|terraform\s+apply'`
`'|ansible-playbook|scp\s+.*\.jar|rsync\s+.*deploy)'
}

is_production_deploy() {
  matches '(-Pprod|--env\s+prod|prod|production)'
}

if ! is_deploy_command; then
  exit 0
fi

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║  🚀 CI/CD 部署安全确认                                ║"
echo "╚══════════════════════════════════════════════════════╝"

# ────────────────────────────────────────────────────────────
# 生产部署额外检查
# ────────────────────────────────────────────────────────────
if is_production_deploy; then
  echo ""
  echo "  ⚠️  检测到生产环境部署命令!"
  echo "  ─────────────────────────────────────────"
  echo "  将依次检查以下项目:"
  echo ""

  # 检查 1: 数据库 SQL 脚本无破坏性操作
  echo "  [1/4] 检查 SQL 脚本..."
  if find "$ROOT/data" -name "*.sql" -exec grep -li "DROP\|TRUNCATE\|DELETE FROM" {} \; 2>/dev/null | head -3; then
    echo "  ❌ SQL 脚本包含危险操作 (DROP/TRUNCATE/DELETE), 禁止部署!"
    exit 1
  else
    echo "  ✅ SQL 脚本安全"
  fi

  # 检查 2: 确认没有调试日志
  echo "  [2/4] 检查生产配置..."
  GREP_DEBUG=$(find "$ROOT" -path "*/nacos-config-import/*" -name "*.yml" -exec grep -l "level.*:\s*DEBUG\|level.*:\s*TRACE" {} \; 2>/dev/null || true)
  if [ -n "$GREP_DEBUG" ]; then
    echo "  ❌ 生产配置中发现 DEBUG/TRACE 日志级别, 禁止部署!"
    echo "  文件: $GREP_DEBUG"
    exit 1
  else
    echo "  ✅ 日志级别检查通过"
  fi

  # 检查 3: 数据库变更是否有备份
  echo "  [3/4] 检查数据备份提醒..."
  echo "  ⚠️  请确认已备份数据库 (auth_platform + auth_log)"
  echo "  ⚠️  建议先执行: docker exec mysql mysqldump -u root -p auth_platform > backup.sql"

  # 检查 4: 是否有未提交变更
  echo "  [4/4] Git 状态检查..."
  if git diff --quiet 2>/dev/null && git diff --cached --quiet 2>/dev/null; then
    echo "  ✅ 工作区干净, 无未提交变更"
  else
    echo "  ⚠️  存在未提交的变更, 请确认是否需要先提交"
    git status --short 2>/dev/null | head -5
  fi

  echo ""
  echo "  ─────────────────────────────────────────"
  echo "  以上 4 项检查完成。"
  echo "  部署前请确保:"
  echo "  1. 已通过 guard-build.sh 安全扫描"
  echo "  2. 已完成数据库备份"
  echo "  3. 已通知相关人员"
  echo "  4. 已准备好回滚方案"
  echo ""
fi

exit 0
