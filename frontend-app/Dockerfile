# =============================================================================
# 移动端前端 —— 多阶段构建（UniApp H5 构建 + Nginx 托管）
# =============================================================================
# 用法（在 frontend-app 目录执行）：
#   docker build -t registry.example.com/auth-platform/frontend-app:v1.0.0 .
#
# 说明：
#   仅构建 H5 版本（build:h5）。微信小程序与 App 有各自的发布渠道，
#   不通过容器部署，如需构建请在本机执行 npm run build:mp-weixin / build:app。
# =============================================================================

# ==================== 阶段 1：依赖安装与构建 ====================
FROM node:20-alpine AS builder

WORKDIR /app

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

# 构建 H5 产物，输出至 dist/build/h5
RUN npm run build:h5

# ==================== 阶段 2：Nginx 托管 ====================
FROM nginx:1.25-alpine

LABEL maintainer="auth-platform"
LABEL description="Auth Platform Mobile H5"

RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && rm -rf /var/cache/apk/*

RUN rm -f /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf

# UniApp H5 的默认输出目录为 dist/build/h5
COPY --from=builder /app/dist/build/h5 /usr/share/nginx/html

RUN chown -R nginx:nginx /usr/share/nginx/html \
    && chown -R nginx:nginx /var/cache/nginx \
    && touch /var/run/nginx.pid \
    && chown nginx:nginx /var/run/nginx.pid

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=20s \
    CMD wget -qO- "http://127.0.0.1/healthz" || exit 1

USER nginx

STOPSIGNAL SIGQUIT

CMD ["nginx", "-g", "daemon off;"]
