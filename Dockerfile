# 多阶段构建：先用 Node 编译前端，再把 dist 放进非 root nginx 镜像。
FROM node:22-alpine AS build

WORKDIR /app

# 先复制依赖清单，利用 Docker 缓存加快重复构建。
COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# 运行阶段只保留 nginx 与静态产物，避免把源码和 node_modules 带到生产镜像。
FROM nginxinc/nginx-unprivileged:stable-alpine AS runtime

COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080

# 健康检查只验证 nginx 能正常返回首页，反代接口由部署验证单独检查。
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q -O - http://127.0.0.1:8080/ > /dev/null || exit 1
