# 本地 Docker 测试流程

本文档记录在 Windows + WSL + Docker Desktop 环境中，本地构建并验证博客系统完整 Docker 部署的步骤和注意事项。

注意：本文档只用于开发机或本地 Docker 测试。2核2G生产服务器不应执行镜像构建，应使用 `demo_bk/docker-compose.prod.pull.yml` 直接拉取 GHCR 中已经构建好的镜像。

## 一、整体结构

本地完整构建测试使用 `demo_bk/docker-compose.prod.yml`，会启动四个服务：

```text
浏览器
  -> frontend 容器：nginx + 前端 dist
      -> /api 反代到 backend:5090
      -> /files 反代到 backend:5090

backend 容器：JRE + Spring Boot jar
  -> mysql 容器
  -> redis 容器
```

注意：

- 前端和 nginx 在 `frontend` 容器。
- Spring Boot 后端在 `backend` 容器。
- MySQL 和 Redis 是独立容器。
- 宿主机已有 MySQL 不影响本地完整部署，因为生产 Compose 默认不把 Docker MySQL 暴露到宿主机 `3306`。

## 二、首次准备

先确认 Docker Desktop 已开启 WSL Integration，然后在 WSL 中执行：

```bash
docker version
docker compose version
```

进入后端目录：

```bash
cd /mnt/e/codes/java_class_work/demo/demo_bk
```

如果已经迁移到单仓库，进入新仓库中的后端目录：

```bash
cd /mnt/e/codes/java_class_work/demo/Easy_blog_full/demo_bk
```

创建本地测试环境变量：

```bash
cp .env.prod.example .env.prod
```

本地测试建议 `.env.prod` 使用下面这些关键值：

```properties
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=5090
FRONTEND_HTTP_PORT=8081
BLOG_BACKEND_IMAGE=ghcr.io/lancasteerr/easy-blog-backend:prod
BLOG_FRONTEND_IMAGE=ghcr.io/lancasteerr/easy-blog-frontend:prod
BLOG_CORS_ALLOWED_ORIGINS=http://localhost:8081,http://127.0.0.1:8081
BLOG_JWT_SECRET=dev-local-secret-change-me-at-least-32-bytes

MYSQL_DATABASE=white_jotter
MYSQL_ROOT_PASSWORD=local-root-password
BLOG_DB_URL=jdbc:mysql://mysql:3306/white_jotter?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_general_ci&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&connectTimeout=3000&socketTimeout=10000&tcpKeepAlive=true&rewriteBatchedStatements=true
BLOG_DB_USERNAME=blog_user
BLOG_DB_PASSWORD=local-db-password
BLOG_DB_POOL_MAX_SIZE=6
BLOG_DB_POOL_MIN_IDLE=2
BLOG_DB_CONNECTION_TIMEOUT=5000
BLOG_DB_VALIDATION_TIMEOUT=3000
BLOG_DB_IDLE_TIMEOUT=600000
BLOG_DB_MAX_LIFETIME=1500000
BLOG_DB_KEEPALIVE_TIME=300000

BLOG_REDIS_HOST=redis
BLOG_REDIS_PORT=6379
BLOG_REDIS_DATABASE=0
BLOG_REDIS_PASSWORD=local-redis-password
BLOG_REDIS_TIMEOUT=3000ms
BLOG_REDIS_CONNECT_TIMEOUT=2000ms
BLOG_REDIS_POOL_MAX_ACTIVE=8
BLOG_REDIS_POOL_MAX_IDLE=4
BLOG_REDIS_POOL_MIN_IDLE=1
BLOG_REDIS_POOL_MAX_WAIT=1000ms

SERVER_TOMCAT_THREADS_MAX=50
SERVER_TOMCAT_THREADS_MIN_SPARE=5
SERVER_TOMCAT_MAX_CONNECTIONS=200
SERVER_TOMCAT_ACCEPT_COUNT=50
SERVER_TOMCAT_CONNECTION_TIMEOUT=5s
SERVER_TOMCAT_KEEP_ALIVE_TIMEOUT=15s
SERVER_TOMCAT_MAX_KEEP_ALIVE_REQUESTS=100

BLOG_STORAGE_ROOT=/app/blog-storage
BLOG_FILE_DOMAIN=http://localhost:8081/files
BLOG_UPLOAD_MAX_FILE_SIZE=5MB
BLOG_UPLOAD_MAX_REQUEST_SIZE=6MB
BLOG_SCHEDULING_ENABLED=true
```

## 三、数据库初始化

MySQL 容器会读取：

```text
demo_bk/sql/white_jotter.sql
```

Compose 中已挂载为：

```yml
./sql/white_jotter.sql:/docker-entrypoint-initdb.d/01-white_jotter.sql:ro
```

MySQL 官方镜像只会在数据库数据卷第一次创建、且 `/var/lib/mysql` 为空时执行 `/docker-entrypoint-initdb.d/` 下的初始化脚本。

因此：

- 第一次启动会自动建库建表。
- 如果 `mysql-data` 卷已经存在，修改 SQL 后不会自动重新执行。
- 如需重跑 SQL 初始化，要删除数据卷，见“停止与清理”。

## 四、上传文件保存位置

Docker 部署时，上传的封面和文章内图片不会保存到源码目录 `demo_bk/blog-storage`。后端容器把文件保存到容器内路径：

```text
/app/blog-storage
```

该路径由 Docker named volume 持久化。本地模拟生产如果使用 `-p easy-blog-prod`，对应卷名通常是：

```text
easy-blog-prod_blog-storage
```

查看已保存文件：

```bash
docker exec easy-blog-prod-backend-1 find /app/blog-storage -maxdepth 6 -type f
```

## 五、启动本地完整部署

首次启动并构建。该命令只建议在开发机执行：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

如果只想先验证后端镜像能不能构建：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml build backend
```

查看容器状态：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
```

查看日志：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f mysql
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f redis
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f backend
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f frontend
```

浏览器访问：

```text
http://localhost:8081/
http://localhost:8081/neko-panel/login
http://localhost:8081/api/public/get_article_list?page=1&size=5
```

## 六、连接 MySQL 控制台

使用 root 进入 MySQL：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml exec mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
```

使用业务用户进入 MySQL：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml exec mysql sh -lc 'mysql -u"$BLOG_DB_USERNAME" -p"$BLOG_DB_PASSWORD" "$MYSQL_DATABASE"'
```

常用 SQL：

```sql
SHOW TABLES;
SELECT id, user_name, role FROM user;
exit;
```

## 七、使用 Navicat 连接 Docker MySQL

默认生产 Compose 没有暴露 MySQL 端口，所以 Navicat 不能直接连接。

本地测试如需 Navicat，给 `docker-compose.prod.yml` 的 `mysql` 服务临时增加：

```yml
ports:
  - "127.0.0.1:3307:3306"
```

然后重建/启动 MySQL 服务：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d mysql
```

Navicat 连接信息：

```text
主机：127.0.0.1
端口：3307
用户名：root
密码：MYSQL_ROOT_PASSWORD 的值
数据库：white_jotter
```

也可以用业务用户：

```text
用户名：BLOG_DB_USERNAME 的值
密码：BLOG_DB_PASSWORD 的值
数据库：white_jotter
```

注意：服务器正式部署时不建议把 MySQL 暴露到公网。

## 八、插入后台账号

后端登录使用 BCrypt 校验密码，`user.password` 不能写明文密码。

如果你已经有 BCrypt 值，可以在 MySQL 控制台执行：

```sql
INSERT INTO `user` (`user_name`, `password`, `role`)
VALUES ('Rin', '你的bcrypt值', 'ROLE_ADMIN')
ON DUPLICATE KEY UPDATE
  `password` = VALUES(`password`),
  `role` = VALUES(`role`);
```

也可以在 WSL 中直接执行：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml exec -T mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' <<'SQL'
INSERT INTO `user` (`user_name`, `password`, `role`)
VALUES ('Rin', '你的bcrypt值', 'ROLE_ADMIN')
ON DUPLICATE KEY UPDATE
  `password` = VALUES(`password`),
  `role` = VALUES(`role`);
SQL
```

## 九、停止与清理

日常停止测试，保留数据库、Redis 和上传文件数据：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml down
```

下次恢复：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d
```

彻底清空测试数据并重新执行 SQL 初始化：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml down -v
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

区别：

- `down` 删除容器和网络，保留 volume。
- `down -v` 删除容器、网络和 volume，MySQL 数据会清空。
- Docker Desktop 里手动删除容器通常不会删除 volume，数据库数据大概率仍在。

## 十、常见问题

### 1. 后端镜像构建时 Maven 下载失败

现象类似：

```text
Could not transfer artifact ... from/to central
Remote host terminated the handshake
```

后端 Dockerfile 已使用：

```text
demo_bk/docker/maven-settings.xml
```

它会把 Maven 下载源切到阿里云镜像。修改后可先单独构建后端：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml build backend
```

### 2. 前端能打开，但接口 502

优先看后端日志：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml logs -f backend
```

常见原因：

- MySQL 没初始化成功。
- `.env.prod` 的数据库或 Redis 密码不一致。
- 后端容器启动失败。

### 3. 数据库表不存在

检查表：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml exec mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "SHOW TABLES;"'
```

如果没有表，通常是旧数据卷已经存在，初始化 SQL 没有重新执行。需要：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml down -v
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

### 4. 登录失败

检查：

- `user` 表是否有账号。
- `password` 是否为 BCrypt，而不是明文。
- `role` 是否为 `ROLE_ADMIN`。
- 连续失败过多可能触发登录失败限制，可以重启 Redis 清空本地测试缓存。

### 5. 端口冲突

如果 `8081` 被占用，修改 `.env.prod`：

```properties
FRONTEND_HTTP_PORT=8082
BLOG_CORS_ALLOWED_ORIGINS=http://localhost:8082,http://127.0.0.1:8082
BLOG_FILE_DOMAIN=http://localhost:8082/files
```

然后重新启动：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d
```

## 十一、正式服务器部署提醒

本地测试使用 `http://localhost:8081`。正式服务器应改成真实域名：

```properties
FRONTEND_HTTP_PORT=80
BLOG_CORS_ALLOWED_ORIGINS=https://你的域名
BLOG_FILE_DOMAIN=https://你的域名/files
```

生产服务器使用拉取版 Compose，不在服务器上构建镜像：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.pull.yml pull
docker compose --env-file .env.prod -f docker-compose.prod.pull.yml up -d
```

HTTPS 推荐放在外层 nginx、Caddy、云负载均衡或宝塔面板处理，容器内部仍保持 HTTP。
