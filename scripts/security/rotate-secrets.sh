#!/usr/bin/env bash
# =============================================================================
# 凭证轮换工具 —— 一键生成全部强随机凭证并更新 .env
# =============================================================================
# 适用场景：
#   1. 凭证疑似泄露（如误提交到代码仓库、日志外泄）时紧急轮换
#   2. 定期安全巡检轮换
#   3. 新环境初始化
#
# 用法：
#   bash scripts/security/rotate-secrets.sh
#
# 执行后会：
#   1. 备份现有 .env（如存在）为 .env.bak.<时间戳>
#   2. 生成全部强随机凭证并写入 .env
#   3. 生成 OAuth2 client_secret 的 BCrypt 哈希（供数据库更新使用）
#   4. 输出后续需要人工执行的步骤（数据库更新、Nacos 改密等）
#
# 安全说明：
#   - 随机值只在本次运行的 .env 与终端输出中出现，不会被写入命令历史
#   - 请务必在生成后立即妥善保存 .env，并确认其已被 .gitignore 忽略
# =============================================================================

set -euo pipefail

ENV_FILE=".env"
BACKUP_FILE=".env.bak.$(date +%Y%m%d%H%M%S)"

echo "=============================================================="
echo " auth-platform 凭证轮换工具"
echo "=============================================================="
echo ""

# ---------- 随机值生成 ----------
# 优先使用 openssl，回退到 /dev/urandom
gen_random() {
    local len="${1:-32}"
    if command -v openssl >/dev/null 2>&1; then
        # 过滤掉 shell/正则/XML 中的特殊字符，避免在配置与 SQL 中引发转义问题
        openssl rand -base64 $((len * 2)) | tr -d '\n' | tr -d '+/=' | tr -d '"'"'"'\`$&|<>*?(){}[]' | cut -c1-"${len}"
    else
        LC_ALL=C tr -dc 'A-Za-z0-9!@#%^&*_+-' </dev/urandom 2>/dev/null | head -c "${len}"
    fi
}

echo "== 步骤 1/4：备份现有 .env =="
if [ -f "$ENV_FILE" ]; then
    cp "$ENV_FILE" "$BACKUP_FILE"
    echo "✅ 已备份: $BACKUP_FILE"
else
    echo "ℹ️  未发现现有 .env，将创建新文件"
fi
echo ""

echo "== 步骤 2/4：生成强随机凭证 =="
MYSQL_ROOT_PASSWORD=$(gen_random 24)
REDIS_PASSWORD=$(gen_random 24)
NACOS_PASSWORD=$(gen_random 20)
NACOS_AUTH_TOKEN=$(gen_random 48)
NACOS_AUTH_IDENTITY_VALUE=$(gen_random 20)
RABBITMQ_USER="authmq"
RABBITMQ_PASS=$(gen_random 24)
PORTAL_CLIENT_SECRET=$(gen_random 32)
echo "✅ 已生成 8 项凭证"
echo ""

# ---------- 生成 BCrypt 哈希 ----------
# 数据库中 client_secret 建议以 {bcrypt} 哈希存储（CryptoManager 支持该格式），
# 这样即使数据库泄露也无法还原出真实密钥。
echo "== 步骤 3/4：生成 client_secret 的 BCrypt 哈希 =="
BCRYPT_HASH=""

# 优先使用项目自带的 BCryptUtil（位于 common-core），无需外部依赖。
# 通过标准输入传递明文，避免密钥出现在 shell 命令历史与进程列表（ps）中。
if command -v java >/dev/null 2>&1 && [ -f common-core/target/classes/com/liang/xz/common/core/util/BCryptUtil.class ]; then
    SEC_CRYPTO_JAR=$(find "$HOME/.m2/repository/org/springframework/security/spring-security-crypto" \
        -name "spring-security-crypto-*.jar" ! -name "*sources*" ! -name "*javadoc*" 2>/dev/null | head -1)
    if [ -n "$SEC_CRYPTO_JAR" ]; then
        BCRYPT_HASH=$(printf '%s' "$PORTAL_CLIENT_SECRET" \
            | java -cp "common-core/target/classes:$SEC_CRYPTO_JAR" \
                com.liang.xz.common.core.util.BCryptUtil - 2>/dev/null)
    fi
fi

if [ -n "$BCRYPT_HASH" ]; then
    echo "✅ 已生成 BCrypt 哈希"
else
    echo "⚠️  未能自动生成 BCrypt 哈希。"
    echo "   请先编译 common-core（mvn -pl common-core compile），或在服务启动后执行："
    echo "   echo -n \"\$PORTAL_CLIENT_SECRET\" | java -cp <classpath> \\"
    echo "       com.liang.xz.common.core.util.BCryptUtil -"
fi
echo ""

# ---------- 写入 .env ----------
cat > "$ENV_FILE" <<EOF
# =============================================================================
# auth-platform 环境变量 —— 由 rotate-secrets.sh 于 $(date '+%Y-%m-%d %H:%M:%S') 生成
# 本文件含真实凭证，已被 .gitignore 忽略，严禁提交到代码仓库
# =============================================================================

# ---------- 镜像与版本 ----------
IMAGE_REGISTRY=auth-platform
APP_VERSION=1.0.0

# ---------- 服务端口（dev 对外暴露用） ----------
# 若这些端口已被本机其它服务占用，请自行修改后再启动
# 排查命令（Windows）：netstat -ano | findstr "LISTENING" | findstr ":80 :3306 :8080 "
# 排查命令（Linux）  ：ss -tlnp | grep -E ':(80|3306|8080|8848)\s'
GATEWAY_PORT=8080
# 默认 3307：本机常已安装 MySQL 占用 3306
MYSQL_PORT=3307
REDIS_PORT=6379
NACOS_PORT=8848
RABBITMQ_MGMT_PORT=15672
# 前端访问端口（默认 8082：80 端口常被其它 Web 服务占用）
WEB_PORT=8082
APP_PORT=8081

# ---------- 基础设施地址 ----------
NACOS_ADDR=nacos:8848

# ---------- MySQL ----------
MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}

# ---------- Redis ----------
REDIS_PASSWORD=${REDIS_PASSWORD}

# ---------- Nacos ----------
NACOS_USERNAME=nacos
NACOS_PASSWORD=${NACOS_PASSWORD}
NACOS_AUTH_IDENTITY_KEY=serverIdentity
NACOS_AUTH_IDENTITY_VALUE=${NACOS_AUTH_IDENTITY_VALUE}
NACOS_AUTH_TOKEN=${NACOS_AUTH_TOKEN}

# ---------- RabbitMQ ----------
RABBITMQ_USER=${RABBITMQ_USER}
RABBITMQ_PASS=${RABBITMQ_PASS}

# ---------- OAuth2 门户客户端 ----------
PORTAL_CLIENT_ID=admin-web
PORTAL_CLIENT_SECRET=${PORTAL_CLIENT_SECRET}
PORTAL_TOKEN_ENDPOINT=http://gateway:8080/auth-server/oauth2/token

# ---------- API 文档开关 ----------
# 生产环境必须设为 false
API_DOCS_ENABLED=true
EOF

chmod 600 "$ENV_FILE"
echo "✅ 已写入 $ENV_FILE（权限 600）"
echo ""

# ---------- 输出后续步骤 ----------
echo "=============================================================="
echo " 步骤 4/4：请继续执行以下人工操作"
echo "=============================================================="
echo ""
echo "① 更新数据库 OAuth2 客户端密钥（admin-web）"
echo "   登录 MySQL 执行："
if [ -n "$BCRYPT_HASH" ]; then
    echo "   UPDATE oauth2_registered_client"
    echo "      SET client_secret = '{bcrypt}${BCRYPT_HASH}'"
    echo "    WHERE client_id = 'admin-web';"
    echo ""
    echo "   说明：{bcrypt} 前缀由 CryptoManager 识别，直接存哈希可防止数据库泄露导致密钥还原。"
else
    echo "   UPDATE oauth2_registered_client"
    echo "      SET client_secret = '{bcrypt}<生成的BCrypt哈希>'"
    echo "    WHERE client_id = 'admin-web';"
    echo ""
    echo "   其中明文为 .env 中的 PORTAL_CLIENT_SECRET（请勿泄露）。"
fi
echo ""
echo "② 修改 Nacos 控制台密码"
echo "   登录 Nacos → 权限控制 → 用户管理 → 修改 nacos 用户密码"
echo "   新密码见 .env 的 NACOS_PASSWORD"
echo ""
echo "③ 重启全部服务使新凭证生效"
echo "   docker compose -f docker-compose.dev.yml up -d --force-recreate"
echo "   （生产环境请改用 docker-compose.prod.yml）"
echo ""
echo "④ 清理 Git 历史中的旧凭证（如曾提交过）"
echo "   参见 scripts/security/README-CREDENTIAL-ROTATION.md"
echo ""
echo "⚠️  重要：请立即妥善备份 .env，文件丢失将导致服务无法连接基础设施。"
echo ""
