
# Easy Blog Backend

[English](#english) | [简体中文](#简体中文)

---

## English

A blog system backend built with **Spring Boot**, providing user authentication, article management, pagination, and Redis-based caching.
Suitable for learning, personal projects, and further development.

### Features

- User registration and login
- Article CRUD operations
- Pagination support
- Redis caching
- Secure password hashing
- RESTful API design

### Tech Stack

- Java 17
- Spring Boot 3.x
- Spring Data JPA
- MySQL
- Redis
- Maven

### Project Structure

The backend is a modular monolith organized by business capability first, then by layer:

```text
src/main/java/com/febrie/demo_bk
├─ article/{web,application,domain,infrastructure}
├─ file/{web,application,infrastructure}
├─ identity/{web,application,infrastructure}
├─ audit/{application,infrastructure}
└─ shared/{config,error,pagination,infrastructure,web}
```

Business modules may only collaborate through application services and DTOs. Persistence, cache, security, and storage implementations remain inside each module's `infrastructure` package.
Article reads keep `ArticleQueryService` as the stable facade; detail, latest-list, rank, and DTO-hydration flows live in `article.application.query`, while Redis access is split into responsibility-specific stores.

### Prerequisites

- JDK 17+
- MySQL 8.x
- Redis 7.x+

### Configuration

Runtime secrets are provided by environment variables. Do not commit real `.env`, `.env.dev`, or `.env.prod` files.
The backend uses Spring profiles: `dev` for local development and `prod` for Docker deployment.
For local Maven or IDEA startup, copy `.env.dev.example` to `.env` so Spring Boot can import it automatically.

Generate a JWT secret:

```bash
node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"
```

Required variables:

```properties
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=5090
BLOG_BACKEND_IMAGE=ghcr.io/lancasteerr/easy-blog-backend:prod
BLOG_FRONTEND_IMAGE=ghcr.io/lancasteerr/easy-blog-frontend:prod
BLOG_CORS_ALLOWED_ORIGINS=https://your-frontend-domain.example
BLOG_JWT_SECRET=your_generated_base64_secret
BLOG_DB_URL=jdbc:mysql://mysql:3306/white_jotter?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_general_ci&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&connectTimeout=3000&socketTimeout=10000&tcpKeepAlive=true&rewriteBatchedStatements=true
BLOG_DB_USERNAME=blog_user
BLOG_DB_PASSWORD=your_db_password
BLOG_REDIS_HOST=redis
BLOG_REDIS_PORT=6379
BLOG_REDIS_DATABASE=0
BLOG_REDIS_PASSWORD=your_redis_password
BLOG_STORAGE_ROOT=/app/blog-storage
BLOG_FILE_DOMAIN=https://your-domain.example/files
BLOG_UPLOAD_MAX_FILE_SIZE=5MB
BLOG_UPLOAD_MAX_REQUEST_SIZE=6MB
```

### Run

Local development dependencies:

```bash
cp .env.dev.example .env.dev
docker compose --env-file .env.dev -f docker-compose.dev.yml up -d
```

Local Maven backend:

```bash
cp .env.dev .env
mvn spring-boot:run
```

Production Docker Compose pulls prebuilt GHCR images:

```bash
cp .env.prod.example .env.prod
docker compose --env-file .env.prod -f docker-compose.prod.pull.yml pull
docker compose --env-file .env.prod -f docker-compose.prod.pull.yml up -d
```

Package only:

```bash
mvn clean package
java -jar target/blog-backend.jar
```

### API Style

- RESTful APIs
- JSON responses
- Simplified pagination response (not exposing `Page` directly)

### Roadmap

-  Comment system
-  API documentation (Swagger / OpenAPI)
-  Search / tag function

### License

MIT License

------

## 简体中文

一个基于 **Spring Boot** 的博客系统后端项目，提供用户认证、文章管理、分页查询以及 Redis 缓存支持，适合学习、个人博客和二次开发。

### 功能特性

- 用户注册与登录
- 文章发布、修改、删除
- 文章分页查询
- Redis 缓存加速读取
- 安全的密码加密（Hash）
- RESTful API 设计

### 技术栈

- Java 17
- Spring Boot 3.x
- Spring Data JPA
- MySQL
- Redis
- Maven

### 项目结构

后端采用“业务模块优先、模块内部四层”的模块化单体结构：

```text
src/main/java/com/febrie/demo_bk
├─ article/{web,application,domain,infrastructure}
├─ file/{web,application,infrastructure}
├─ identity/{web,application,infrastructure}
├─ audit/{application,infrastructure}
└─ shared/{config,error,pagination,infrastructure,web}
```

业务模块之间只能通过应用服务和 DTO 协作；Mapper、持久化对象、缓存、安全和存储实现均留在各模块的 `infrastructure` 包中。
文章读取由 `ArticleQueryService` 保持稳定门面，详情、最新列表、排行榜和 DTO 水合分别放在 `article.application.query`；Redis 访问按详情、列表 DTO、最新索引和浏览量职责拆分。

### 环境要求

- JDK 17 或更高版本
- MySQL 8.x
- Redis 6.x+

### 配置说明

运行时密钥通过环境变量注入，请不要提交真实 `.env`、`.env.dev` 或 `.env.prod` 文件。
后端使用 Spring Profile 分离配置：`dev` 用于本地开发，`prod` 用于 Docker 正式部署。
本地 Maven 或 IDEA 启动时，可以把 `.env.dev.example` 复制为 `.env`，Spring Boot 会自动导入。

生成 JWT 密钥：

```bash
node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"
```

必要变量：

```properties
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=5090
BLOG_BACKEND_IMAGE=ghcr.io/lancasteerr/easy-blog-backend:prod
BLOG_FRONTEND_IMAGE=ghcr.io/lancasteerr/easy-blog-frontend:prod
BLOG_CORS_ALLOWED_ORIGINS=https://your-frontend-domain.example
BLOG_JWT_SECRET=your_generated_base64_secret
BLOG_DB_URL=jdbc:mysql://mysql:3306/white_jotter?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_general_ci&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&connectTimeout=3000&socketTimeout=10000&tcpKeepAlive=true&rewriteBatchedStatements=true
BLOG_DB_USERNAME=blog_user
BLOG_DB_PASSWORD=your_db_password
BLOG_REDIS_HOST=redis
BLOG_REDIS_PORT=6379
BLOG_REDIS_DATABASE=0
BLOG_REDIS_PASSWORD=your_redis_password
BLOG_STORAGE_ROOT=/app/blog-storage
BLOG_FILE_DOMAIN=https://your-domain.example/files
BLOG_UPLOAD_MAX_FILE_SIZE=5MB
BLOG_UPLOAD_MAX_REQUEST_SIZE=6MB
```

### 启动项目

启动本地开发依赖：

```bash
cp .env.dev.example .env.dev
docker compose --env-file .env.dev -f docker-compose.dev.yml up -d
```

使用 Maven 本地运行后端：

```bash
cp .env.dev .env
mvn spring-boot:run
```

正式 Docker Compose 部署会拉取 GitHub Actions 预先构建好的 GHCR 镜像：

```bash
cp .env.prod.example .env.prod
docker compose --env-file .env.prod -f docker-compose.prod.pull.yml pull
docker compose --env-file .env.prod -f docker-compose.prod.pull.yml up -d
```

仅打包：

```bash
mvn clean package
java -jar target/blog-backend.jar
```

### 接口规范

- RESTful API 风格
- 统一使用 JSON 返回数据
- 分页接口不直接暴露 `Page` 对象，仅返回必要字段

### TODO List

-  评论系统
-  Swagger / OpenAPI 文档
-  搜索 / tag功能
