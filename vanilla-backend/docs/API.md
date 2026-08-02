# Vanilla API 参考

服务基础地址：`http://<host>:8080/vanilla`，所有接口均返回 JSON。

## 通用约定

### 认证（JWT）

**1. 登录换取 token**（公开接口）：

```bash
curl -X POST http://localhost:8080/vanilla/auth/api/v1/login \
  -H "Content-Type: application/json" \
  -d '{"loginName":"admin","password":"admin123"}'
```

成功返回 `{token, loginName, nikeName}`，token 为 JWT（HS256，默认 12h 有效）。

**2. 受保护接口携带 token**：

```
Authorization: Bearer <token>
```

`JwtAuthenticationFilter` 解析 token 得到登录用户名，在 `@PreAuthorize` 鉴权前加载用户角色权限到 SecurityContext。

> 受保护接口一律使用 **JWT**（`Authorization: Bearer <token>`），登录用户名取自 token 的 subject，按 `t_user.login_name` 匹配。

未登录/无效 token 访问受保护接口返回：

```json
{"success":false,"code":401,"msg":"Unauthorized"}
```

无对应角色时返回：

```json
{"success":false,"code":403,"msg":"No Permission"}
```

> 用户信息会缓存在 Redis（`USER_INFO_<用户名>`，24h）。创建集群/栈、用户管理页变更角色后系统会自动失效对应缓存，新角色即时生效。

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
| endpoint | string | | Docker daemon / K8s API Server 地址，如 `unix:///var/run/docker.sock`、`https://127.0.0.1:6443` |
| tlsVerify | boolean | | 是否启用 TLS（默认 false） |
| dockerCertPath | string | | TLS 证书目录（服务器已有证书的场景，与下方上传三选一） |
| caCert | string | | CA 证书（PEM 文本，用户上传，**存库**） |
| clientCert | string | | 客户端证书（PEM 文本，存库） |
| clientKey | string | | 客户端私钥（PEM 文本，存库） |
| userIds | int[] | | 集群普通成员用户 id |

> TLS 证书优先使用数据库中的 `caCert/clientCert/clientKey`（前端上传），后端连接时自动落盘为临时文件，同时兼容 K8s 与 Docker 命名；列表/详情接口**不会返回**证书明文。

**请求**

```bash
curl -X POST http://localhost:8080/vanilla/cluster/api/v1/create \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
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

> **可见范围**：全局管理员/集群管理员（`cluster_admin`）看到该集群**全部**栈；`cluster_user` 等普通成员只看到自己有栈角色绑定的栈。

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
| volumeIds | int[] | | 引用的栈级卷 id 列表（卷在卷管理页维护，删除服务不影响卷；K8s PVC 名 = 卷名） |
| containerPorts | object[] | | **容器/Pod 暴露端口** `[{protocol("tcp"默认/"udp"), port}]`（对应 K8s containerPort / Docker EXPOSE） |

> 服务与 SVC **相互独立**：服务表单只声明容器监听的端口（containerPorts）；「端口访问」页在此之上创建 SVC 并配置访问方式。

### 3.2 修改服务 `POST /v1/update`

**角色**：`ROLE_stack_{stackId}_stack_admin`

请求体：`{id*, stackId*, image*, replicas?, command?, args?, cpu?, memory?, hostname?, terminationGracePeriodSeconds?, strategy?, envs?}`

> 注意 `image` 为必填校验字段。

### 3.3 删除服务 `POST /v1/delete`

**角色**：`ROLE_stack_{stackId}_stack_admin`。**物理删除**：连同该服务的端口/卷一起删除，并**释放栈内服务名**（唯一键），允许同名服务重新创建。

请求体：`{stackId*, serviceId*}`（注意是 `serviceId`）

### 3.4 服务列表 `POST /v1/list`

**角色**：`ROLE_stack_{stackId}_stack_admin` / `member` / `readonly`

请求体：`{stackId*, page?, size?, search?}`

响应中每个服务附带 `ports[]` 与 `volumes[]` 关联数据。

---

## 4. 端口 Port `/port/api`

### 4.1 创建端口 `POST /v1/create`

**角色**：`ROLE_stack_{stackId}_stack_admin` / `member`

请求体：`{stackId*, serviceId*, protocol?("tcp"默认/"udp"), port*, serviceType?}`

同一服务下端口不可重复。`port` 为该 SVC 引用的**容器端口**（见 3.1 `containerPorts`），`serviceType` 为访问方式（`ClusterIP` / `NodePort` / `LoadBalancer`，留空 **自动**：端口 ≤ 2767 映射 NodePort 30000+端口，否则 ClusterIP）。
**端口访问页负责创建 SVC**：选择服务（POD）后引用其已声明的容器端口，配置访问方式即可。**每个 SVC = 一个 K8s Service**（名 `{服务名}-{端口}`，targetPort=容器端口）；Docker 集群忽略 `serviceType`（宿主端口映射=容器端口+副本偏移）。删除 SVC 为物理删除，重部署会清理对应 Service。

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

> **卷是栈级独立资源**（不再挂靠服务），在独立页面维护；服务通过 `volumeIds` 引用并挂载。
> 删除服务**不影响卷**；删除卷会同步清理服务引用。删除为物理删除（释放栈内卷名）。

### 5.1 创建卷 `POST /v1/create`

**角色**：`ROLE_stack_{stackId}_stack_admin` / `member`

请求体：`{stackId*, volumeName*, size?, mountPath?}`（size 单位 GB；mountPath 为容器内挂载路径）

### 5.2 修改卷 `POST /v1/update`

**角色**：`ROLE_stack_{stackId}_stack_admin`

请求体：`{id*, stackId*, size?, mountPath?}`（卷名创建后不可改）

### 5.3 删除卷 `POST /v1/delete`

**角色**：`ROLE_stack_{stackId}_stack_admin`

请求体：`{stackId*, id*}`。物理删除并清理服务引用。

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

## 7. 用户管理 `/user/api`

**角色**：以下端点均需 `admin`。

### 7.1 创建用户 `POST /v1/create`

请求体：`{nikeName*, loginName*, status?, roles?}`，`roles` 为角色绑定数组：

| 字段 | 说明 |
|---|---|
| roleName | `admin` / `user`（全局）或 `cluster_admin` / `cluster_user`（需 clusterId）或 `stack_admin` / `stack_member` / `stack_readonly`（需 stackId） |
| clusterId | 集群角色必填 |
| stackId | 栈角色必填 |

```json
{"nikeName": "Dev One", "loginName": "dev1", "roles": [{"roleName": "user"}]}
```

### 7.2 修改用户 `POST /v1/update`

请求体：`{id*, nikeName?, status?, roles?}`。`roles` 为 `null` 时不修改角色，非 `null` 时**全量替换**。

### 7.3 删除用户 `POST /v1/delete`

请求体：`{id*}`。置 `status=1` 禁用并清空角色绑定，账号即刻失效（未授权访问返回 401）。

### 7.4 用户列表 `POST /v1/list`

请求体：`{page?, size?, search?}`，按登录名/昵称模糊搜索。响应含角色绑定列表。

---

## 8. 部署生命周期 `/stack/api`

### 8.1 部署栈 `POST /stack/api/v1/deploy`

**角色**：`ROLE_stack_{stackId}_stack_admin`

请求体：`{stackId*}`

流程：**宿主端口全局预校验（Docker）** → 逐服务「拉镜像 → 按更新策略创建/替换容器（Docker）或 Deployment/Service/PVC（K8s）」→ 清理孤儿资源。失败自动回滚清理。

> **健康检查**：服务可配置 `healthCheckCmd`（如 `curl -f http://localhost:8080/health || exit 1`）。Docker 侧容器挂载 HEALTHCHECK（间隔 30s / 超时 10s / 重试 3 / 启动宽限 5s），K8s 侧生成 readiness + liveness exec 探针；状态接口返回 `healthyCount`。

**更新策略**（`service.strategy`）：

| 策略 | 行为 |
|---|---|
| `Recreate`（默认） | 删除该服务旧容器后按副本数全量创建 |
| `RollingUpdate` | 逐副本「停旧 → 建新」，其余副本持续对外服务；副本数变化时退化为全量重建 |

> 部署不再整栈先删：同一栈内其他服务在重部署期间保持运行。

**响应**（`StackStatusVo`）：

```json
{
  "stackId": 1,
  "status": "RUNNING",
  "services": [
    {"serviceId": 1, "serviceName": "nginx", "status": "RUNNING", "runningCount": 2, "healthyCount": 2, "replicas": 2, "exposedAddresses": ["192.168.139.2:30080"]},
    {"serviceId": 2, "serviceName": "static", "status": "RUNNING", "runningCount": 1, "healthyCount": 1, "replicas": 1, "exposedAddresses": ["192.168.100.2:30081"]}
  ]
}
```

> `healthyCount`：服务配置了 `healthCheckCmd`（健康检查命令）时按容器健康状态统计——Docker 读取容器 HEALTHCHECK 状态、K8s 按 readyReplicas；未配置时等于 `runningCount`。

> `exposedAddresses`：部署后服务对外暴露的地址列表——K8s 按 Service 类型展示（NodePort → `节点IP:nodePort`、LoadBalancer → `externalIP:port`、ClusterIP → `clusterIP:port`），Docker 按容器端口绑定展示（`宿主IP:宿主端口`，未部署为空数组）。

**容器命名**：`vanilla-{stackId}-{serviceName}`（单副本）/ `vanilla-{stackId}-{serviceName}-{index}`（多副本）。

**端口映射**：宿主端口 = 声明端口 + 副本索引（多副本时 `80`、`81`、`82`...）。服务间端口范围重叠会在部署前直接报错。

**容器标签**：`com.vanilla.stack_id`、`com.vanilla.service_id`（用于状态统计与清理）。

### 8.2 查询状态 `POST /stack/api/v1/status`

**角色**：`stack_admin` / `member` / `readonly`

请求体：`{stackId*}`

**栈状态语义**：

| 状态 | 含义 |
|---|---|
| `RUNNING` | 所有服务全部容器运行 |
| `STOPPED` | 所有容器停止 |
| `PARTIAL` | 部分服务/副本运行 |
| `NONE` | 无任何容器 |

### 8.3 停止栈 `POST /stack/api/v1/stop`

**角色**：`stack_admin`。停止栈下所有容器（不删除）。

### 8.4 下架栈 `POST /stack/api/v1/remove`

**角色**：`stack_admin`。删除栈下所有容器（含停止的）。

### 8.5 查看容器日志 `POST /stack/api/v1/logs`

**角色**：`stack_admin` / `member` / `readonly`

请求体：

```json
{"stackId": 1, "serviceId": 1, "replicaIndex": 1, "tail": 500}
```

| 字段 | 说明 |
|---|---|
| `stackId`* | 栈 id |
| `serviceId`* | 服务 id |
| `replicaIndex` | 副本索引；缺省时单副本取唯一容器，多副本优先取**运行中的**副本 |
| `tail` | 日志行数，默认 500，范围 1~10000 |

**响应**（`ContainerLogVo`，日志为 stdout + stderr 合并文本）：

```json
{"containerId": "abc123", "containerName": "vanilla-1-nginx-1", "log": "GET / HTTP/1.1\" 200 ..."}
```

服务未部署（无容器）返回 `服务未部署，无可查看日志的容器`。

---

## 9. Swagger / OpenAPI

- UI：`http://localhost:8080/vanilla/doc.html`（Knife4j）
- OpenAPI JSON：`http://localhost:8080/vanilla/v3/api-docs`

以上路径已放行匿名访问（`permitAll`）。
