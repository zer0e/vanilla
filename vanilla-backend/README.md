# Vanilla — 服务部署平台后端

基于 Docker / K8s 的服务部署管理平台后端。以「集群 → 栈 → 服务」为模型组织容器编排资源，提供资源 CRUD、基于角色的权限控制（RBAC），并通过 docker-java 直连 Docker daemon、fabric8 直连 Kubernetes API 完成**部署 / 状态查询 / 停止 / 下架 / 日志**的全生命周期管理。

> **Docker 与 K8s 双运行时**：按集群 `type`（DOCKER / K8S）自动分流——Docker 走容器/标签模型（已验证 e2e），K8s 走 Deployment/Service/PVC 模型（资源映射与状态语义有单元测试固化，真机验证步骤见 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) 第 9 节）。

## 功能特性

- **资源管理**
  - 集群（Cluster）：Docker 连接信息（endpoint、TLS、证书）管理，per-cluster 连接缓存
  - 栈（Stack）：集群下的逻辑分组，含成员授权
  - 服务（Service）：镜像、副本数、CPU/内存限制、命令、环境变量（JSON 存储）
  - 端口（Port）：服务端口声明（协议 + 端口），部署时映射到宿主
  - 卷（Volume）：栈级卷声明（名称 + 大小）
- **部署生命周期**（Docker / K8s 双运行时）
  - Docker：拉取镜像 → 按副本数创建并启动容器（env / 端口映射 / 资源限制 / 标签 / HEALTHCHECK）
  - K8s：栈 → `vanilla` 命名空间下 Deployment + Service（NodePort），卷 → PVC
  - 状态查询：Docker 按容器标签、K8s 按 Deployment readyReplicas 统计 RUNNING / STOPPED / PARTIAL / NONE，含**健康数 healthyCount**
  - 停止 / 下架：幂等操作，部署前自动清理同栈旧资源；K8s 下架保留 PVC
  - **容器日志**：按栈 + 服务 + 副本索引查看最近 N 行（stdout+stderr）
  - 部署前**宿主端口全局预校验**（Docker）、冲突快速失败；中途失败自动回滚清理
- **RBAC 鉴权**：`admin`（全局）→ `cluster_admin / cluster_user`（集群级）→ `stack_admin / stack_member / stack_readonly`（栈级）
- **用户管理**：用户 CRUD + 角色绑定（全局/集群/栈作用域），变更即时失效缓存
- **更新策略**：`Recreate`（默认，先删后建）与 `RollingUpdate`（逐副本替换，其余副本持续服务；副本数变化时退化为全量重建）
- **操作历史**：栈下所有操作留痕（创建/更新/删除/部署/停止/下架）
- 统一响应封装、全局异常处理、接口文档（Knife4j / OpenAPI 3）

## 技术栈

| 分类 | 选型 |
|---|---|
| 语言 / 框架 | Java 17 · Spring Boot 3.3.1 · Spring Security |
| ORM | MyBatis-Plus 3.5.7 · PageHelper 5.3.2 |
| 存储 | MySQL 8（软删除 + JSON 列）· Redis 7（用户/角色缓存）· Redisson 3.32（分布式锁） |
| 运行时 | Docker：docker-java 3.3.6（core + httpclient5 transport）· K8s：fabric8 kubernetes-client 6.13.0 |
| 其他 | MapStruct 1.5 · Knife4j 4.4 · Lombok · spring-boot-starter-actuator |

## 架构概览

```
┌────────────────────────── web（Controller 层）──────────────────────────┐
│  ClusterController · StackController · ServiceController                 │
│  PortController · VolumeController · HistoryController                    │
└───────────────┬──────────────────────────────────────────────────────────┘
                │ @PreAuthorize（方法级 RBAC）
┌───────────────▼──────────────── application（应用层）────────────────────┐
│  ClusterServiceImpl · StackServiceImpl · SerServiceImpl                  │
│  PortServiceImpl · VolumeServiceImpl · HistoryServiceImpl                 │
│  DeployServiceImpl（部署编排） · UserServiceImpl（认证/授权）              │
│  config/：Security · GlobalExceptionHandler · ResponseAdvice · Redis     │
└───────────────┬──────────────────────────────────────────────────────────┘
                │
┌───────────────▼──────────────── infrastructure（基础设施层）─────────────┐
│  db/repository（DO 实体） · db/mapper（MyBatis） · converter（MapStruct） │
│  docker/DockerClientFactory（per-cluster DockerClient 缓存）              │
└───────────────┬──────────────────────────────────────────────────────────┘
                │
      ┌─────────▼─────────┐   ┌─────────▼─────────┐   ┌──────────▼──────────┐
      │      MySQL        │   │      Redis        │   │   Docker daemon     │
      │   vanilla 库      │   │  用户/角色缓存      │   │  unix/tcp socket    │
      └───────────────────┘   └───────────────────┘   └─────────────────────┘
```

> 认证机制：用户名 + 密码经 `POST /auth/api/v1/login` 换取 **JWT**，受保护接口携带 `Authorization: Bearer <token>`；`JwtAuthenticationFilter` 在 `@PreAuthorize` 前将登录用户名及其角色权限装载到 Spring Security 上下文完成鉴权。默认管理员 `admin / admin123`（登录后请修改）。旧 `x-auth-user` 请求头仍兼容（过渡期）。

## 目录结构

```
vanilla-backend/
├── src/main/java/com/github/zer0e/vanilla/
│   ├── application/          # 应用层：服务实现、DTO、VO、配置
│   │   ├── dto/              # 请求对象（Create/Update/Get/Delete）
│   │   ├── vo/               # 响应对象（含 StackStatusVo/ServiceStatusVo）
│   │   ├── impl/             # 业务实现
│   │   └── config/           # 安全、异常、响应封装、Redis 配置
│   ├── common/               # 通用：Constants、RestResponse、PageData、异常
│   ├── domain/               # 领域模型：Cluster/Stack/Service/Port/Volume、角色枚举
│   ├── infrastructure/       # 基础设施：db(repository/mapper)、converter、docker
│   └── web/controller/       # REST 控制器
├── src/main/resources/
│   ├── application.yaml      # 主配置（默认激活 dev）
│   ├── application-dev.yaml  # 开发环境（MySQL/Redis 连接）
│   ├── mapper/*.xml          # MyBatis SQL
│   └── sql/vanilla.sql       # 建表脚本 + 基础 seed 数据
├── compose.yaml              # 本地基础设施（MySQL/Redis）
├── pom.xml
└── mvnw / mvnw.cmd
```

## 快速开始

### 环境要求

- JDK 17、Maven 3.6+（或使用项目内 `./mvnw`）
- MySQL 8、Redis 7

### 1. 初始化数据库

```bash
mysql -uroot -p < src/main/resources/sql/vanilla.sql
```

脚本会创建 `vanilla` 库、全部表，并 seed 基础角色和 `admin` 管理员账号。

### 2. 配置

默认启用 `dev` profile，连接 `localhost:3306`（root/root）与 `localhost:6379`。可在 `application-dev.yaml` 中调整：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/vanilla
    username: root
    password: root
  data:
    redis:
      host: localhost
      port: 6379
```

也可用仓库自带的 `compose.yaml` 一键起 MySQL + Redis：

```bash
docker compose up -d
```

### 3. 构建与运行

```bash
./mvnw clean package -DskipTests
java -jar target/vanilla-backend-0.0.1-SNAPSHOT.jar
```

服务启动后监听 `8080`，上下文路径 `/vanilla`。

- 接口文档（Swagger UI）：http://localhost:8080/vanilla/doc.html
- 健康检查：http://localhost:8080/vanilla/actuator/health

### 4. 调用示例

```bash
# 创建 DOCKER 集群（连接本机 docker daemon）
curl -X POST http://localhost:8080/vanilla/cluster/api/v1/create \
  -H "Content-Type: application/json" \
  -H "x-auth-user: admin" \
  -d '{"clusterName":"docker-1","type":"DOCKER","endpoint":"unix:///var/run/docker.sock","tlsVerify":false}'

# 创建栈 -> 创建服务 -> 添加端口 -> 部署
curl -X POST http://localhost:8080/vanilla/stack/api/v1/create \
  -H "Content-Type: application/json" -H "x-auth-user: admin" \
  -d '{"clusterId":1,"stackName":"web"}'
curl -X POST http://localhost:8080/vanilla/service/api/v1/create \
  -H "Content-Type: application/json" -H "x-auth-user: admin" \
  -d '{"stackId":1,"serviceName":"nginx","image":"nginx:latest","replicas":1,"cpu":512,"memory":128}'
curl -X POST http://localhost:8080/vanilla/port/api/v1/create \
  -H "Content-Type: application/json" -H "x-auth-user: admin" \
  -d '{"stackId":1,"serviceId":1,"protocol":"tcp","port":80}'
curl -X POST http://localhost:8080/vanilla/stack/api/v1/deploy \
  -H "Content-Type: application/json" -H "x-auth-user: admin" \
  -d '{"stackId":1}'
```

> **角色即时生效**：创建集群/栈后系统会自动失效创建者的权限缓存（`USER_INFO_<用户名>`），新授予的 `cluster_admin` / `stack_admin` 立即生效，无需手动清理；用户管理页的角色变更同样即时失效。

## API 一览

| 模块 | 端点 | 说明 |
|---|---|---|
| 认证 | `POST /auth/api/v1/login` | 用户名+密码登录，返回 JWT（公开接口） |
| 集群 | `POST /cluster/api/v1/create` | 创建集群（需 admin） |
| | `POST /cluster/api/v1/update` | 修改集群（需 admin） |
| | `POST /cluster/api/v1/delete` | 删除集群（需 admin，软删除） |
| | `GET /cluster/api/v1/list` | 当前用户有权限的集群 |
| 栈 | `POST /stack/api/v1/create` | 创建栈 |
| | `POST /stack/api/v1/update` | 修改/改名栈 |
| | `POST /stack/api/v1/delete` | 删除栈 |
| | `POST /stack/api/v1/list` | 分页查询栈 |
| | `POST /stack/api/v1/deploy` | **部署栈到集群** |
| | `POST /stack/api/v1/status` | **查询栈运行状态** |
| | `POST /stack/api/v1/stop` | **停止栈** |
| | `POST /stack/api/v1/remove` | **下架栈（停+删容器）** |
| | `POST /stack/api/v1/logs` | **查看服务容器日志** |
| 服务 | `POST /service/api/v1/create` | 创建服务 |
| | `POST /service/api/v1/update` | 修改服务 |
| | `POST /service/api/v1/delete` | 删除服务 |
| | `POST /service/api/v1/list` | 分页查询服务（含关联端口/卷） |
| 端口 | `POST /port/api/v1/create` | 添加端口 |
| | `POST /port/api/v1/update` | 修改端口 |
| | `POST /port/api/v1/delete` | 删除端口 |
| | `POST /port/api/v1/list` | 分页查询端口 |
| 卷 | `POST /volume/api/v1/create` | 创建卷 |
| | `POST /volume/api/v1/update` | 修改卷 |
| | `POST /volume/api/v1/delete` | 删除卷 |
| | `POST /volume/api/v1/list` | 分页查询卷 |
| 历史 | `POST /history/api/v1/list` | 查询栈操作历史 |
| 用户 | `POST /user/api/v1/create` | 创建用户 + 角色绑定（需 admin） |
| | `POST /user/api/v1/update` | 修改用户 / 替换角色（需 admin） |
| | `POST /user/api/v1/delete` | 禁用用户 + 清角色（需 admin） |
| | `POST /user/api/v1/list` | 分页查询用户（需 admin） |

完整字段、鉴权角色与响应示例见 **[docs/API.md](docs/API.md)**。

## K8s 运行时映射

K8S 类型集群的栈操作由 `KubernetesStackServiceImpl` 承担（`DeployServiceImpl` 按集群类型自动分流），资源统一落在 `vanilla` 命名空间：

| 平台概念 | K8s 资源 | 说明 |
|---|---|---|
| 栈 | `vanilla` 命名空间 + `com.vanilla.stack_id` 标签 | 命名空间不存在时自动创建（无权限则需预先创建） |
| 服务 | `Deployment`（名 `vanilla-{stackId}-{serviceName}`） | 副本数、镜像、env、容器端口、资源限制（CPU shares→m、内存 Mi） |
| 更新策略 | Deployment strategy | `Recreate` / `RollingUpdate`（K8s 原生处理滚动与扩缩容） |
| 健康检查 | readiness + liveness exec 探针 | 与 Docker HEALTHCHECK 参数一致（`sh -c '<healthCheckCmd>'`） |
| 端口 | Service（类型可由服务 `serviceType` 显式指定：ClusterIP / NodePort / LoadBalancer，留空自动） | 自动/NodePort：声明端口 ≤ 2767 时附加 NodePort 30000+端口，超出交给 k8s 分配 |
| 卷 | `PersistentVolumeClaim`（名 `vanilla-{stackId}-{serviceId}-{volumeName}`，ReadWriteOnce） | 下架不删 PVC，与 Docker named volume 语义一致 |
| 停止 | Deployment scale=0 | 状态查询反映为 STOPPED（Deployment 仍存在） |
| 下架 | 删除 Deployment + Service（保留 PVC） | — |
| 日志 | 按标签选 Pod → `getLog()` 截取最近 N 行 | 多副本按 Pod 名排序取指定索引 |

集群连接复用 `t_cluster.endpoint`（API Server 地址，`tcp://` 自动转 https）+ `dockerCertPath`（兼容 `ca.crt/client.crt/client.key` 与 `ca.pem/cert.pem/key.pem`），按 clusterId 缓存并在集群更新/删除时失效。

## RBAC 权限模型

权限通过 `t_user_role` 关联用户与角色构造，`@PreAuthorize` 校验形如 `ROLE_xxx` 的 authority：

| 角色 | Authority 示例 | 适用范围 |
|---|---|---|
| 全局管理员 | `ROLE_admin` | 集群创建/修改/删除 |
| 集群管理员 | `ROLE_cluster_{id}_cluster_admin` | 创建/查询集群下的栈 |
| 集群成员 | `ROLE_cluster_{id}_cluster_user` | 创建/查询集群下的栈 |
| 栈管理员 | `ROLE_stack_{id}_stack_admin` | 栈更新/删除、部署/停止/下架、服务/端口/卷变更 |
| 栈成员 | `ROLE_stack_{id}_stack_member` | 创建服务/端口/卷 |
| 栈只读 | `ROLE_stack_{id}_stack_readonly` | 查询状态、历史、列表 |

创建集群/栈时，创建者自动获得对应 `cluster_admin` / `stack_admin` 角色。

## 相关文档

- [接口文档 docs/API.md](docs/API.md) — 全部端点、请求/响应、错误码
- [架构说明 docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — 分层设计、鉴权、部署流程
- [部署指南 docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) — 环境搭建、配置、云主机验证

## 测试验证

- **单元测试**：`DeployServiceImplTest`（29 个用例）+ `KubernetesStackServiceImplTest`（9 个用例，Mockito）。Docker 侧锁住端口绑定写入 HostConfig、宿主端口跨服务冲突预校验、容器命名、状态映射、失败回滚、Recreate 先删后建、RollingUpdate 逐副本替换、容器日志选择与读取、健康检查 HEALTHCHECK 构建与健康统计；K8s 侧锁住集群类型识别、Deployment/Service/PVC 资源映射、readyReplicas 状态语义（RUNNING/PARTIAL/STOPPED/NONE）、停止 scale=0、下架保留 PVC、日志按副本截取。运行：`./mvnw test`（无需外部依赖，Spring 上下文测试已标注 `@Disabled`）。
- **端到端验证**：已在云主机（Alibaba Cloud Linux 4 + Docker 24.0.9 + MySQL 8.0 + Redis 7.2）跑通集群/栈/服务/端口/卷 CRUD、RBAC、部署→状态→停止→重新部署→下架、多副本（端口偏移）与多服务场景。回归测试中发现的端口映射丢失、多副本端口冲突、软删除过滤等缺陷均已修复并固化为测试。
