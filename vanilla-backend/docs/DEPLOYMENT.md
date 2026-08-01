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

- **11 张表**：`t_cluster`、`t_stack`、`t_service`、`t_port`、`t_volume`、`t_user`、`t_role`、`t_permission`、`t_role_permission`、`t_user_role`、`t_operation_history`
- **7 个角色**：`admin`、`user`、`cluster_admin`、`cluster_user`、`stack_admin`、`stack_member`、`stack_readonly`
- **管理员账号**：`admin`（login_name，绑定 `admin` 角色）

> 表结构含软删除审计字段与唯一约束（如 `t_stack.uk_cluster_stack` 永久占用栈名），已与 DO 实体对齐。

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

以下流程已在云主机完整跑通。认证均使用 `x-auth-user: admin`。

### 8.1 冒烟 + 创建集群

```bash
# 创建 DOCKER 集群，连接本机 docker daemon
curl -X POST http://localhost:8080/vanilla/cluster/api/v1/create \
  -H "Content-Type: application/json" -H "x-auth-user: admin" \
  -d '{"clusterName":"docker-test","type":"DOCKER","endpoint":"unix:///var/run/docker.sock","tlsVerify":false}'

# 关键：刷新用户缓存，使新授予的 cluster_admin 生效
docker exec vanilla-redis redis-cli DEL USER_INFO_admin
```

### 8.2 创建栈 / 服务 / 端口

```bash
curl -X POST http://localhost:8080/vanilla/stack/api/v1/create \
  -H "Content-Type: application/json" -H "x-auth-user: admin" \
  -d '{"clusterId":1,"stackName":"web"}'
docker exec vanilla-redis redis-cli DEL USER_INFO_admin   # 获取 stack_admin

curl -X POST http://localhost:8080/vanilla/service/api/v1/create \
  -H "Content-Type: application/json" -H "x-auth-user: admin" \
  -d '{"stackId":1,"serviceName":"nginx","image":"nginx:latest","replicas":2,"cpu":512,"memory":128,"envs":[{"name":"ENV_TEST","value":"hello"}]}'

curl -X POST http://localhost:8080/vanilla/port/api/v1/create \
  -H "Content-Type: application/json" -H "x-auth-user: admin" \
  -d '{"stackId":1,"serviceId":1,"protocol":"tcp","port":80}'
```

### 8.3 部署与状态

```bash
curl -X POST http://localhost:8080/vanilla/stack/api/v1/deploy \
  -H "Content-Type: application/json" -H "x-auth-user: admin" -d '{"stackId":1}'
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
  -H "Content-Type: application/json" -H "x-auth-user: admin" \
  -d '{"stackId":1,"page":1,"size":20}'
# 包含 创建栈/创建服务/添加端口/部署栈/停止栈/下架栈 等事件
```

## 9. 常见问题

| 现象 | 原因/处理 |
|---|---|
| 部署报 `Bind for 0.0.0.0:X failed` | 宿主端口被占用（如后端占用 8080），或服务间端口范围重叠；改用空闲端口，或调整服务端口配置 |
| 部署报 `宿主端口 X 冲突` | 多副本端口偏移与其它服务端口重叠，部署前已拦截，调整端口即可 |
| `No Permission` | 权限不足；创建集群/栈后记得 `DEL USER_INFO_<username>` 刷新角色缓存 |
| 镜像拉取超时 | Docker Hub 不可达，配置镜像加速（第 7 节） |
| 中文乱码 | 终端编码问题，API 本身返回 UTF-8 JSON |
| 更新接口响应字段为 null | MyBatis-Plus `updateById` 跳过 null 字段，**DB 数据不受影响**，仅响应 VO 显示问题 |

## 10. 构建产物与监控

- 应用：`target/vanilla-backend-0.0.1-SNAPSHOT.jar`（约 80MB）
- 健康检查：`GET /vanilla/actuator/health`
- 接口文档：`/vanilla/doc.html`（Knife4j）
