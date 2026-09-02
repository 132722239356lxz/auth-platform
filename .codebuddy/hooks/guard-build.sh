#!/usr/bin/env bash
# =============================================================================
# CodeBuddy 打包部署前安全检查钩子
# =============================================================================
# 触发条件: mvn package/deploy/install, docker build, npm run build, gradle build
# 检查项:
#   1. 硬编码密码/密钥/Token/API Key (扫描 application*.yml, *.properties, nacos配置)
#   2. 敏感文件检测 (.env, .pem, id_rsa, .jks, .p12 等)
#   3. CORS 通配符配置
#   4. 生产环境日志级别
#   5. Actuator 端点暴露
#   6. 已知高危依赖版本
#   7. 代码安全模式 (SQL注入, 命令执行, 异常处理等)
# =============================================================================
set -uo pipefail

ROOT="${CODEBUDDY_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null)}"

if [ -z "$ROOT" ]; then
  echo "[SEC-CHECK] 无法定位项目根目录，跳过安全检查。"
  exit 0
fi
cd "$ROOT" 2>/dev/null || exit 0

# =============================================================================
# 计数器
# =============================================================================
CRITICAL=0
HIGH=0
MEDIUM=0
LOW=0

# =============================================================================
# 输出函数
# =============================================================================
c_red()    { CRITICAL=$((CRITICAL + 1)); printf '  [严重] %s\n' "$1"; printf '         文件: %s\n' "$2"; printf '         内容: %s\n' "$3"; }
c_high()   { HIGH=$((HIGH + 1));       printf '  [高危] %s\n' "$1"; printf '         文件: %s\n' "$2"; }
c_medium() { MEDIUM=$((MEDIUM + 1));   printf '  [中危] %s\n' "$1"; printf '         文件: %s\n' "$2"; }
c_low()    { LOW=$((LOW + 1));         printf '  [低危] %s\n' "$1"; }

print_header() {
  printf '\n'
  printf '  ================================================\n'
  printf '    代码安全审查报告 (部署前检查)\n'
  printf '  ================================================\n'
  printf '  检查时间: %s\n' "$(date '+%Y-%m-%d %H:%M:%S')"
  printf '  项目路径: %s\n' "${ROOT:-未知}"
  printf '\n'
}

print_summary() {
  local TOTAL=$((CRITICAL + HIGH + MEDIUM + LOW))
  printf '\n'
  printf '  ================================================\n'
  printf '    安全审查汇总\n'
  printf '  ------------------------------------------------\n'
  printf '    严重: %-3d  高危: %-3d  中危: %-3d  低危: %-3d\n' "$CRITICAL" "$HIGH" "$MEDIUM" "$LOW"
  printf '    总计: %d 个问题\n' "$TOTAL"
  printf '  ================================================\n'
  printf '\n'
}

section_header() {
  printf '\n  --- %s ---\n' "$1"
}

# 辅助: 检查某行是否包含变量引用 (如 ${VAR}) — 是则跳过
is_placeholder() {
  echo "$1" | grep -qF '${'
}

# 辅助: 获取文件中匹配行的内容 (取值部分)
get_match_value() {
  local file="$1" pattern="$2"
  grep -nE "$pattern" "$file" 2>/dev/null | grep -vF '${' | head -5
}

# =============================================================================
# 1. 硬编码凭据扫描
# =============================================================================
check_hardcoded_credentials() {
  section_header "检查项 1/7: 硬编码凭据扫描"
  local FOUND=0

  # 收集所有配置文件
  local CONFIG_FILES
  CONFIG_FILES=$(find "$ROOT" -type f \
    \( -name "application*.yml" -o -name "application*.yaml" -o -name "bootstrap*.yml" -o -name "bootstrap*.yaml" -o -name "*.properties" \) \
    -not -path "*/node_modules/*" -not -path "*/target/*" -not -path "*/.git/*" 2>/dev/null)

  # 也加入 Nacos 配置目录
  if [ -d "$ROOT/nacos-config-import" ]; then
    local NACOS_FILES
    NACOS_FILES=$(find "$ROOT/nacos-config-import" -name "*.yml" -o -name "*.yaml" 2>/dev/null)
    CONFIG_FILES="$CONFIG_FILES $NACOS_FILES"
  fi

  for FILE in $CONFIG_FILES; do
    [ -f "$FILE" ] || continue
    local REL="${FILE#$ROOT/}"

    # ---- 数据库/Redis/RabbitMQ/Mail 密码 ----
    while IFS= read -r line; do
      [ -z "$line" ] && continue
      local LN=$(echo "$line" | cut -d: -f1)
      local VAL=$(echo "$line" | cut -d: -f2- | sed 's/^[[:space:]]*//')

      # 跳过变量引用和明显占位符
      is_placeholder "$VAL" && continue
      echo "$VAL" | grep -qiE "your[-_]|changeit|changeme|example|test|placeholder|yourpassword|123456|password\s*:\s*$" && continue
      # 跳过仅包含密码关键词但没有实际值的行
      echo "$VAL" | grep -qE "password:\s*$" && continue

      c_red "硬编码密码" "$REL:$LN" "${VAL:0:80}"
      FOUND=1
    done < <(grep -nE "password:\s*['\"][^'\"]+['\"]" "$FILE" 2>/dev/null | grep -vF '${')

    # ---- API Key / Secret Key / Token (长度>=20的字符串) ----
    while IFS= read -r line; do
      [ -z "$line" ] && continue
      local LN=$(echo "$line" | cut -d: -f1)
      local VAL=$(echo "$line" | cut -d: -f2- | sed 's/^[[:space:]]*//')
      is_placeholder "$VAL" && continue

      c_red "硬编码 API Key/Token" "$REL:$LN" "${VAL:0:80}..."
      FOUND=1
    done < <(grep -nEi "(api[-_]key|secret[-_]key|access[-_]key|private[-_]key|token|auth[-_]token):\s*['\"][A-Za-z0-9_=\.\-]{20,}" "$FILE" 2>/dev/null | grep -vF '${')

    # ---- RSA 私钥 ----
    while IFS= read -r line; do
      [ -z "$line" ] && continue
      local LN=$(echo "$line" | cut -d: -f1)
      c_red "RSA私钥明文存储" "$REL:$LN" "(私钥以 MII/-----BEGIN 开头)"
      FOUND=1
    done < <(grep -nE "(rsa[-_]private[-_]key|private[-_]key):\s*['\"]?(MII|-----BEGIN)" "$FILE" 2>/dev/null)

    # ---- HS256/JWT 签名密钥 ----
    while IFS= read -r line; do
      [ -z "$line" ] && continue
      local LN=$(echo "$line" | cut -d: -f1)
      local VAL=$(echo "$line" | cut -d: -f2- | sed 's/^[[:space:]]*//' | cut -c1-60)
      is_placeholder "$VAL" && continue
      c_red "JWT/HMAC签名密钥明文存储" "$REL:$LN" "$VAL..."
      FOUND=1
    done < <(grep -nEi "(hs256[-_]secret[-_]key|hmac[-_]key|signing[-_]key|jwt[-_]secret):\s*['\"]" "$FILE" 2>/dev/null | grep -vF '${')

    # ---- AES 密钥 ----
    while IFS= read -r line; do
      [ -z "$line" ] && continue
      local LN=$(echo "$line" | cut -d: -f1)
      c_red "AES加密密钥明文存储" "$REL:$LN" "(建议使用环境变量或密钥管理服务)"
      FOUND=1
    done < <(grep -nEi "(aes[-_]key|encrypt[-_]key|master[-_]key):\s*['\"]" "$FILE" 2>/dev/null | grep -vF '${')
  done

  if [ "$FOUND" -eq 0 ]; then
    printf '  (通过) 未发现硬编码凭据\n'
  fi
}

# =============================================================================
# 2. 敏感文件检查
# =============================================================================
check_sensitive_files() {
  section_header "检查项 2/7: 敏感文件泄露检查"
  local FOUND=0

  # 密钥/证书文件
  for PATTERN in '*.pem' '*id_rsa*' '*id_ed25519*' '*id_ecdsa*' '*.keytab' '*.jks' '*.keystore' '*.p12' '*.pfx'; do
    local FILES
    FILES=$(find "$ROOT" -maxdepth 6 -name "$PATTERN" -not -path "*/node_modules/*" -not -path "*/target/*" -not -path "*/.git/*" 2>/dev/null)
    for FILE in $FILES; do
      [ -f "$FILE" ] || continue
      local REL="${FILE#$ROOT/}"
      c_high "发现密钥/证书文件" "$REL"
      FOUND=1
    done
  done

  # .env 文件 (排除 .env.example)
  local ENV_FILES
  ENV_FILES=$(find "$ROOT" -maxdepth 3 -name ".env" -not -name ".env.example" -not -path "*/node_modules/*" 2>/dev/null)
  for FILE in $ENV_FILES; do
    [ -f "$FILE" ] || continue
    local REL="${FILE#$ROOT/}"
    c_high "发现 .env 环境变量文件" "$REL"
    FOUND=1
  done

  if [ "$FOUND" -eq 0 ]; then
    printf '  (通过) 未发现敏感文件泄露\n'
  fi
}

# =============================================================================
# 3. CORS 配置检查
# =============================================================================
check_cors_config() {
  section_header "检查项 3/7: CORS 跨域配置检查"
  local FOUND=0

  # 检查 YAML/Properties 中的 CORS 通配符
  for FILE in $(grep -rl "allowedOrigin\|allowed-origin\|allowOrigin\|allow-origin\|AllowedOrigins\|allowedOrigins\|CorsConfiguration" "$ROOT" \
    --include="*.yml" --include="*.yaml" --include="*.java" --include="*.properties" 2>/dev/null | grep -v node_modules | grep -v target); do
    [ -f "$FILE" ] || continue
    local REL="${FILE#$ROOT/}"

    # 查找 YAML 中的 allowedOrigins: "*"
    while IFS= read -r line; do
      [ -z "$line" ] && continue
      local LN=$(echo "$line" | cut -d: -f1)
      c_high "CORS 配置使用通配符" "$REL:$LN"
      FOUND=1
    done < <(grep -nE "(allowedOrigin|allowed-origin|allowOrigin|allow-origin|AllowedOrigins|allowedOrigins).*\*" "$FILE" 2>/dev/null)

    # 查找 Java 中的 addAllowedOrigin("*")
    while IFS= read -r line; do
      [ -z "$line" ] && continue
      local LN=$(echo "$line" | cut -d: -f1)
      c_high "CORS Java配置使用通配符" "$REL:$LN"
      FOUND=1
    done < <(grep -nE 'allowedOrigins.*\*|addAllowedOrigin.*\*|allowedOriginPattern.*\*' "$FILE" 2>/dev/null)
  done

  if [ "$FOUND" -eq 0 ]; then
    printf '  (通过) CORS 配置正常\n'
  fi
}

# =============================================================================
# 4. 生产环境日志级别检查
# =============================================================================
check_log_level() {
  section_header "检查项 4/7: 生产环境日志级别检查"
  local FOUND=0

  for FILE in $(find "$ROOT" -name "application-prod*" -o -name "application-production*" 2>/dev/null | grep -v node_modules | grep -v target); do
    [ -f "$FILE" ] || continue
    local REL="${FILE#$ROOT/}"

    while IFS= read -r line; do
      [ -z "$line" ] && continue
      local LN=$(echo "$line" | cut -d: -f1)
      c_high "生产环境日志级别包含 DEBUG/TRACE" "$REL:$LN"
      FOUND=1
    done < <(grep -niE "level:\s*(debug|trace)" "$FILE" 2>/dev/null)

    while IFS= read -r line; do
      [ -z "$line" ] && continue
      local LN=$(echo "$line" | cut -d: -f1)
      c_high "生产环境 ROOT 日志级别异常" "$REL:$LN"
      FOUND=1
    done < <(grep -niE "root:\s*DEBUG|root:\s*TRACE" "$FILE" 2>/dev/null)
  done

  if [ "$FOUND" -eq 0 ]; then
    printf '  (通过) 生产环境日志级别正常\n'
  fi
}

# =============================================================================
# 5. Actuator 端点暴露检查
# =============================================================================
check_actuator_exposure() {
  section_header "检查项 5/7: Actuator 端点暴露检查"
  local FOUND=0

  for FILE in $(find "$ROOT" \( -name "application*.yml" -o -name "application*.yaml" -o -name "*.properties" \) \
    -not -path "*/node_modules/*" -not -path "*/target/*" 2>/dev/null); do
    [ -f "$FILE" ] || continue

    # 跳过 dev 环境
    echo "$FILE" | grep -q "dev" && continue
    local REL="${FILE#$ROOT/}"

    # 检查是否暴露所有端点
    while IFS= read -r line; do
      [ -z "$line" ] && continue
      local LN=$(echo "$line" | cut -d: -f1)
      # 跳过注释行
      echo "$line" | grep -q '^[[:space:]]*#' && continue
      c_high "Actuator 暴露所有端点" "$REL:$LN"
      FOUND=1
    done < <(grep -nE "include:\s*'?\*'?" "$FILE" 2>/dev/null)

    # 检查敏感端点
    while IFS= read -r line; do
      [ -z "$line" ] && continue
      local LN=$(echo "$line" | cut -d: -f1)
      echo "$line" | grep -q '^[[:space:]]*#' && continue
      c_medium "Actuator 暴露敏感端点" "$REL:$LN"
      FOUND=1
    done < <(grep -nE "include:.*\b(env|configprops|heapdump|threaddump|mappings|beans|conditions)\b" "$FILE" 2>/dev/null)
  done

  if [ "$FOUND" -eq 0 ]; then
    printf '  (通过) Actuator 端点配置正常\n'
  fi
}

# =============================================================================
# 6. 依赖安全版本检查
# =============================================================================
check_dependency_security() {
  section_header "检查项 6/7: 依赖安全版本检查"
  local FOUND=0

  for POM in $(find "$ROOT" -maxdepth 4 -name "pom.xml" -not -path "*/target/*" 2>/dev/null); do
    [ -f "$POM" ] || continue
    local REL="${POM#$ROOT/}"

    # Log4j 1.x / 2.x < 2.17 (Log4Shell)
    if grep -qE 'log4j.*1\.[0-9]|log4j.*2\.(0|1[0-6])\b' "$POM" 2>/dev/null; then
      c_red "Log4j 存在 Log4Shell 漏洞" "$REL"
      FOUND=1
    fi

    # Fastjson < 1.2.83
    if grep -qE 'fastjson.*1\.2\.[0-7][0-9]' "$POM" 2>/dev/null; then
      c_high "Fastjson 版本存在反序列化漏洞" "$REL"
      FOUND=1
    fi

    # SnakeYAML < 2.0
    if grep -qE 'snakeyaml.*1\.[0-9]' "$POM" 2>/dev/null; then
      c_medium "SnakeYAML 1.x 存在反序列化漏洞" "$REL"
      FOUND=1
    fi

    # XStream (已知RCE)
    if grep -q 'xstream' "$POM" 2>/dev/null; then
      c_medium "XStream 已弃用，建议迁移" "$REL"
      FOUND=1
    fi
  done

  if [ "$FOUND" -eq 0 ]; then
    printf '  (通过) 未发现已知高危依赖\n'
  fi
}

# =============================================================================
# 7. 代码安全模式扫描
# =============================================================================
check_code_security() {
  section_header "检查项 7/7: 代码安全模式扫描"
  local FOUND=0

  # SQL 字符串拼接
  for FILE in $(grep -rl 'String\.format.*SELECT\|String\.format.*INSERT\|String\.format.*UPDATE\|String\.format.*DELETE\|+.*"SELECT\|+.*"INSERT\|+.*"UPDATE\|+.*"DELETE' "$ROOT" --include="*.java" 2>/dev/null | grep -v target | head -10); do
    [ -f "$FILE" ] || continue
    local REL="${FILE#$ROOT/}"
    while IFS= read -r line; do
      [ -z "$line" ] && continue
      local LN=$(echo "$line" | cut -d: -f1)
      c_high "SQL 字符串拼接(潜在SQL注入)" "$REL:$LN"
      FOUND=1
    done < <(grep -nE 'String\.format.*(SELECT|INSERT|UPDATE|DELETE)' "$FILE" 2>/dev/null | head -5)
  done

  # Statement (非 PreparedStatement)
  for FILE in $(grep -rl 'createStatement()' "$ROOT" --include="*.java" 2>/dev/null | grep -v target | head -10); do
    [ -f "$FILE" ] || continue
    local REL="${FILE#$ROOT/}"
    while IFS= read -r line; do
      [ -z "$line" ] && continue
      local LN=$(echo "$line" | cut -d: -f1)
      c_high "Statement 非参数化查询" "$REL:$LN"
      FOUND=1
    done < <(grep -n 'createStatement()' "$FILE" 2>/dev/null | head -3)
  done

  # System.out.print 调试残留 (>10处)
  local SYSOUT_COUNT
  SYSOUT_COUNT=$(grep -r 'System\.out\.print' "$ROOT" --include="*.java" 2>/dev/null | grep -v target | wc -l)
  if [ "$SYSOUT_COUNT" -gt 10 ]; then
    c_low "存在 $SYSOUT_COUNT 处 System.out.print 调试代码"
    FOUND=1
  fi

  # printStackTrace 异常处理不当 (>5处)
  local PST_COUNT
  PST_COUNT=$(grep -r '\.printStackTrace()' "$ROOT" --include="*.java" 2>/dev/null | grep -v target | wc -l)
  if [ "$PST_COUNT" -gt 5 ]; then
    c_low "存在 $PST_COUNT 处 printStackTrace() 应使用 Logger"
    FOUND=1
  fi

  if [ "$FOUND" -eq 0 ]; then
    printf '  (通过) 代码安全扫描通过\n'
  fi
}

# =============================================================================
# 整改建议
# =============================================================================
print_remediation() {
  printf '\n'
  printf '  ================================================\n'
  printf '    整改建议\n'
  printf '  ================================================\n'

  if [ "$CRITICAL" -gt 0 ]; then
    printf '\n  [严重问题整改方案]\n'
    printf '  1. 硬编码凭据处理:\n'
    printf '     - 数据库密码: 使用 ${DB_PASSWORD} 环境变量引用\n'
    printf '     - API Key:    推荐通过 Nacos 配置中心管理，不提交 Git\n'
    printf '     - RSA 私钥:   使用环境变量或 jasypt 加密存储\n'
    printf '     - 已泄露凭据:  在对应平台 (智谱AI/QQ邮箱) 重置密钥\n'
    printf '  2. 敏感密钥文件: 加入 .gitignore，使用密钥管理服务\n'
  fi

  if [ "$HIGH" -gt 0 ]; then
    printf '\n  [高危问题整改方案]\n'
    printf '  1. CORS 通配符: 替换为具体的允许域名列表\n'
    printf '  2. 生产 DEBUG: 改为 INFO 或 WARN 级别\n'
    printf '  3. Actuator: 仅暴露 health,info 端点\n'
    printf '  4. SQL 拼接: 使用 MyBatis #{param} 参数化查询\n'
    printf '  5. 依赖漏洞: 升级到安全修复版本\n'
  fi

  if [ "$MEDIUM" -gt 0 ]; then
    printf '\n  [中危问题整改建议]\n'
    printf '  1. 命令行执行需确认输入过滤\n'
    printf '  2. 删除操作建议添加 @Transactional\n'
    printf '  3. 确认凭据文件是否需要提交\n'
  fi

  if [ "$LOW" -gt 0 ]; then
    printf '\n  [低危问题改进建议]\n'
    printf '  1. System.out.print 替换为 Logger 日志输出\n'
    printf '  2. printStackTrace() 替换为 logger.error(msg, e)\n'
  fi
}

# =============================================================================
# 主流程
# =============================================================================
main() {
  print_header

  check_hardcoded_credentials
  check_sensitive_files
  check_cors_config
  check_log_level
  check_actuator_exposure
  check_dependency_security
  check_code_security

  print_summary

  if [ "$CRITICAL" -gt 0 ] || [ "$HIGH" -gt 0 ]; then
    print_remediation

    printf '\n'
    printf '  ================================================\n'
    printf '  安全检查未通过! 禁止打包/部署!\n'
    printf '  ------------------------------------------------\n'
    printf '  严重: %d 个, 高危: %d 个\n' "$CRITICAL" "$HIGH"
    printf '  请先修复上述问题后重试。\n'
    printf '  ------------------------------------------------\n'
    printf '  如确认已知风险并接受，请在命令末尾添加:\n'
    printf '  # build-approved\n'
    printf '  或在回复中明确说明"接受风险 确认发布"。\n'
    printf '  ================================================\n'
    printf '\n'
    exit 2
  elif [ "$MEDIUM" -gt 0 ]; then
    print_remediation

    printf '\n'
    printf '  ================================================\n'
    printf '  存在 %d 个中危问题，建议修复后发布。\n' "$MEDIUM"
    printf '  回复"确认发布"继续，或先修复上述问题。\n'
    printf '  ================================================\n'
    printf '\n'
    exit 2
  else
    printf '\n'
    printf '  ================================================\n'
    printf '  安全检查全部通过! 可以继续发布。\n'
    printf '  回复"确认发布"以执行打包/部署操作。\n'
    printf '  ================================================\n'
    printf '\n'
    exit 2
  fi
}

main "$@"
