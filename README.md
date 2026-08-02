# Vanilla — 服务部署平台（mini-PaaS）

Vanilla 是一个轻量级服务部署管理平台，以「**集群 → 栈 → 服务**」三层模型组织容器编排资源，支持 **Docker 与 Kubernetes 双运行时**，提供资源的全生命周期管理、基于角色的权限控制（RBAC）与可直接操作 K8s 的前端界面。

- **后端**：Java 17 · Spring Boot 3.3.1（[vanilla-backend/](vanilla-backend/)）
- **前端**：Vue 3 · Vite · Element Plus（[vanilla-frontend/](vanilla-frontend/)）

## 核心能力

- **双运行时一键部署**：同一套「部署 / 状态 / 停止 / 下架 / 日志」操作，按集群类型自动分流到 Docker（docker-java）或 K8s（fabric8）——K8s 上部署前还可以**预览将要生成的资源 YAML**。
- **资源模型**：
  - **集群**（Cluster）——Docker daemon 或 K8s API Server 连接信息，TLS 证书**上传存库**（非服务器本地目录）
  - **栈**（Stack）——集群下的逻辑分组，含成员授权；**K8s 模式下一栈一命名空间**（命名空间 = 栈名）
  - **服务**（Service）——镜像、副本数、资源限制、命令、环境变量、**容器端口（containerPorts）** 声明
  - **端口访问**（SVC）——独立于服务管理，选择服务后**引用其已声明的容器端口**并配置访问方式（ClusterIP / NodePort / LoadBalancer 或自动）
  - **卷**（Volume）——**栈级独立资源**，服务按引用挂载，删除服务不影响卷，下架保留数据
- **RBAC 权限**：`admin`（全局）→ `cluster_admin / cluster_user`（集群）→ `stack_admin / stack_member / stack_readonly`（栈），JWT 为唯一认证方式
- **健康检查**：服务可配置健康检查命令，Docker 映射为 HEALTHCHECK、K8s 生成 readiness/liveness 探针，状态接口返回健康数
- **更新策略**：`Recreate`（先删后建）与 `RollingUpdate`（逐副本替换）

## 快速开始

需要 JDK 17、Maven 3.6+、MySQL 8、Redis 7，以及一个目标运行时（Docker daemon 或 K8s 集群）。

```bash
# 1. 初始化数据库（创建 vanilla 库 + 全部表 + 基础角色，默认管理员 admin/admin123）
mysql -uroot -proot < vanilla-backend/src/main/resources/sql/vanilla.sql

# 2. 启动基础依赖（或用自己已有的 MySQL/Redis，改 application-dev.yaml 连接配置）
cd vanilla-backend && docker compose up -d

# 3. 后端（默认 8080，上下文路径 /vanilla）
./mvnw clean package -DskipTests
java -jar target/vanilla-backend-0.0.1-SNAPSHOT.jar

# 4. 前端开发模式（默认 5173，/vanilla 已配置代理转发到后端，无需跨域）
cd ../vanilla-frontend && npm install && npm run dev
```

打开 http://localhost:5173 ，用 `admin / admin123` 登录（登录后请修改密码）。更多细节见各子模块文档。

## 详细文档

| 文档 | 说明 |
|---|---|
| [vanilla-backend/README.md](vanilla-backend/README.md) | 后端功能、技术栈、架构、API 一览、K8s 运行时映射、RBAC |
| [vanilla-backend/docs/API.md](vanilla-backend/docs/API.md) | 全部接口的请求/响应字段与鉴权角色 |
| [vanilla-backend/docs/ARCHITECTURE.md](vanilla-backend/docs/ARCHITECTURE.md) | 分层设计、数据模型、认证授权、部署流程 |
| [vanilla-backend/docs/DEPLOYMENT.md](vanilla-backend/docs/DEPLOYMENT.md) | 从零搭建 + 云主机 Docker 验证 + 本地 K8s（OrbStack）验证 |
| [vanilla-frontend/README.md](vanilla-frontend/README.md) | 前端技术栈、页面结构、开发构建 |

## 仓库结构

```
vanilla/
├── vanilla-backend/          # Spring Boot 后端（DDD 分层 + MyBatis-Plus + docker-java + fabric8）
│   ├── docs/                 # API / 架构 / 部署指南
│   └── src/main/resources/sql/vanilla.sql   # 建表脚本 + seed 数据
├── vanilla-frontend/         # Vue 3 前端（集群 → 栈 → 服务 三级导航）
└── README.md
```

## License

[Apache License 2.0](LICENSE)