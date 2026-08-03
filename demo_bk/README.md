
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

```yml
src/main/java
├─ controller   // REST APIs
├─ service      // Business logic
├─ dao          // Data access
├─ pojo         // JPA entities
├─ dto          // Data transfer objects
├─ filter       // Filter
├─ util         // Utility
├─ result       // Return code
└─ config       // Configuration
```

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

-  PV count
-  Comment system
-  API documentation (Swagger / OpenAPI)
-  Rate limiting

[Frontend](https://github.com/Lancasteerr/Easy_blog_frontend)

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
```yml

src/main/java
├─ controller // 接口层（REST API）
├─ service // 业务逻辑层
├─ dao // 数据访问层
├─ pojo // JPA 实体类
├─ dto // 数据传输对象
├─ filter //过滤器
├─ util //工具类
├─ result //返回值
└─ config // 配置类
```
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

### Roadmap

-  浏览量统计
-  评论系统
-  Swagger / OpenAPI 文档
-  接口访问限流

[前端仓库](https://github.com/Lancasteerr/Easy_blog_frontend)

### License

MIT License
