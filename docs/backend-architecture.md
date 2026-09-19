# Easy Blog 后端架构约定

## 总体原则

后端保持单 Maven 工程，采用“业务模块优先、模块内部四层”的模块化单体结构：

```text
com.febrie.demo_bk
├─ article
├─ file
├─ identity
├─ audit
└─ shared
```

每个业务模块按实际需要使用以下层级，不为没有业务模型的模块创建空目录：

| 层级 | 职责 | 禁止事项 |
|---|---|---|
| `web` | Controller、HTTP 请求和响应适配 | 不得直接访问 Mapper、实体、Redis 或文件存储 |
| `application` | 用例编排、事务边界、模块公开服务和 DTO | 不得依赖 Controller、Servlet 或 Web 异常处理器 |
| `domain` | 纯业务规则、值对象和领域事件 | 不得依赖 Spring、MyBatis、Redis 或 Servlet |
| `infrastructure` | 数据库、缓存、安全、存储、配置和定时任务 | 不得被其他业务模块直接引用 |

本项目采用实用分层：应用层可以直接依赖本模块基础设施，但跨模块只能访问对方应用层公开服务或 DTO。

## 目录结构

```text
article
├─ web
├─ application
│  ├─ dto
│  ├─ content
│  ├─ event
│  └─ query
├─ domain
│  └─ event
└─ infrastructure
   ├─ persistence
   ├─ cache
   └─ scheduling

file
├─ web
├─ application
│  ├─ dto
│  └─ storage
└─ infrastructure
   ├─ persistence
   ├─ storage
   ├─ config
   └─ scheduling

identity
├─ web
│  └─ request
├─ application
└─ infrastructure
   ├─ persistence
   └─ security

audit
├─ application
└─ infrastructure
   ├─ aop
   ├─ persistence
   └─ scheduling

shared
├─ config
├─ error
├─ pagination
├─ infrastructure
│  └─ redis
└─ web
   ├─ advice
   ├─ request
   └─ response
```

每个包通过 `package-info.java` 就近说明职责。新增类必须先确定业务归属，再确定所属层级。

## 文章查询与缓存职责

`ArticleQueryService` 是 Controller 使用的稳定门面，只负责公共参数校验和查询策略分派。具体读取流程位于 `article.application.query`：

- `ArticleDetailQuery`：详情缓存、负缓存、回源锁、MySQL 降级和浏览量记录；
- `LatestArticleListQuery`：最新文章版本索引、分页索引修复和完整分页降级；
- `ArticleViewRankQuery`：浏览量排行榜读取、失效成员修复和 MySQL 快照降级；
- `ArticleListHydrator`：批量 DTO 水合、单文章锁、负缓存和实时浏览量合并。

文章缓存按数据责任拆分，禁止重新合并为包含所有缓存能力的聚合 Store：

- `ArticleDetailCacheStore` 只管理详情缓存、详情负缓存和详情锁 Key；
- `ArticleListCacheStore` 只管理列表 DTO 缓存、批量读取和列表锁 Key；
- `LatestArticleIndexStore` 只管理最新版本号、分页索引和版本推进；
- `ArticleViewStore` 只管理实时浏览量、排行榜和相关修复状态；
- `CacheValue` 统一表达正常命中、负缓存命中和真正未命中。

上述拆分不得改变 Redis Key、TTL、Lua 脚本、锁等待时间和故障降级语义。

## 允许的依赖方向

```text
article.application ──> file.application
article.web ──────────> audit.application（仅操作日志注解）
identity.web ─────────> audit.application（仅操作日志注解）
业务模块 ─────────────> shared
shared ───────────────> 不依赖任何业务模块
```

- Controller 只能调用所属模块应用服务以及必要的共享 Web 能力。
- 业务模块不得引用其他模块的 `domain`、`infrastructure`、Mapper、持久化对象或 Store。
- `shared` 代码必须无业务语义、已被多个模块实际使用，并且不依赖业务模块。
- 定时任务放在所属模块的 `infrastructure.scheduling`，不建立全局任务包。

## 文件放置规则

- HTTP 请求模型放入 `web.request`，不得使用持久化实体接收请求。
- 应用层 DTO 放入 `application.dto`；MyBatis 实体和 Mapper 放入 `infrastructure.persistence`。
- Redis 业务缓存放入所属模块的 `infrastructure.cache`；只有无业务语义的 Redis 能力才能进入 `shared`。
- 复杂查询按场景放入模块的 `application.query`，对 Web 层继续提供稳定的应用服务门面。
- 模块专属配置放入模块 `infrastructure.config`；跨模块全局配置放入 `shared.config`。
- 不新增含义模糊的 `common`、`util`、`manager`、`impl` 或全局 `task` 包。
- 不为单一实现机械创建 `Service`/`ServiceImpl`、Repository 接口。
- 工具代码优先保留在所属模块和所属层，满足共享准入条件后才能上移。
- 模块根包除 `package-info.java` 外不直接存放业务类。

## 兼容性约束

目录治理和职责拆分不得改变：

- HTTP 路径、请求字段、响应 JSON 和状态码；
- MySQL 表结构、SQL 语句 ID 和事务边界；
- Redis Key、TTL、缓存 JSON 字段、Lua 脚本和降级行为；
- 文件路径规则、JWT 行为和操作日志语义。

## 评审检查清单

- [ ] 新类位于正确的业务模块和层级。
- [ ] Controller 未直接引用 Mapper、持久化对象或 Store。
- [ ] 不存在跨模块引用 `infrastructure` 或 `domain`。
- [ ] `shared` 未引入业务语义或业务模块依赖。
- [ ] 未新增含义模糊的全局工具包和任务包。
- [ ] MyBatis XML namespace 与 Java Mapper 包名一致。
- [ ] 新增或修改代码包含必要的中文注释。
- [ ] 外部接口、数据库和 Redis 兼容性未被改变。
- [ ] JDK 17 下 `mvnw clean test` 全部通过。
