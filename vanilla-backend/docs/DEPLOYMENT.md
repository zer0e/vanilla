# Vanilla 部署指南

覆盖从零搭建环境、初始化数据库、构建运行，到在 Linux 主机上完成 Docker 部署与端到端验证的完整过程。**本指南已按一次真实云主机（Alibaba Cloud Linux 4，x86_64）部署验证过。**

## 1. 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | 需设为默认 java（项目要求 17，8 不可用） |
| Maven | 3.6+ | 或用项目内 `./mvnw` wrapper |
| MySQL | 8.x | 应用数据 |
| Redis | 7.x | 用户/角色缓存、Redisson 锁 |
| Docker | 20+ | 部署目标（仅需 Docker 模式） |

## 2. 安装基础环境（Linux 示例）

```bash
# Docker
dnf install -y docker
systemctl enable --now docker

# Java 17（如系统默认是 8，需切换 alternatives）
dnf install -y java-17-openjdk-devel
J17=/usr/lib/jvm/java-17-*/
alternatives --set java  $J17/bin/java
alternatives --set javac $J17/bin/javac

# Maven
dnf install -y maven
```

## 3. 启动 MySQL 与 Redis

方式一：仓库自带 `compose.yaml`（MySQL root 密码 `root`，端口 3306/6379）：

```bash
docker compose up -d
```

方式二：分别启动（国内网络拉不到 Docker Hub 时见第 7 节镜像加速）：

```bash
docker run -d --name vanilla-mysql \
  -e MYSQL_ROOT_PASSWORD=root -p 3306:3306 -v vanilla-mysql-data:/var/lib/mysql mysql:8.0
docker run -d --name vanilla-redis -p 6379:6379 redis:7.2
```

## 4. 初始化数据库

```bash
mysql -uroot -proot < src/main/resources/sql/vanilla.sql
```

脚本创建 `vanilla` 库、全部表并 seed：

- **12 张表**：`t_cluster`、`t_stack`、`t_service`、`t_port`、`t_volume`、`t_service_volume`（服务引用卷）、`t_user`、`t_role`、`t_permission`、`t_role_permission`、`t_user_role`、`t_operation_history`
- **7 个角色**：`admin`、`user`、`cluster_admin`、`cluster_user`、`stack_admin`、`stack_member`、`stack_readonly`
- **管理员账号**：`admin`（login_name，绑定 `admin` 角色）

> 表结构含软删除审计字段与唯一约束（如 `t_stack.uk_cluster_stack`）；集群/栈为软删除，服务/端口（SVC）/卷为**物理删除**并即时释放对应唯一键（如 `t_service.uk_stack_service` 允许同名服务重建）。已与 DO 实体对齐。

> **已有库升级**：本版本 `t_service` 新增 `health_check_cmd` 列（健康检查命令）、`t_cluster` 新增证书存库三列、`t_user` 新增密码列（JWT 登录），存量库需执行：
> ```sql
> ALTER TABLE vanilla.t_service ADD COLUMN health_check_cmd varchar(255) NULL COMMENT '健康检查命令，如 curl -f http://localhost/health || exit 1' AFTER termination_grace_period_seconds;
> ALTER TABLE vanilla.t_cluster ADD COLUMN ca_cert longtext NULL COMMENT 'CA 证书（PEM，上传存库）',
>   ADD COLUMN client_cert longtext NULL COMMENT '客户端证书（PEM）',
>   ADD COLUMN client_key longtext NULL COMMENT '客户端私钥（PEM）';
> ALTER TABLE vanilla.t_user ADD COLUMN password varchar(255) NULL COMMENT '登录密码（BCrypt 哈希）' AFTER login_name;
> -- 为存量 admin 设置默认密码 admin123（BCrypt 哈希，登录后请修改）
> UPDATE vanilla.t_user SET password='$2a$10$FhdXj62bhe2bqtr/47dzK.vQRWEbMBtjvP0di7gEpM.z5dn3Ya7Wq'
>   WHERE login_name='admin' AND password IS NULL;
> -- 端口访问（SVC）新拆分：容器端口列 + 服务引用卷表
> ALTER TABLE vanilla.t_service ADD COLUMN container_ports json NULL COMMENT '容器/Pod 暴露端口 [{protocol, port}]（服务表单声明，SVC 创建时引用）' AFTER health_check_cmd;
> CREATE TABLE IF NOT EXISTS vanilla.t_service_volume (
>   id int auto_increment primary key,
>   service_id int not null,
>   volume_id int not null,
>   create_time datetime null,
>   unique key uk_service_volume (service_id, volume_id)
> ) comment '服务引用卷表';
> ```

## 5. 配置

默认激活 `dev` profile，连接本机 MySQL/Redis：

```yaml
# application-dev.yaml
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

若后端与数据库不在同一主机，改为对应地址。

## 6. 构建与运行

```bash
./mvnw clean package -DskipTests      # 产物 target/vanilla-backend-0.0.1-SNAPSHOT.jar
java -jar target/vanilla-backend-0.0.1-SNAPSHOT.jar
```

后台运行（`< /dev/null` 避免持有 SSH 会话）：

```bash
nohup java -jar vanilla-backend.jar > app.log 2>&1 < /dev/null &
```

**验证启动**：

```bash
curl http://localhost:8080/vanilla/actuator/health   # HTTP 200
# Swagger：http://localhost:8080/vanilla/doc.html
```

> 默认端口 8080、上下文路径 `/vanilla`。如需改端口，可在 `application.yaml` 加 `server.port`。

## 7. 国内镜像加速（可选）

Docker Hub 在国内直连常超时。配置 registry mirror 后重启：

```bash
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<EOF
{"registry-mirrors":["https://docker.m.daocloud.io","https://docker.1panel.live","https://hub.rat.dev","https://dockerproxy.net"]}
EOF
systemctl restart docker
docker info | grep -A6 "Registry Mirrors"
```

拉取镜像测试：`docker pull mysql:8.0`

## 8. 端到端验证（Docker 部署）

以下流程已在云主机完整跑通。认证均使用登录换取的 JWT（`Authorization: Bearer <token>`）。

### 8.1 冒烟 + 创建集群

```bash
# 登录获取 TOKEN（受保护接口均需 Authorization: Bearer <token>）
TOKEN=$(curl -s -X POST http://localhost:8080/vanilla/auth/api/v1/login \
  -H "Content-Type: application/json" \
  -d '{"loginName":"admin","password":"admin123"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# 创建 DOCKER 集群，连接本机 docker daemon
curl -X POST http://localhost:8080/vanilla/cluster/api/v1/create \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"clusterName":"docker-test","type":"DOCKER","endpoint":"unix:///var/run/docker.sock","tlsVerify":false}'

# 创建集群自动授予 cluster_admin 并即时失效缓存，无需手动操作
```

### 8.2 创建栈 / 服务 / 端口访问（SVC）

```bash
curl -X POST http://localhost:8080/vanilla/stack/api/v1/create \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"clusterId":1,"stackName":"web"}'
# 创建栈自动授予 stack_admin 并即时失效缓存，无需手动操作

# 服务表单声明容器端口 containerPorts（协议 + 端口）
curl -X POST http://localhost:8080/vanilla/service/api/v1/create \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"stackId":1,"serviceName":"nginx","image":"nginx:latest","replicas":2,"cpu":512,"memory":128,"containerPorts":[{"protocol":"tcp","port":80}],"envs":[{"name":"ENV_TEST","value":"hello"}]}'

# 端口访问（SVC）：引用容器端口并配置访问方式（空 serviceType = 自动）
curl -X POST http://localhost:8080/vanilla/port/api/v1/create \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"stackId":1,"serviceId":1,"protocol":"tcp","port":80,"serviceType":""}'
```

### 8.3 部署与状态

```bash
curl -X POST http://localhost:8080/vanilla/stack/api/v1/deploy \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"stackId":1}'
# => status RUNNING, nginx runningCount=2

docker ps   # vanilla-1-nginx-0 (0.0.0.0:80->80/tcp), vanilla-1-nginx-1 (0.0.0.0:81->80/tcp)
curl -I http://localhost:80   # HTTP 200
```

### 8.4 停止 → 重新部署 → 下架

```bash
curl -X POST .../stack/api/v1/stop   -d '{"stackId":1}'   # 容器 Exited
curl -X POST .../stack/api/v1/status -d '{"stackId":1}'   # STOPPED
curl -X POST .../stack/api/v1/deploy -d '{"stackId":1}'   # 幂等重建, RUNNING
curl -X POST .../stack/api/v1/remove -d '{"stackId":1}'   # 容器清空
curl -X POST .../stack/api/v1/status -d '{"stackId":1}'   # NONE
```

### 8.5 验证操作历史

```bash
curl -X POST http://localhost:8080/vanilla/history/api/v1/list \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"stackId":1,"page":1,"size":20}'
# 包含 创建栈/创建服务/创建端口访问/部署栈/停止栈/下架栈 等事件
```

## 9. K8s（Kubernetes）集群部署与验证

> 平台已实现按集群 `type = K8S` 的部署链路（Deployment / Service / PVC / 探针 / 日志），资源映射与状态语义有单元测试固化，**并已在本机 OrbStack Kubernetes 集群（API Server `https://127.0.0.1:26443`）完成真机 e2e**：部署 → 状态 → 健康 → 日志 → 停止 → 重新部署 → 下架，以及部署前 YAML 预览。以下为验证路径。

### 9.1 准备

- 一个可访问的 K8s 集群（API Server 地址，如 `https://<api-server>:6443`）。平台采用**一栈一命名空间**（命名空间 = 栈名）并自动创建命名空间，故账号需具备**创建命名空间**与部署权限；无该权限时，需管理员预先创建同名命名空间。
- 如需 TLS：`tlsVerify=true` 并在集群的 `dockerCertPath` 放置 `ca.crt` / `client.crt` / `client.key`（兼容 Docker 的 `ca.pem` / `cert.pem` / `key.pem` 命名）。

### 9.2 创建集群

```bash
curl -X POST http://localhost:8080/vanilla/cluster/api/v1/create \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"clusterName":"k8s-1","type":"K8S","endpoint":"https://127.0.0.1:6443","tlsVerify":true,"dockerCertPath":"/etc/vanilla/k8s-certs"}'
# 创建栈自动授予 stack_admin 并即时失效缓存，无需手动操作
```

### 9.3 部署与验证

创建栈/服务/端口/卷（同第 8 节），然后：

```bash
curl -X POST .../stack/api/v1/preview -d '{"stackId":1}'  # 部署预览：返回 Namespace/Deployment/Service/PVC 的 YAML
curl -X POST .../stack/api/v1/deploy -d '{"stackId":1}'   # 创建 Deployment + Service + PVC
curl -X POST .../stack/api/v1/status -d '{"stackId":1}'   # readyReplicas → RUNNING/PARTIAL；healthyCount
curl -X POST .../stack/api/v1/logs  \
  -d '{"stackId":1,"serviceId":1,"replicaIndex":0,"tail":200}'   # Pod 日志

kubectl -n <栈名> get deploy,svc,pvc   # 预期：资源名=服务名；Service(ClusterIP|NodePort) / PVC（命名空间=栈名）
# 声明端口 ≤ 2767 时宿主访问：NodePort = 30000 + 声明端口（如声明 80 → 30080）
```

停止（scale=0）、重新部署（createOrReplace 滚动）、下架（删 Deployment+Service、保留 PVC）、多副本、健康检查探针（服务配置 `healthCheckCmd`）均应可验证。

### 9.4 已知取舍（K8s）

| 项 | 说明 |
|---|---|
| CPU | Docker CPU shares 按 m 近似映射（1024 = 1 vCPU），非精确对应 |
| 宿主端口 | NodePort 受 30000–32767 限制，仅「声明端口 ≤ 2767」映射；多副本不再逐副本偏移（由集群分发） |
| HTTP 同步等待 | 部署接口不阻塞等待滚动完成，就绪状态由 status/healthyCount 轮询体现 |
| 命名空间 | **命名空间 = 栈名**（栈名集群内唯一，自动创建）；资源名 = 服务名/卷名，命名空间隔离保证跨栈不冲突 |

## 10. 常见问题

| 现象 | 原因/处理 |
|---|---|
| 部署报 `Bind for 0.0.0.0:X failed` | 宿主端口被占用（如后端占用 8080），或服务间端口范围重叠；改用空闲端口，或调整服务端口配置 |
| 部署报 `宿主端口 X 冲突` | 多副本端口偏移与其它服务端口重叠，部署前已拦截，调整端口即可 |
| K8s 部署报无权限 | 运行账号需能创建命名空间并在**栈命名空间**内创建 Deployment/Service/PVC（无权限时预先建好同名命名空间，见第 9 节） |
| `No Permission` | 权限不足：`admin` 之外的用户缺少对应集群/栈角色，或集群/栈创建后角色缓存尚未自动失效（正常情况下会即时失效，仅异常时考虑 `DEL USER_INFO_<username>`） |
| 镜像拉取超时 | Docker Hub 不可达，配置镜像加速（第 7 节） |
| 中文乱码 | 终端编码问题，API 本身返回 UTF-8 JSON |
| 更新接口响应字段为 null | MyBatis-Plus `updateById` 跳过 null 字段，**DB 数据不受影响**，仅响应 VO 显示问题 |

## 11. 构建产物与监控

- 应用：`target/vanilla-backend-0.0.1-SNAPSHOT.jar`（约 96MB，含 fabric8 K8s 客户端）
- 健康检查：`GET /vanilla/actuator/health`
- 接口文档：`/vanilla/doc.html`（Knife4j）
