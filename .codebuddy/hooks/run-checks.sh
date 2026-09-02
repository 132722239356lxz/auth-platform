#!/usr/bin/env bash
# 公共提交前检查逻辑（被原生 Git 钩子与 CodeBuddy 钩子共用）
# 1) 全模块 Maven 编译校验（skip tests，避免提交无法编译的代码）
# 2) 密钥/凭证防护：拦截疑似敏感文件进入暂存区
# 退出码：0 通过；1 不通过（原因打印到 stdout）
set -uo pipefail

ROOT="${CODEBUDDY_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null)}"
if [ -z "$ROOT" ]; then
  echo "无法定位项目根目录，跳过检查（请确认在 git 仓库内执行）。"
  exit 1
fi
cd "$ROOT" || exit 1

echo "==> [check] 校验全模块编译 (mvn -DskipTests compile) ..."
if ! mvn -q -DskipTests -T1C compile; then
  echo "预提交检查失败：Maven 编译未通过。请先修复编译错误后再提交。"
  echo "如需强制跳过检查：git commit --no-verify"
  exit 1
fi

# 密钥/凭证防护：检查暂存区是否包含疑似敏感文件
SECRET_HITS="$(git diff --cached --name-only --diff-filter=ACM 2>/dev/null \
  | grep -Ei '\.env$|credentials|id_rsa|\.pem$|secrets?\.ya?ml$' || true)"
if [ -n "$SECRET_HITS" ]; then
  echo "预提交检查失败：检测到疑似密钥/凭证文件将被提交："
  echo "$SECRET_HITS"
  echo "请确认这些文件不应入库（加入 .gitignore 或使用 git rm --cached）。"
  echo "如需强制跳过检查：git commit --no-verify"
  exit 1
fi

echo "==> [check] ✅ 检查通过。"
exit 0
