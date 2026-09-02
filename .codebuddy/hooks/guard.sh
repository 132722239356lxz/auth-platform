#!/usr/bin/env bash
# =============================================================================
# CodeBuddy PreToolUse 综合安全守护钩子
# =============================================================================
# 功能:
#   1. 命令白名单 — 仅允许 npm/git/java/mvn/cd/dir/ls/netstat 等常规命令
#   2. SQL 整表删除防护 — 阻止 DROP TABLE / TRUNCATE TABLE 等危险 SQL
#   3. 打包部署安全检查 — mvn package/docker build 前自动审查安全问题
#   4. CI/CD 部署安全确认 — 生产部署前 4 项额外检查 (DB备份/日志级别/Git状态)
#   5. 文件/文件夹删除确认 — rm/del/rmdir 等需要用户确认
#   6. Git 危险操作确认 — reset --hard / push --force / revert 需要确认
#   7. 关键配置文件修改预警 — nacos配置/数据种子文件/安全代码变更需确认
# =============================================================================
set -uo pipefail

INPUT="$(cat)"
ROOT="${CODEBUDDY_PROJECT_DIR:-}"

# =============================================================================
# 工具函数
# =============================================================================
log_block()  { printf '\n========================================\n[GUARD-BLOCK] %s\n========================================\n\n' "$*"; }
log_confirm(){ printf '\n========================================\n[GUARD-CONFIRM] ⚠️  需要用户确认: %s\n========================================\n\n' "$*"; }
log_info()   { printf '[GUARD-INFO] %s\n' "$*"; }
log_warn()   { printf '[GUARD-WARN] ⚠️  %s\n' "$*"; }

# 大小写不敏感匹配
matches() { echo "$INPUT" | grep -Eqi "$1"; }

# 判断是否为仅查看类操作 (这些直接放行)
is_readonly() {
  matches '(git[[:space:]]+status|git[[:space:]]+log|git[[:space:]]+diff|git[[:space:]]+branch[[:space:]]*$|git[[:space:]]+stash[[:space:]]+list|git[[:space:]]+remote[[:space:]]+-v|git[[:space:]]+show)'
}

# =============================================================================
# 1. SQL 整表删除防护 (硬阻止)
# =============================================================================
check_sql_danger() {
  local BLOCKED=0

  # DROP TABLE / DATABASE
  if matches '(DROP[[:space:]]+TABLE|DROP[[:space:]]+DATABASE|DROP[[:space:]]+SCHEMA)'; then
    log_block "检测到 DROP TABLE/DATABASE/SCHEMA 语句！禁止直接删除整张表或数据库。"
    BLOCKED=1
  fi

  # TRUNCATE TABLE
  if matches 'TRUNCATE[[:space:]]+TABLE'; then
    log_block "检测到 TRUNCATE TABLE 语句！禁止清空整张表数据。"
    BLOCKED=1
  fi

  # DROP INDEX / VIEW / PROCEDURE / FUNCTION
  if matches '(DROP[[:space:]]+INDEX|DROP[[:space:]]+VIEW|DROP[[:space:]]+PROCEDURE|DROP[[:space:]]+FUNCTION)'; then
    log_block "检测到 DROP INDEX/VIEW/PROCEDURE/FUNCTION 语句！禁止删除数据库对象。"
    BLOCKED=1
  fi

  # ALTER TABLE ... DROP COLUMN
  if matches 'ALTER[[:space:]]+TABLE[[:space:]]+.*[[:space:]]+DROP[[:space:]]+(COLUMN[[:space:]]+)?'; then
    log_block "检测到 ALTER TABLE ... DROP COLUMN 语句！删除表列可能导致数据丢失。"
    BLOCKED=1
  fi

  # DELETE FROM without WHERE (全表删除)
  if matches 'DELETE[[:space:]]+FROM[[:space:]]+[a-zA-Z_][a-zA-Z0-9_]*[[:space:]]*;'; then
    log_block "检测到 DELETE FROM 语句缺少 WHERE 条件！危险的全表删除操作。"
    BLOCKED=1
  fi

  # DELETE FROM ... WHERE 1=1 (绕过方式)
  if matches 'DELETE[[:space:]]+FROM[[:space:]]+.*WHERE[[:space:]]+1[[:space:]]*=[[:space:]]*1'; then
    log_block "检测到 DELETE FROM ... WHERE 1=1 语句！这是删除全表数据的绕过写法。"
    BLOCKED=1
  fi

  if [ "$BLOCKED" -eq 1 ]; then
    echo ""
    echo "如需执行此类操作，请手动在数据库客户端中完成，或联系管理员。"
    echo "CodeBuddy AI 不允许通过代码生成或命令行方式删除整张表。"
    exit 2
  fi
}

# =============================================================================
# 2. 文件/文件夹删除确认 (需要用户确认)
# =============================================================================
check_delete_operations() {
  local NEED_CONFIRM=0
  local REASON=""

  # rm -rf / rm -r / rm -f  (Linux/Git Bash)
  if matches 'rm[[:space:]]+-[rRfF]'; then
    NEED_CONFIRM=1
    REASON="检测到 rm 递归/强制删除命令"
  fi

  # rmdir (Linux)
  if matches 'rmdir[[:space:]]'; then
    NEED_CONFIRM=1
    REASON="检测到 rmdir 删除目录命令"
  fi

  # del /f /s /q (Windows)
  if matches 'del[[:space:]]+/[fsqFSQ]'; then
    NEED_CONFIRM=1
    REASON="检测到 Windows del 强制删除命令"
  fi

  # rd /s /q (Windows 递归删除目录)
  if matches 'rd[[:space:]]+/[sqSQ]'; then
    NEED_CONFIRM=1
    REASON="检测到 Windows rd 递归删除目录命令"
  fi

  # erase (Windows)
  if matches 'erase[[:space:]]'; then
    NEED_CONFIRM=1
    REASON="检测到 Windows erase 删除命令"
  fi

  # 关键目录删除
  if matches 'rm[[:space:]].*(node_modules|\.git|src|target|dist)'; then
    NEED_CONFIRM=1
    REASON="检测到删除关键目录 (node_modules/.git/src/target/dist)"
  fi

  if [ "$NEED_CONFIRM" -eq 1 ]; then
    log_confirm "$REASON"
    echo "原因: $REASON"
    echo ""
    echo "此操作涉及文件/文件夹删除，需要您明确确认后才能执行。"
    echo "如果确认要执行，请回复 '确认删除' 或 'proceed with deletion'，我会重新执行。"
    exit 2
  fi
}

# =============================================================================
# 3. Git 危险操作确认
# =============================================================================
check_git_dangerous() {
  local NEED_CONFIRM=0
  local REASON=""

  # reset --hard (丢弃所有本地变更)
  if matches 'git[[:space:]]+reset[[:space:]]+--hard'; then
    NEED_CONFIRM=1
    REASON="git reset --hard 将丢弃所有未提交的本地变更"
  fi

  # push --force / push -f
  if matches 'git[[:space:]]+push[[:space:]].*(-f|--force|--force-with-lease)'; then
    NEED_CONFIRM=1
    REASON="git push --force 将覆盖远程仓库历史"
  fi

  # git revert (代码回退)
  if matches 'git[[:space:]]+revert'; then
    NEED_CONFIRM=1
    REASON="git revert 代码回退操作"
  fi

  # git clean
  if matches 'git[[:space:]]+clean[[:space:]]+-[fd]'; then
    NEED_CONFIRM=1
    REASON="git clean 将删除未跟踪的文件"
  fi

  # git stash drop
  if matches 'git[[:space:]]+stash[[:space:]]+drop'; then
    NEED_CONFIRM=1
    REASON="git stash drop 将永久删除暂存的变更"
  fi

  # git branch -D (强制删除分支)
  if matches 'git[[:space:]]+branch[[:space:]]+-D'; then
    NEED_CONFIRM=1
    REASON="git branch -D 将强制删除分支"
  fi

  if [ "$NEED_CONFIRM" -eq 1 ]; then
    log_confirm "Git 危险操作: $REASON"
    echo ""
    echo "此 Git 操作具有破坏性，需要您明确确认后才能执行。"
    echo "如果确认要执行，请回复 '确认操作' 或 'proceed'。"
    exit 2
  fi
}

# =============================================================================
# 4. 命令白名单
# =============================================================================
check_command_whitelist() {
  # 提取第一个有意义的命令词（跳过 cd、export、set 等环境命令）
  local CMD_LINE
  CMD_LINE=$(echo "$INPUT" | grep -oP '(?<="command":\s*")[^"]+' | head -1)

  # 如果 JSON 提取失败，尝试从原始输入提取
  if [ -z "$CMD_LINE" ]; then
    CMD_LINE=$(echo "$INPUT" | grep -oP '^\s*(?:cmd\.exe\s+/[sc]\s+/[kc]\s+")?\s*([a-zA-Z0-9_.-]+)' | sed 's/cmd\.exe.*"//' | head -1)
  fi

  # 跳过空行、注释、纯环境变量、cd 等无害操作
  if [ -z "$CMD_LINE" ]; then
    return 0
  fi

  # 提取第一个词作为主命令
  local MAIN_CMD
  MAIN_CMD=$(echo "$CMD_LINE" | awk '{print $1}' | tr -d '"')

  # 处理 cmd.exe /c 包装的情况
  if [ "$MAIN_CMD" = "cmd.exe" ] || [ "$MAIN_CMD" = "cmd" ]; then
    MAIN_CMD=$(echo "$CMD_LINE" | sed 's/cmd\.exe\s*\/[sc]\s*\/[kc]\s*"//' | awk '{print $1}')
  fi

  # 允许的命令白名单
  case "$MAIN_CMD" in
    # 包管理器
    npm|npx|yarn|pnpm)
      return 0 ;;
    # Node.js
    node)
      return 0 ;;
    # Java 生态
    java|javac|mvn|mvnw|jar|jps|jstack|jmap)
      return 0 ;;
    # 版本控制 (进一步受 git 危险操作检查约束)
    git)
      return 0 ;;
    # 导航和文件浏览
    cd|dir|ls|tree|pwd|pushd|popd)
      return 0 ;;
    # 文件读取
    type|cat|more|head|tail|sort|wc|uniq)
      return 0 ;;
    # 文本输出
    echo|printf|tee)
      return 0 ;;
    # 网络请求
    curl|wget)
      return 0 ;;
    # 文件搜索
    find|grep|findstr|where|which|locate)
      return 0 ;;
    # 目录创建
    mkdir)
      return 0 ;;
    # 文件复制/移动
    copy|xcopy|robocopy|move|mv|cp)
      return 0 ;;
    # 系统信息
    systeminfo|netstat|tasklist|ver|hostname|whoami|uname|nslookup|ping|tracert)
      return 0 ;;
    # 环境变量
    set|export|env|printenv)
      return 0 ;;
    # Docker
    docker|docker-compose)
      return 0 ;;
    # WSL
    wsl|bash|sh)
      return 0 ;;
    # Windows 工具
    powershell|pwsh)
      # 限制 PowerShell 只允许查看类操作
      if matches '(Get-|Select-|Where-|Format-|Out-|Write-|Test-|Resolve-|ConvertFrom-)'; then
        return 0
      fi
      log_block "PowerShell 仅允许查看类命令 (Get-/Select-/Format- 等)。当前操作被阻止。"
      echo "如需要，请手动在终端中执行 PowerShell 命令。"
      exit 2
      ;;
    # 构建工具
    gradle|gradlew|make|cmake)
      return 0 ;;
    # pip / python
    pip|pip3|python|python3|py)
      return 0 ;;
    # 压缩
    tar|zip|unzip|gzip|gunzip)
      return 0 ;;
    # 权限（仅查看）
    icacls|lsattr)
      if matches '(icacls.*\/grant|icacls.*\/deny|icacls.*\/setowner|chmod[[:space:]]+[0-7]|chown)'; then
        log_block "禁止修改文件权限。仅允许使用 icacls 查看权限。"
        exit 2
      fi
      return 0 ;;
    # CodeBuddy 内部或空命令
    ''|*CODEBUDDY*|*codebuddy*)
      return 0 ;;
    # 管道或重定向 (已由前面的命令处理)
    *)
      # 检查是否是子 shell 或变量赋值
      if echo "$MAIN_CMD" | grep -qE '^[A-Z_][A-Z0-9_]*='; then
        return 0
      fi
      # 检查是否是特殊字符
      if echo "$MAIN_CMD" | grep -qE '^[|&;><]$'; then
        return 0
      fi
      ;;
  esac

  log_block "命令 '$MAIN_CMD' 不在允许列表中。"
  echo ""
  echo "当前允许的命令类别:"
  echo "  📦 包管理:   npm, npx, yarn, pnpm, pip, gradle"
  echo "  ☕ Java:      java, javac, mvn, mvnw, jar"
  echo "  🔧 Git:      git (危险操作需确认)"
  echo "  📂 导航:     cd, dir, ls, tree, pwd"
  echo "  📖 文件读取: cat, type, more, head, tail"
  echo "  🔍 搜索:     grep, find, findstr, where"
  echo "  🌐 网络:     curl, wget, ping"
  echo "  💻 系统信息: systeminfo, netstat, tasklist"
  echo "  📁 目录创建: mkdir"
  echo "  📋 复制移动: copy, move, cp, mv"
  echo "  🐳 Docker:    docker, docker-compose"
  echo "  🪟 WSL:       wsl, bash"
  echo ""
  echo "如需添加命令 '$MAIN_CMD' 到白名单，请联系管理员修改 .codebuddy/hooks/guard.sh。"
  exit 2
}

# =============================================================================
# 5. 打包部署前安全检查
# =============================================================================
is_build_command() {
  matches '(mvn\s+(package|install|deploy|verify|spring-boot:build-image)'`
`'|mvnw\s+(package|install|deploy|verify)'`
`'|npm\s+run\s+build'`
`'|npm\s+run\s+deploy'`
`'|yarn\s+build'`
`'|yarn\s+deploy'`
`'|pnpm\s+build'`
`'|pnpm\s+deploy'`
`'|docker\s+build'`
`'|docker-compose\s+build'`
`'|docker\s+compose\s+build'`
`'|gradle\s+build'`
`'|gradlew\s+build'`
`'|gradle\s+assemble'`
`'|gradlew\s+assemble)'
}

is_build_approved() {
  matches '(BUILD_APPROVED=1|#\s*build-approved|--build-approved)'
}

check_build_security() {
  if ! is_build_command; then
    return 0
  fi

  # 用户已确认跳过，放行
  if is_build_approved; then
    log_info "用户已确认发布，跳过安全检查..."
    return 0
  fi

  printf '\n'
  printf '╔══════════════════════════════════════════════════╗\n'
  printf '║  📦 检测到打包/部署命令，启动安全检查...         ║\n'
  printf '╚══════════════════════════════════════════════════╝\n'

  local GUARD_BUILD="$ROOT/.codebuddy/hooks/guard-build.sh"
  if [ -f "$GUARD_BUILD" ]; then
    # 设置环境变量传递项目根路径
    export CODEBUDDY_PROJECT_DIR="$ROOT"
    bash "$GUARD_BUILD"
    local BUILD_RC=$?
    exit $BUILD_RC
  else
    log_warn "guard-build.sh 不存在，跳过安全检查"
    return 0
  fi
}

# =============================================================================
# 6. 关键配置文件保护 (预警)
# =============================================================================
check_critical_files() {
  local WARN=0

  # 修改 hooks 自身配置需要确认
  if matches '(\.codebuddy/hooks/|\.codebuddy/settings\.json)'; then
    if ! matches '(cat |type |more |head |tail |dir |ls )'; then
      WARN=1
      log_warn "检测到修改 CodeBuddy hooks/settings 配置的操作。"
    fi
  fi

  # 修改 Nacos 配置
  if matches 'nacos-config-import/'; then
    if ! matches '(cat |type |more |head |tail )'; then
      WARN=1
      log_warn "检测到修改 Nacos 配置中心文件的操作，可能影响多环境配置。"
    fi
  fi

  # 修改数据种子 SQL
  if matches 'data/[^/]*\.sql'; then
    if ! matches '(cat |type |more |head |tail )'; then
      WARN=1
      log_warn "检测到修改数据种子 SQL 文件 (data/*.sql)，可能影响数据库初始化。"
    fi
  fi

  # 修改安全相关代码
  if matches '(AuthorizationServerConfig\.java|JwkConfig\.java|CryptoConfig\.java|IpWhitelistFilter\.java|TokenAuthenticationFilter\.java|SecurityConfig\.java)'; then
    WARN=1
    log_warn "检测到修改安全核心代码，请确认变更内容。"
  fi

  if [ "$WARN" -eq 1 ]; then
    echo "以上关键文件变更已记录，请确认是否继续。"
  fi
}

# =============================================================================
# 主流程
# =============================================================================

# 0. 跳过纯只读操作（加速检查）
if is_readonly; then
  exit 0
fi

# 1. SQL 整表删除防护（硬阻止，不可绕过）
check_sql_danger

# 2. 命令白名单检查
check_command_whitelist

# 3. 打包部署前安全检查（有风险时阻断，确认后放行）
check_build_security

# 4. CI/CD 部署安全确认（生产环境额外检查）
check_cicd_deploy() {
  local CI_DEPLOY="$ROOT/.codebuddy/hooks/ci-deploy.sh"
  if [ -f "$CI_DEPLOY" ]; then
    echo "$INPUT" | bash "$CI_DEPLOY" || exit $?
  fi
}
check_cicd_deploy

# 5. 删除操作确认
check_delete_operations

# 6. Git 危险操作确认
check_git_dangerous

# 7. 关键文件保护预警
check_critical_files

# 全部通过
exit 0
