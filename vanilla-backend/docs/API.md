# Vanilla API 参考

服务基础地址：`http://<host>:8080/vanilla`，所有接口均返回 JSON。

## 通用约定

### 认证

请求头携带登录用户名：

```
x-auth-user: admin
```

`XAuthUserFilter` 据此加载用户及其角色权限。未登录访问受保护接口返回：

```json
{"success":false,"code":401,"msg":"Unauthorized"}
```

无对应角色时返回：

```json
{"success":false,"code":403,"msg":"No Permission"}
```

> 用户信息会缓存在 Redis（`USER_INFO_<用户名>`，24h）。创建集群/栈后角色会变化，需清除缓存才能生效新角色。

### 统一响应结构

```json
{
  "success": true,      // 是否成功
  "code": 0,            // 业务码：0=成功；401=未登录；403=无权限；500=业务错误
  "msg": null,          // 错误信息
  "data": {}            // 业务数据
}
```

### 分页

列表接口使用 `page`（默认 1）与 `size`（默认 15），返回 `PageData`：

```json
{
  "page": 1,
  "size": 15,
  "count": 42,
  "data": []
}
```

### 常见错误信息

| 错误信息 | 说明 |
|---|---|
| `集群不存在` | clusterId 无效或已删除 |
| `栈不存在` / `栈名重复` | 栈相关 |
| `服务不存在` / `服务名重复` | 服务相关 |
| `端口已存在` / `端口不存在` | 端口相关 |
| `卷名重复` / `卷不存在` | 卷相关 |
| `暂不支持该集群类型` | 非 DOCKER 类型 |
| `集群连接地址未配置` | endpoint 为空 |
| `部署失败：<原因>` | 部署异常（含镜像拉取、端口冲突等） |
| `宿主端口 X 冲突：...` | 部署前端口预校验失败 |

---

## 1. 集群 Cluster `/cluster/api`

### 1.1 创建集群 `POST /v1/create`

**角色**：`admin`

请求体：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| clusterName | string | ✅ | 集群名称 |
| description | string | | 描述 |
| type | string | | 集群类型 `DOCKER`（默认）/ `K8S` |
| endpoint | string | | Docker daemon 地址，如 `unix:///var/run/docker.sock`、`tcp://192.168.1.100:2375` |
| tlsVerify | boolean | | 是否启用 TLS（默认 false） |
| dockerCertPath | string | | TLS 证书目录 |
| userIds | int[] | | 集群普通成员用户 id |

**请求**

```bash
curl -X POST http://localhost:8080/vanilla/cluster/api/v1/create \
  -H "Content-Type: application/json" -H "x-auth-user: admin" \
  -d '{"clusterName":"docker-1","type":"DOCKER","endpoint":"unix:///var/run/docker.sock","tlsVerify":false}'
```

**响应**

```json
{
  "success": true, "code": 0, "msg": null,
  "data": {
    "id": 1, "clusterName": "docker-1", "type": "DOCKER",
    "endpoint": "unix:///var/run/docker.sock", "tlsVerify": false,
    "dockerCertPath": null, "description": null,
    "createUser": "admin", "createTime": "2026-08-01T20:38:50"
  }
}
```

> 创建后创建者自动获得 `ROLE_cluster_{id}_cluster_admin`。

### 1.2 修改集群 `POST /v1/update`

**角色**：`admin`。除 `id` 外均为可选（部分更新）。

请求体：`{id, clusterName?, description?, type?, endpoint?, tlsVerify?, dockerCertPath?, userIds?}`

### 1.3 删除集群 `POST /v1/delete`

**角色**：`admin`。软删除。请求体：`{id}`。

### 1.4 集群列表 `GET /v1/list`

**角色**：任意已登录用户。返回当前用户有权限的集群列表（`List<ClusterVo>`），无需请求体。

---

## 2. 栈 Stack `/stack/api`

### 2.1 创建栈 `POST /v1/create`

**角色**：`ROLE_cluster_{clusterId}_cluster_admin` 或 `ROLE_cluster_{clusterId}_cluster_user`

请求体：`{clusterId*, stackName*, description?}`

创建成功后创建者自动获得 `ROLE_stack_{id}_stack_admin`。

### 2.2 修改 / 改名栈 `POST /v1/update`

**角色**：`ROLE_stack_{id}_stack_admin`

请求体：`{id*, stackName?, description?}`。改名时会校验同集群重名。

### 2.3 删除栈 `POST /v1/delete`

**角色**：`ROLE_stack_{id}_stack_admin`。软删除。请求体：`{id}`。

### 2.4 栈列表 `POST /v1/list`

**角色**：`ROLE_cluster_{clusterId}_cluster_admin` / `cluster_user`

请求体：`{clusterId*, page?, size?, search?}`，按栈名模糊搜索。

---

## 3. 服务 Service `/service/api`

### 3.1 创建服务 `POST /v1/create`

**角色**：`ROLE_stack_{stackId}_stack_admin` / `stack_member`

请求体：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| stackId | int | ✅ | 所属栈 |
| serviceName | string | ✅ | 服务名称（栈内唯一） |
| image | string | ✅ | 镜像，如 `nginx:latest` |
| replicas | int | | 副本数（默认 1） |
| command | string | | 启动命令（按空白拆分） |
| args | string | | 命令参数 |
| cpu | int | | CPU shares |
| memory | int | | 内存（MB），部署时 ×1MB |
| hostname | string | | 容器主机名 |
| terminationGracePeriodSeconds | string | | 停止宽限期 |
| strategy | string | | 更新策略 |
| envs | object[] | | 环境变量 `[{name, value}]`，JSON 列存储 |

### 3.2 修改服务 `POST /v1/update`

**角色**：`ROLE_stack_{stackId}_stack_admin`

请求体：`{id*, stackId*, image*, replicas?, command?, args?, cpu?, memory?, hostname?, terminationGracePeriodSeconds?, strategy?, envs?}`

> 注意 `image` 为必填校验字段。

### 3.3 删除服务 `POST /v1/delete`

**角色**：`ROLE_stack_{stackId}_stack_admin`。软删除。

请求体：`{stackId*, serviceId*}`（注意是 `serviceId`）

### 3.4 服务列表 `POST /v1/list`

**角色**：`ROLE_stack_{stackId}_stack_admin` / `member` / `readonly`

请求体：`{stackId*, page?, size?, search?}`

响应中每个服务附带 `ports[]` 与 `volumes[]` 关联数据。

---

## 4. 端口 Port `/port/api`

### 4.1 创建端口 `POST /v1/create`

**角色**：`ROLE_stack_{stackId}_stack_admin` / `member`

请求体：`{stackId*, serviceId*, protocol?("tcp"默认/"udp"), port*}`

同一服务下端口不可重复。

### 4.2 修改端口 `POST /v1/update`

**角色**：`ROLE_stack_{stackId}_stack_admin`

请求体：`{id*, stackId*, serviceId*, protocol?, port*}`

### 4.3 删除端口 `POST /v1/delete`

**角色**：`ROLE_stack_{stackId}_stack_admin`

请求体：`{id*, stackId*, serviceId*}`

### 4.4 端口列表 `POST /v1/list`

**角色**：`ROLE_stack_{stackId}_stack_admin` / `member` / `readonly`

请求体：`{stackId*, serviceId*, page?, size?}`

---

## 5. 卷 Volume `/volume/api`

### 5.1 创建卷 `POST /v1/create`

**角色**：`ROLE_stack_{stackId}_stack_admin` / `member`

请求体：`{stackId*, volumeName*, size?}`（size 单位 GB）

### 5.2 修改卷 `POST /v1/update`

**角色**：`ROLE_stack_{stackId}_stack_admin`

请求体：`{id*, stackId*, size?}`

### 5.3 删除卷 `POST /v1/delete`

**角色**：`ROLE_stack_{stackId}_stack_admin`

请求体：`{stackId*, id*}`

### 5.4 卷列表 `POST /v1/list`

**角色**：`ROLE_stack_{stackId}_stack_admin` / `member` / `readonly`

请求体：`{stackId*, page?, size?, search?}`

---

## 6. 操作历史 History `/history/api`

### 6.1 历史列表 `POST /v1/list`

**角色**：`ROLE_stack_{stackId}_stack_admin` / `member` / `readonly`

请求体：`{stackId*, page?, size?}`

响应 `data[]` 为 `OperationHistoryDo`，按 `createTime` 倒序：

```json
{
  "id": 8,
  "stackId": 1,
  "event": "部署栈 web",
  "createUser": "admin",
  "createTime": "2026-08-01T20:48:05"
}
```

---

## 7. 部署生命周期 `/stack/api`

### 7.1 部署栈 `POST /stack/api/v1/deploy`

**角色**：`ROLE_stack_{stackId}_stack_admin`

请求体：`{stackId*}`

流程：清空同栈旧容器 → **宿主端口全局预校验** → 逐服务「拉镜像 → 按副本创建并启动容器」。失败自动回滚清理。

**响应**（`StackStatusVo`）：

```json
{
  "stackId": 1,
  "status": "RUNNING",
  "services": [
    {"serviceId": 1, "serviceName": "nginx", "status": "RUNNING", "runningCount": 2, "replicas": 2},
    {"serviceId": 2, "serviceName": "static", "status": "RUNNING", "runningCount": 1, "replicas": 1}
  ]
}
```

**容器命名**：`vanilla-{stackId}-{serviceName}`（单副本）/ `vanilla-{stackId}-{serviceName}-{index}`（多副本）。

**端口映射**：宿主端口 = 声明端口 + 副本索引（多副本时 `80`、`81`、`82`...）。服务间端口范围重叠会在部署前直接报错。

**容器标签**：`com.vanilla.stack_id`、`com.vanilla.service_id`（用于状态统计与清理）。

### 7.2 查询状态 `POST /stack/api/v1/status`

**角色**：`stack_admin` / `member` / `readonly`

请求体：`{stackId*}`

**栈状态语义**：

| 状态 | 含义 |
|---|---|
| `RUNNING` | 所有服务全部容器运行 |
| `STOPPED` | 所有容器停止 |
| `PARTIAL` | 部分服务/副本运行 |
| `NONE` | 无任何容器 |

### 7.3 停止栈 `POST /stack/api/v1/stop`

**角色**：`stack_admin`。停止栈下所有容器（不删除）。

### 7.4 下架栈 `POST /stack/api/v1/remove`

**角色**：`stack_admin`。删除栈下所有容器（含停止的）。

---

## 8. 测试接口 `/test/api/`

| 路径 | 角色 | 说明 |
|---|---|---|
| `POST /v1/start` | `user` | 返回当前登录用户名 |
| `POST /v1/error` | `user` | 触发异常，验证全局异常处理 |
| `POST /v1/test` | 已登录 | 读取 `admin` 角色名 |

---

## 9. Swagger / OpenAPI

- UI：`http://localhost:8080/vanilla/doc.html`（Knife4j）
- OpenAPI JSON：`http://localhost:8080/vanilla/v3/api-docs`

以上路径已放行匿名访问（`permitAll`）。
