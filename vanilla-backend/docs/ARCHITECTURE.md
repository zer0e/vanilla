# Vanilla 架构说明

## 分层设计

项目遵循经典 DDD 式分层，依赖方向为 `web → application → infrastructure`，`domain` 与 `common` 为共享层：

| 层 | 目录 | 职责 |
|---|---|---|
| **web** | `web/controller` | REST 端点，参数校验（`@Valid`）、Swagger 注解，不写业务逻辑 |
| **application** | `application/impl` | 业务编排、事务、鉴权注解；`config/` 放安全/异常/序列化等横切配置 |
| **infrastructure** | `infrastructure/db` | 持久化：`repository`（DO 实体）、`mapper`（MyBatis-Plus + XML） |
| | `infrastructure/converter` | MapStruct 对象转换（DTO/DO/VO） |
| | `infrastructure/docker` | DockerClient 连接管理 |
| **domain** | `domain` | 领域模型与枚举（Cluster/Stack/Service/Port/Volume、角色、数据类型） |
| **common** | `common` | 常量、统一响应、分页、异常、安全工具 |

### 对象流转

```
DTO（入参）--MapStruct--> DO（持久化）--MyBatis--> MySQL
DO --MapStruct--> VO（出参）
```

---

## 数据模型与软删除

所有主业务表（`t_cluster`、`t_stack`、`t_service`、`t_port`、`t_volume`）的 DO 继承 `Base`，包含统一的审计/软删除字段：

```
create_user, create_time, modify_user, modify_time,
delete_user, delete_time, status(0=存在, 1=已删除)
```

删除采用**软删除**：`status = 1` + 记录删除时间，列表查询显式过滤 `status = 0`。

**关联结构**：

```
t_cluster 1 ── n t_stack 1 ── n t_service 1 ── n t_port
                      │                │
                      └── n t_volume    └── 冗余 stack_id（权限校验便捷）
```

`t_service.envs` 使用 MySQL `JSON` 列 + MyBatis-Plus `JacksonTypeHandler` 序列化环境变量。

`t_user_role` 记录用户-角色-资源关联：`(user_id, role_id, stack_id?, cluster_id?)`。

---

## 认证与授权（RBAC）

### 认证：`JwtAuthenticationFilter`

1. 用户经 `POST /auth/api/v1/login`（用户名 + BCrypt 密码校验）换取 JWT（HS256，默认 12h）。
2. 后续请求携带 `Authorization: Bearer <token>`，`JwtAuthenticationFilter` 在前置鉴权（`@PreAuthorize`）前解析 JWT 得到登录用户名。
3. 调用 `UserServiceImpl.loadUserByUsername`：
   - 查 `t_user` → `t_user_role` → `t_role` / `t_permission`
   - 构造 `List<UserRolePermission>` 作为 authorities
   - 结果缓存到 Redis `USER_INFO_<用户名>`（24h TTL）
4. 构造 `UsernamePasswordAuthenticationToken` 写入 `SecurityContextHolder`。
5. 兼容兜底：无 JWT 时仍读取旧的 `x-auth-user` 请求头。

### 授权：`@PreAuthorize`

authority 由 `UserRolePermission.getAuthority()` 拼接：

```
role=true 时前缀 ROLE_：
  全局角色         → ROLE_admin
  集群角色         → ROLE_cluster_{id}_cluster_admin / _cluster_user
  栈角色           → ROLE_stack_{id}_stack_admin / _stack_member / _stack_readonly
```

方法级 `@PreAuthorize("hasAnyRole('stack_' + #dto.stackId + '_stack_admin')")` 基于 SpEL 取参数中的资源 id 动态拼接角色名进行校验。

> **注意**：角色授权信息缓存在 Redis。创建集群/栈授予新角色、用户管理页变更角色时，系统会自动失效相关用户缓存，新角色立即生效（`@PreAuthorize` 读取的是 SecurityContext 中缓存失效后重建的 authorities）。

### 权限矩阵

| 操作 | 所需角色 |
|---|---|
| 集群 create/update/delete | `ROLE_admin` |
| 用户 create/update/delete/list | `ROLE_admin` |
| 栈 create / list | 对应集群的 `cluster_admin` 或 `cluster_user` |
| 栈 update/delete、部署/停止/下架 | 对应栈的 `stack_admin` |
| 服务/端口/卷 create | `stack_admin` 或 `stack_member` |
| 服务/端口/卷 update/delete | `stack_admin` |
| 服务/端口/卷/历史/状态 list | `stack_admin` / `stack_member` / `stack_readonly` |

---

## Docker 集成

### DockerClientFactory

- 按 `clusterId` 缓存 `DockerClient`（`ConcurrentHashMap`）。
- 从 `t_cluster` 读取 `endpoint` / `tlsVerify` / `dockerCertPath` 构造配置：
  - `DefaultDockerClientConfig` + `ApacheDockerHttpClient`
  - 支持 `unix://`、`tcp://`、TLS
- `invalidate(clusterId)` 关闭并移除缓存（集群信息变更后调用）。

### DeployService（部署编排）

```
deployStack(stackId)
  ├─ getStack()                         # 校验栈存在
  ├─ dockerClientFactory.getClient()    # 取/建连接
  ├─ validateHostPorts()                # 宿主端口全局预校验（跨服务/副本）
  ├─ for service in services:
  │    pullImage()                      # 同步拉取镜像
  │    ├─ Recreate（默认）：删旧 → 按副本数创建并启动
  │    └─ RollingUpdate：逐副本「停旧 → 建新」（副本数变化退化为 Recreate）
  ├─ removeOrphanContainers()           # 清理已删除服务的残留容器
  ├─ catch BusinessException → 回滚清理容器
  ├─ recordHistory()
  └─ getStackStatus()                   # 返回运行状态
```

部署按服务维度进行，不再整栈先删；同栈其他服务在重部署期间保持运行。

**容器约定**

- 命名：`vanilla-{stackId}-{serviceName}[-{index}]`
- 标签：`com.vanilla.stack_id`、`com.vanilla.service_id`
- 端口：宿主端口 = 声明端口 + 副本索引（多副本偏移避免同服务冲突）；**跨服务范围重叠**在部署前预校验拦截
- 资源：`HostConfig.withCpuShares(cpu)`、`withMemory(memory * 1MB)`
- 端口映射与资源限制写入**同一个 HostConfig**，避免 `withHostConfig` 覆盖端口绑定

**状态统计**：按 `com.vanilla.stack_id` 标签过滤容器 → 按 `com.vanilla.service_id` 分组 → 统计 `running` 数量 → 映射为 `RUNNING/STOPPED/PARTIAL/NONE`。
健康统计数据（`healthyCount`）：配置了 `healthCheckCmd` 的服务逐个 `inspectContainerCmd` 读取 HEALTHCHECK 状态。

---

## 运行时分流（Docker / K8s）

`DeployServiceImpl` 是统一的部署入口：每个操作先按栈的集群类型分流——`K8S` 委托 `KubernetesStackServiceImpl`，其余走 Docker 链路（保持既有方法与单测不变）。两套运行时共用 `RuntimeStateResolver`（状态映射）与同一套 RBAC `@PreAuthorize`。

K8s 链路（`KubernetesClientFactory` 按 clusterId 缓存 `KubernetesClient`）：

```
栈(kubernetes) → vanilla 命名空间
  ├─ 服务 → Deployment（标签 com.vanilla.stack_id / service_id 分组）
  ├─ 端口 → Service（ClusterIP；声明端口 ≤ 2767 附加 NodePort 30000+端口）
  ├─ 卷   → PVC（ReadWriteOnce；下架保留）
  ├─ 健康 → readiness + liveness exec 探针（同 Docker HEALTHCHECK 参数）
  ├─ 停止 → scale=0；下架 → 删 Deployment+Service；日志 → Pod getLog() 截尾
  └─ 状态 → readyReplicas → RUNNING/PARTIAL/STOPPED/NONE（缩放为 0 记 STOPPED）
```

---

## 缓存与锁

| 用途 | 实现 |
|---|---|
| 用户信息/权限缓存 | `StringRedisTemplate`，key `USER_INFO_<username>`，24h TTL |
| 角色查询缓存 | `@Cacheable(cacheNames="roles")` |
| 集群人员更新锁 | Redisson 分布式锁 `lock-cluster-{id}`，避免并发更新成员列表 |

> 用户缓存是**安全敏感**的：权限变化（如新授予角色）需要主动失效，否则旧权限最长生效 24h。

---

## 横切配置

| 组件 | 职责 |
|---|---|
| `GlobalExceptionHandler` | `BusinessException` → 500 + msg；未知异常 → 500 通用提示 |
| `ResponseAdvice` | 统一响应包装（可选） |
| `SecurityConfig` | 无状态会话、关闭 CSRF、放行 Swagger/error、401/403 统一 JSON |
| `MybatisPlusConfig` | 分页插件等 |
| `RedisConfig` | RedisTemplate 序列化 |

---

## 关键流程时序

### 一次部署

```
调用方 ─POST /stack/api/v1/deploy {stackId}──▶ StackController
        x-auth-user: admin                     │
                                               ▼
                                   DeployService.deployStack
        @PreAuthorize(ROLE_stack_1_stack_admin)│
                                               │
                    ┌───────────┬──────────────┴─────────────┬─────────────┐
                    ▼           ▼                            ▼             ▼
              StackMapper  DockerClientFactory        ServiceMapper   PortMapper
              校验栈存在       建立连接/缓存              查询服务列表      查询端口
                    │           │                            │             │
                    └───────────┴────────────────────────────┴─────────────┘
                                               │ validateHostPorts（预校验）
                                               ▼
                          pull image → createContainer → startContainer × replicas
                                               │
                                   异常时 removeStackContainers 回滚
                                               ▼
                                   recordHistory("部署栈 X")
                                               ▼
                                   getStackStatus() → StackStatusVo
```

### 授权判定

```
请求 ──▶ XAuthUserFilter
         读取 x-auth-user ──▶ UserServiceImpl.loadUserByUsername
          ├─ Redis 命中？── 是 ──▶ 反序列化 User（含 authorities）
          └─ 未命中 ──▶ 查库组装 authorities ──▶ 写回 Redis(24h)
          ──▶ SecurityContextHolder.setAuthentication(...)
          ──▶ Controller 方法 @PreAuthorize 校验 authority
```
