# Easy Blog 后端架构约定

## 目标结构

后端保持单 Maven 工程，按业务能力组织为模块化单体：

```text
com.febrie.demo_bk
├─ article                 DTO、Command/Query 应用服务
│  ├─ web                  文章 HTTP 接口
│  └─ internal             Mapper、实体、缓存、浏览统计、领域事件与任务
├─ file                    DTO、文件应用服务
│  ├─ web                  文件 HTTP 接口
│  └─ internal             Mapper、实体、存储实现与临时文件任务
├─ identity                认证应用服务
│  ├─ web                  登录与退出接口
│  └─ internal             用户、JWT、登录限制、黑名单和安全配置
├─ audit                   操作日志注解
│  └─ internal             异步日志服务、切面、Mapper、实体、脱敏与清理任务
├─ shared                  无业务归属的共享基础能力
│  ├─ config               MyBatis、Redis、Scheduling 配置
│  ├─ infrastructure       低层 RedisStore
│  └─ web                  Result、异常处理与 HTTP 辅助
└─ DemoBkApplication.java
```

模块根包保存需要被同模块 Web 层或其他模块调用的应用服务和数据契约；`web` 保存 HTTP 适配代码；`internal` 保存 Mapper、持久化对象、定时任务和基础设施实现。

## 允许的依赖方向

```text
article  ──> file
article  ──> shared
article  ──> audit（仅使用操作日志注解）
file     ──> shared
identity ──> shared
identity ──> audit（仅使用操作日志注解）
audit    ──> shared
```

- `shared` 不得依赖任何业务模块。
- 业务模块不得访问其他模块的 Mapper、持久化对象或 `internal` 包。
- Controller 只能调用所属模块的应用服务，不能直接访问 Mapper。
- 跨模块调用必须通过对方模块根包中的公开应用服务或数据契约。
- `article`、`identity` 对 `audit` 的依赖仅限根包中的 `OperationLoger` 注解，审计模块不得反向依赖业务模块。

## 模块职责

### article

- `ArticleCommandService` 负责文章新增、编辑、删除和事务边界。
- `ArticleQueryService` 负责详情、最新列表和浏览量排行榜查询。
- 正文文件引用解析、文章缓存、浏览统计和定时任务属于 `article.internal`。

### file

- `FileService` 统一负责上传、查询、校验、绑定、释放和临时文件清理。
- 存储接口及本地存储实现属于 `file.internal`。
- 其他模块只能通过文件 ID 调用 `FileService`，不得依赖文件持久化对象。

### identity

- `AuthService` 负责登录认证和退出登录。
- 用户持久化、登录失败计数、JWT、黑名单和安全过滤器属于 `identity.internal`。

### audit

- 操作日志注解是模块对外契约。
- 切面、日志写入服务、参数脱敏、持久化和清理任务属于 `audit.internal`。
- `OperationLogAspect` 必须通过 `OperationLogService` 写库，不能直接调用 Mapper；该独立服务是使 `@Async` 经过 Spring 代理生效的必要异步边界。

### shared

只有同时满足以下条件的代码才能进入 `shared`：

1. 不包含文章、文件、认证或审计业务语义；
2. 被多个模块实际使用，而不是为未来复用预先抽象；
3. 不依赖任何业务模块。

## 编码与评审规则

- 新增或修改的业务代码必须带有适当的中文注释。
- 不以文件行数作为唯一拆分标准；只有存在不同变更原因时才拆分。
- 不为单一实现机械创建 `Service`/`ServiceImpl` 接口对。
- 统一使用构造器注入。
- MyBatis 接口统一以 `Mapper` 命名。
- 定时任务放在所属业务模块，不建立全局 `task` 包。
- 工具方法优先放在所属模块；只有符合 `shared` 准入条件时才上移。
- 重构不得改变外部 API、MySQL 表结构、Redis Key、TTL 或降级行为。

## Pull Request 检查清单

- [ ] 新代码位于正确的业务模块。
- [ ] 没有跨模块访问 `internal`、Mapper 或持久化对象。
- [ ] 没有向 `shared` 或全局工具类加入业务逻辑。
- [ ] Controller 没有直接访问 Mapper。
- [ ] 接口、数据库和 Redis 兼容性未被意外改变。
- [ ] 新增或修改代码包含必要的中文注释。
- [ ] 单元测试和端到端冒烟路径覆盖本次变更。
