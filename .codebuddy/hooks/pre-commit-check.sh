#!/usr/bin/env bash
# CodeBuddy Code PreToolUse 钩子
# 拦截 CodeBuddy 自身执行的 `git commit`：先跑公共检查，未通过则阻止提交。
# 通过 .codebuddy/settings.json 注册（matcher: Bash）。
# 注意：CodeBuddy 钩子在 Windows 上由 Git Bash 执行；命令需兼容 bash。
set -uo pipefail

INPUT="$(cat)"

# 仅对 Bash 工具中的 git commit 生效
echo "$INPUT" | grep -Eq 'git[[:space:]]+commit' || exit 0
# 用户显式跳过（--no-verify）时不拦截
echo "$INPUT" | grep -q -- '--no-verify' && exit 0

ROOT="${CODEBUDDY_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"
CHECK="$ROOT/.codebuddy/hooks/run-checks.sh"
[ -f "$CHECK" ] || exit 0

OUT="$(bash "$CHECK" 2>&1)"; RC=$?
if [ "$RC" -ne 0 ]; then
  # 退出码 2 = 阻止工具调用；消息以 stdout 为准
  printf '%s\n' "$OUT"
  echo "已阻止 CodeBuddy 执行 git commit。请先修复问题后再提交，或显式加 --no-verify 跳过检查。"
  exit 2
fi
exit 0
