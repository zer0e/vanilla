# Vanilla 前端

Vanilla 服务部署平台的前端，提供**集群 → 栈 → 服务**三级导航的操作界面：登录鉴权（JWT）、集群管理、栈管理、服务与端口访问（SVC）、卷、操作历史与用户/权限管理（仅 admin）。

技术栈：**Vue 3 · Vite · Vue Router · Pinia · Element Plus · Axios**。

## 页面结构

| 路由 | 页面 | 说明 |
|---|---|---|
| `/login` | 登录 | 用户名 + 密码换取 JWT，存 Pinia + localStorage |
| `/clusters` | 集群管理 | 集群 CRUD（Docker/K8s、endpoint、TLS 证书上传存库）、成员选择；**集群为中心导航** |
| `/clusters/:clusterId/stacks` | 栈列表 | 集群下栈的管理/部署入口；按权限可见（管理员全量，成员仅绑定栈） |
| `/clusters/:clusterId/stacks/:stackId` | 栈详情 | 服务管理（容器端口声明）、**端口访问（SVC）**、卷、操作历史；部署前**预览 K8s 资源 YAML** |
| `/users` | 用户管理 | 用户 CRUD + 角色绑定（全局/集群/栈），仅 admin 在顶部菜单可见 |

## 目录结构

```
vanilla-frontend/
├── src/
│   ├── api/          # Axios 请求封装（auth/cluster/stack/service/port/volume/history/user）
│   │   └── http.js   # 实例 + 请求/响应拦截器（附 JWT、统一错误提示）
│   ├── layouts/      # 默认布局（侧边栏上下文信息 + 顶部用户菜单）
│   ├── router/       # 路由与登录守卫
│   ├── stores/       # Pinia（auth：用户名/token）
│   ├── utils/        # 工具函数
│   └── views/        # 页面组件（Login/Clusters/Stacks/StackDetail/Users）
├── vite.config.js    # 别名 @、dev 代理、分包优化
└── package.json
```

## 本地开发

要求 Node.js 18+。

```bash
npm install
npm run dev
```

默认 `http://localhost:5173`。开发环境已配置 Vite 代理：`/vanilla` 开头请求转发到 `http://localhost:8080`（后端），**无需处理跨域**。后端启动方式见 [vanilla-backend/README.md](../vanilla-backend/README.md)。

用 `admin / admin123` 登录（登录后请修改密码）。

## 构建与部署

```bash
npm run build   # 产物 dist/
```

产物为纯静态文件，部署时用 Nginx 托管并将 `/vanilla` 接口反代到后端，例如：

```nginx
location / {
    root /opt/vanilla/dist;
    try_files $uri $uri/ /index.html;
}
location /vanilla/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
```

## 相关文档

- 后端 README / API / 架构 / 部署：[vanilla-backend](../vanilla-backend/)
- 项目总览：[根 README](../README.md)