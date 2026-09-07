#!/bin/sh
# =============================================================================
# 源码敏感信息扫描 —— 拦截硬编码凭证，防止密码/密钥随代码入库
# =============================================================================
# 用途：在 CI 流水线或本地扫描配置与源码，检测以明文写死的密码、密钥、Token。
#      命中即返回非零退出码，阻断流水线。
#
# 用法：
#   sh scripts/security/secret-scan.sh
#
# 扫描范围（使用 find 而非 git ls-files，以便覆盖尚未纳入版本控制的新增文件）：
#   *.yml / *.yaml / *.properties / *.xml / *.java / .env*
#   —— 前端 .env 中的 VITE_ 变量会被打进 JS 产物，若含真实密钥等同公开，必须纳入扫描。
#
# 已排除的误报场景：
#   - Maven 资源过滤占位符   @nacos.password@
#   - Spring 环境变量占位符  ${NACOS_PASSWORD:xxx}
#   - 文档与模板占位值       请替换 / your- / changeit / xxx / example / .env.example
# =============================================================================

set -u

echo "== 源码敏感信息扫描 =="

# 敏感字段名关键字
KEYWORDS='password|passwd|pwd|secret|api[-_]?key|apikey|access[-_]?key|token|private[-_]?key'

# 明文值特征：= 或 : 之后紧跟至少 6 位可见字符（引号内亦可）
# 说明：不在此处排除 ${}，交由下面的 EXCLUDE 统一按"占位符"规则过滤，逻辑更集中
PATTERN="(${KEYWORDS})[[:space:]]*[:=][[:space:]]*[\"']?[A-Za-z0-9!@#%^&*_+\\-./]{6,}"

# 误报排除：占位符、示例值、模板文件
EXCLUDE='(\.env\.example|请替换|your-|changeit|xxxx|example|example\.com|placeholder|dummy|<[^>]*>|@[a-z]+\.[a-zA-Z-]+@|\$\{)'

FOUND=0

FILES=$(find . -type f \
    \( -name '*.yml' -o -name '*.yaml' -o -name '*.properties' \
       -o -name '*.xml' -o -name '*.java' -o -name '.env*' \) \
    -not -path './*/target/*' \
    -not -path './node_modules/*' \
    -not -path './logs/*' \
    -not -path './.git/*' \
    -not -path './.m2/*' \
    2>/dev/null)

for FILE in $FILES; do
    [ -f "$FILE" ] || continue
    # -i 大小写不敏感：变量可能命名为 SECRET / Secret / secret 等任意形式
    MATCHES=$(grep -niE "$PATTERN" "$FILE" 2>/dev/null | grep -vE "$EXCLUDE")
    if [ -n "$MATCHES" ]; then
        echo "❌ 疑似硬编码凭证: $FILE"
        echo "$MATCHES" | sed 's/^/    /'
        FOUND=1
    fi
done

if [ "$FOUND" -ne 0 ]; then
    echo ""
    echo "检测到硬编码凭证。请改为环境变量注入："
    echo "  1. 在 .env.example 中登记变量名"
    echo "  2. 配置文件中写成 \${VAR_NAME:默认值} 形式"
    echo "  3. 真实值通过 .env（已 gitignore）或 CI/CD Secret 注入"
    echo ""
    echo "若确认为误报，请在本脚本的 EXCLUDE 规则中补充排除项。"
    exit 1
fi

echo "✅ 未发现硬编码凭证"
exit 0
