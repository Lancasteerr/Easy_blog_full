# 2026-10-23 依赖安全补丁记录

本轮修改项目依赖、构建容器、运行镜像和 CI。开发环境构建已通过；按本次交付范围，完整镜像构建由维护者执行。未升级本机 Java/Node，未推送提交，未操作生产服务器。

## 版本差异

### 后端运行依赖

在 `demo_bk/pom.xml` 使用 Spring Boot 统一版本属性覆盖，最终 JAR 已检查同组模块版本一致。

| 依赖 | 原版本 | 新版本 |
| --- | --- | --- |
| Tomcat embedded core、el、websocket | 10.1.55 | 10.1.61 |
| Jackson BOM、core、databind、datatype、module | 2.21.4 | 2.21.7 |
| Jackson annotations | 2.21 | 2.21（目标 BOM 指定，保持） |
| Netty 各模块 | 4.1.135.Final | 4.1.137.Final |
| Log4j API、to-slf4j | 2.24.3 | 2.25.5 |

保持 Java 编译目标 17、Spring Boot 3.5.16、Spring Framework 6.2.19、Spring Security 6.5.11、JJWT 0.13.0、MyBatis Plus 3.5.17。没有修改数据库结构、HTTP 接口或 Redis 键与数据格式。

### 前端及构建依赖

直接依赖声明与 `package-lock.json` 同步；CI 使用 `npm ci`，不在构建时重新解析浮动依赖。未新增 overrides。

| 依赖 | 原版本 | 新版本 | 用途 |
| --- | --- | --- | --- |
| Axios | 1.18.1 | 1.20.0 | 浏览器请求 |
| DOMPurify | 3.4.12 | 3.4.17 | 文章 HTML 清洗 |
| Vue 与匹配的 @vue/* | 3.5.40 | 3.5.42 | 页面及编译器 |
| Vite | 8.1.5 | 8.1.6 | 开发/CI 构建 |
| follow-redirects | 1.16.0 | 1.16.1 | Axios Node 适配器传递依赖 |
| nanoid | 3.3.16 | 3.3.18 | 传递依赖 |
| source-map-js | 1.2.1 | 1.2.2 | 构建传递依赖 |
| @babel/parser | 7.29.7 | 7.29.9 | 新版 Vue 编译器要求的传递更新 |
| @babel/types | 7.29.7 | 7.29.8 | parser 配套传递更新 |

其他前端包保持锁定版本；Node 不进入最终前端运行镜像。

### Docker 与 CI

原浮动标签不能反推出历史镜像中实际安装的补丁版本。以下摘要固定基础镜像输入，应用最终发布摘要须由实际构建取得。

| 位置 | 原配置 | 新配置 |
| --- | --- | --- |
| 前端构建 | node:22-alpine | node:22.23.4-alpine |
| 前端运行 | nginxinc/nginx-unprivileged:stable-alpine | nginxinc/nginx-unprivileged:1.30.6-alpine |
| 后端构建 | maven:3.9-eclipse-temurin-17 | maven:3.9.16-eclipse-temurin-17，完整替换 JDK 为 17.0.21+9 |
| 后端运行 | eclipse-temurin:17-jre，补丁浮动 | 固定基础摘要，完整替换 JRE 为 17.0.21+9 |
| CI 测试 Java | Temurin 17，补丁浮动 | Temurin 17.0.21+9 |
| CI 前端验证 Node | 未单独验证 | 22.23.4 |
| Maven Wrapper | 3.9.11 | 3.9.11（保持） |

固定的基础镜像摘要：

```text
node:22.23.4-alpine@sha256:fe4f6d74e20370acb04ad889077d14d0f64a9c8ea6a42bc60cddf1e844699914
nginxinc/nginx-unprivileged:1.30.6-alpine@sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03
maven:3.9.16-eclipse-temurin-17@sha256:1a352420f7aba21f5ad08df31bab55f74c013fb491f1ae8ab1dd7ff9ed698584
eclipse-temurin:17-jre@sha256:e04fa72be13b257a59fdec6dc14af78c8f80f035d3c1ed4e03e45552a231d4c8
```

上述 Java 基础镜像自身尚未包含目标补丁，不能仅依赖标签。`demo_bk/docker/prepare-temurin.sh` 从 [Temurin 官方发布](https://github.com/adoptium/temurin17-binaries/releases/tag/jdk-17.0.21%2B9) 获取固定的 Linux x64 包并验证 SHA-256，然后完整替换旧 Java 目录。当前容器构建明确限定 `linux/amd64`。

```text
OpenJDK17U-jdk_x64_linux_hotspot_17.0.21_9.tar.gz
8b9b611d917e3dfd8419d3566b5949226f69b052fed7e109c1368add1dfe8711
OpenJDK17U-jre_x64_linux_hotspot_17.0.21_9.tar.gz
9f41bf88c7900c7097444a1bdeb84146ae692bc08842490ab098063c74bf9a8a
```

## 验证结果与边界

| 检查 | 结果 |
| --- | --- |
| 后端 Maven verify、可执行 JAR 打包 | 78 项测试通过，0 失败/错误/跳过；升级前 71 项 |
| HTTP/JSON 与认证回归 | 新增 6 项：匿名文章、未认证写入拒绝、畸形 JSON、错误密码、畸形 JWT、有效 JWT 注销 |
| Redis 兼容性 | 新增 JSON 日期/计数缓存格式回归；现有 Redis/Lua 测试通过，未连接生产 Redis |
| 最终 JAR 依赖检查 | Tomcat/Jackson/Netty/Log4j 模块版本符合上表 |
| 运行依赖 OSV 查询 | 查询 78 个运行依赖坐标，仅命中下述两项 Spring 条件型告警 |
| npm ci、npm run build | 通过，Vite 8.1.6 |
| 官方 npm registry 审计 | 0 项告警，包含开发依赖 |
| CI 静态检查 | actionlint、YAML 解析、shell 语法检查通过 |
| 完整镜像构建、启动冒烟、镜像漏洞扫描 | 未完成，按要求由维护者构建或 CI 验证 |

后端本地验证使用已有 `D:\java17`（Java 17.0.12），前端使用已有 Node 24.18.0；未安装或升级本机环境。容器准备阶段已成功校验并运行目标 Temurin 17.0.21+9，Node 22.23.4 构建阶段也已完成前端构建，但这不等于最终镜像验收。78 项后端测试尚未在目标 Java 补丁上重新执行，CI 已配置该测试，不能沿用本地结果宣称目标镜像通过。

手工浏览器登录、文章编辑、图片上传和 HTML 清洗交互尚未逐项执行；生产数据库、Redis、Caddy 也未进行远程验证。构建与自动测试通过不代表整站不存在漏洞。

## 保留告警及适用性

保持 Spring Framework 6.2.19 的兼容路线保留两个已知告警：

- [CVE-2026-47884：XsltView 路径限制问题](https://spring.io/security/cve-2026-47884/)：官方描述需要 XsltView、宽泛 URL 映射及隐式视图名，可能导致 SSRF/RCE。源码核查未使用这些视图功能，当前接口返回 JSON。版本仍受影响，只能基于当前功能判断未满足触发条件。
- [CVE-2026-47890：SSE 视图片段流损坏](https://spring.io/security/cve-2026-47890/)：需要通过 SSE 向客户端发送视图片段，并且攻击者能控制被发送的数据。源码未使用此功能。

两项官方公告给出的 6.2 修复版本为商业支持渠道的 6.2.20。本轮不进行 Spring 7 / Boot 4 迁移，不能声称后端依赖告警清零。CI 只对这两个 CVE 和 `spring-webmvc@6.2.19` 设置例外，2026-11-22 到期；源码若引入相关视图/SSE 类型，JAR 检查脚本要求重新评估。此文本匹配检查是回归提醒，不能替代安全审查。

MySQL、Redis 服务端本轮未升级；Compose 仍为 `mysql:8.4` 和 `redis:7.4-alpine`，未映射宿主机端口。Caddy 位于 nginx 前处理 HTTPS，按维护者确认未使用受影响的印度证书链；线上 Caddy/数据库/Redis 精确版本和防火墙暴露面仍需运维另行核实。

## CI 发布与部署、回滚

流水线在后端目标 Java 测试、前端构建和 npm 审计通过后构建候选镜像，再执行隔离网络 HTTP 冒烟和 Trivy 扫描。未被上述限定例外覆盖的 HIGH/CRITICAL（含未修复项）阻止发布。原始扫描、SBOM 和最终镜像摘要保存在 CI artifacts，最终发布使用已验证的同一批本地镜像。

维护者自行构建时可使用下列命令；工作目录为仓库根目录：

```bash
docker build --pull -t easy-blog-backend:ci ./demo_bk
docker build --pull -t easy-blog-frontend:ci ./blog_front
bash .github/scripts/smoke-images.sh
```

生产仍使用 `demo_bk/docker-compose.prod.pull.yml` 拉取已发布镜像。部署前记录当前前后端摘要，以便回滚。CI 发布后，将 `.env.prod` 中 `BLOG_BACKEND_IMAGE` 和 `BLOG_FRONTEND_IMAGE` 分别设为新发布的 `ghcr.io/...@sha256:...`，也可使用对应的 `sha-<完整提交号>` 标签，然后在 `demo_bk` 执行：

```bash
docker compose --env-file .env.prod -f docker-compose.prod.pull.yml pull backend frontend
docker compose --env-file .env.prod -f docker-compose.prod.pull.yml up -d --no-deps --force-recreate backend frontend
docker compose --env-file .env.prod -f docker-compose.prod.pull.yml ps
```

确认 Java/nginx 实际版本、首页、匿名接口、登录及上传正常，再结束部署。单纯重启旧容器不会替换镜像。回滚时把两个镜像变量恢复为部署前的摘要，重复以上拉取和重建步骤；沿用原数据卷及配置，不执行 `down -v`。本轮没有数据迁移。

本机仅给出后续升级建议：默认 Java 25.0.4.1 可升级至计划中的 25.0.5，需要验证项目时另配置 17.0.21+9；Node 24.18.0 可升级至 24.22.1。本轮不执行，最终目标运行兼容性以固定版本 CI/容器结果为准。
