# 多阶段构建：先用 Node 编译前端，再把 dist 放进非 root nginx 镜像。
# 固定 CI 构建使用的 Node 补丁；生产运行阶段仍只包含 nginx 和静态资源。
FROM node:22.23.4-alpine@sha256:fe4f6d74e20370acb04ad889077d14d0f64a9c8ea6a42bc60cddf1e844699914 AS build

WORKDIR /app

# 先复制依赖清单，利用 Docker 缓存加快重复构建。
COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# 运行阶段只保留 nginx 与静态产物，避免把源码和 node_modules 带到生产镜像。
# 固定 nginx 安全补丁和镜像摘要，避免 stable 标签漂移或复用旧基础镜像。
FROM nginxinc/nginx-unprivileged:1.30.6-alpine@sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03 AS runtime

COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080

# 健康检查只验证 nginx 能正常返回首页，反代接口由部署验证单独检查。
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q -O - http://127.0.0.1:8080/ > /dev/null || exit 1
