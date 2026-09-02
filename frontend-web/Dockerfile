# =============================================================================
# 管理端 Web 前端 —— 多阶段构建（Vite 构建 + Nginx 托管）
# =============================================================================
# 用法（在 frontend-web 目录执行）：
#   docker build -t registry.example.com/auth-platform/frontend-web:v1.0.0 .
#
# 说明：
#   - 前端为纯静态 SPA，所有 API 请求由容器内 Nginx 反向代理到网关，
#     因此构建期无需注入网关地址（VITE_GATEWAY_URL 仅用于本地 Vite dev proxy）
#   - 禁止在构建参数中传入任何密钥：VITE_ 变量会被打进 JS 产物，等同公开
# =============================================================================

# ==================== 阶段 1：依赖安装与构建 ====================
FROM node:20-alpine AS builder

WORKDIR /app

# 先只复制依赖描述文件，最大化利用 Docker 缓存层
COPY package.json ./
COPY package-lock.json* ./

# 有 lock 文件则用 npm ci（严格依据 lock，构建可复现）；
# 否则降级为 npm install，避免因缺少 lock 而直接构建失败
RUN if [ -f package-lock.json ]; then \
        npm ci --no-audit --no-fund; \
    else \
        echo "WARN: package-lock.json 不存在，降级使用 npm install（构建不可复现，建议补充 lock 文件）"; \
        npm install --no-audit --no-fund; \
    fi

COPY . .

# 构建静态产物到 dist/
RUN npm run build

# ==================== 阶段 2：Nginx 托管 ====================
FROM nginx:1.25-alpine

LABEL maintainer="auth-platform"
LABEL description="Auth Platform Web Console"

RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && rm -rf /var/cache/apk/*

# 移除默认配置，使用项目自定义配置
RUN rm -f /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# Nginx 以非 root 用户运行需放开目录权限（alpine 镜像默认含 nginx 用户）
RUN chown -R nginx:nginx /usr/share/nginx/html \
    && chown -R nginx:nginx /var/cache/nginx \
    && touch /var/run/nginx.pid \
    && chown nginx:nginx /var/run/nginx.pid

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=20s \
    CMD wget -qO- "http://127.0.0.1/healthz" || exit 1

# 以非 root 用户运行
USER nginx

STOPSIGNAL SIGQUIT

CMD ["nginx", "-g", "daemon off;"]
