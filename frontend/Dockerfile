# 前端 Dockerfile - 多阶段构建，依赖层与源码层分离
# 仅使用 Docker 原生分层缓存，禁止 # syntax=docker/dockerfile:* 语法

ARG DOCKER_REGISTRY=
FROM ${DOCKER_REGISTRY}node:20-alpine AS builder

WORKDIR /app

# 配置京东 npm 国内镜像源（无需 VPN）
RUN npm config set registry https://registry.m.jd.com/

# ---- 依赖层（仅 package.json / package-lock.json 变化才重新安装依赖）----
COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund || npm install --no-audit --no-fund

# ---- 源码层（仅业务源码变化才重新构建，不触发依赖安装）----
COPY . .
RUN npm run build

FROM ${DOCKER_REGISTRY}nginx:alpine

COPY --from=builder /app/dist /usr/share/nginx/html

COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
