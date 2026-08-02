-- Vanilla 数据库建表脚本（与 DO 实体字段对齐，含软删除 Base 字段）
-- 已在 MySQL 8.0 上经端到端部署验证

CREATE DATABASE IF NOT EXISTS vanilla DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE vanilla;

-- 集群
create table vanilla.t_cluster
(
    id               int auto_increment primary key,
    cluster_name     varchar(255) not null comment '集群名称',
    description      varchar(255) null comment '集群描述',
    type             varchar(32)  default 'DOCKER' comment '集群类型 DOCKER/K8S',
    endpoint         varchar(255) null comment 'Docker daemon 连接地址',
    tls_verify       tinyint      default 0 comment '是否启用 TLS 校验',
    docker_cert_path varchar(255) null comment 'Docker TLS 证书目录',
    ca_cert          longtext     null comment 'CA 证书（PEM，上传存库）',
    client_cert      longtext     null comment '客户端证书（PEM）',
    client_key       longtext     null comment '客户端私钥（PEM）',
    create_user      varchar(255) null,
    create_time      datetime     null,
    modify_time      datetime     null,
    modify_user      varchar(255) null,
    delete_time      datetime     null,
    delete_user      varchar(255) null,
    status           tinyint      not null default 0 comment '删除标记 0正常 1删除'
) comment '集群表';

-- 栈
create table vanilla.t_stack
(
    id          int auto_increment primary key,
    cluster_id  int          not null comment '所属集群',
    stack_name  varchar(255) not null comment '栈名称',
    description varchar(255) null,
    owner       varchar(255) null comment '负责人',
    create_user varchar(255) null,
    create_time datetime     null,
    modify_time datetime     null,
    modify_user varchar(255) null,
    delete_time datetime     null,
    delete_user varchar(255) null,
    status      tinyint      not null default 0,
    unique key uk_cluster_stack (cluster_id, stack_name)
) comment '栈表';

-- 服务
create table vanilla.t_service
(
    id                               int auto_increment primary key,
    stack_id                         int          not null,
    service_name                     varchar(255) not null,
    image                            varchar(255) null comment '镜像',
    replicas                         int          default 1,
    command                          varchar(255) null,
    args                             varchar(255) null,
    cpu                              int          null comment 'CPU shares',
    memory                           int          null comment '内存 MB',
    hostname                         varchar(255) null,
    termination_grace_period_seconds varchar(64)  null,
    health_check_cmd                 varchar(255) null comment '健康检查命令，如 curl -f http://localhost/health || exit 1',
    strategy                         varchar(64)  null,
    service_type                     varchar(64)  null comment 'K8s Service 类型：ClusterIP/NodePort/LoadBalancer，空=自动',
    envs                             json         null comment '环境变量',
    create_user                      varchar(255) null,
    create_time                      datetime     null,
    modify_time                      datetime     null,
    modify_user                      varchar(255) null,
    delete_time                      datetime     null,
    delete_user                      varchar(255) null,
    status                           tinyint      not null default 0,
    unique key uk_stack_service (stack_id, service_name)
) comment '服务表';

-- 端口
create table vanilla.t_port
(
    id          int auto_increment primary key,
    service_id  int          not null,
    stack_id    int          not null,
    protocol    varchar(8)   default 'tcp',
    port        int          not null,
    create_user varchar(255) null,
    create_time datetime     null,
    modify_time datetime     null,
    modify_user varchar(255) null,
    delete_time datetime     null,
    delete_user varchar(255) null,
    status      tinyint      not null default 0,
    unique key uk_service_port (service_id, port)
) comment '端口表';

-- 卷（service 级，部署时挂载到容器）
create table vanilla.t_volume
(
    id          int auto_increment primary key,
    stack_id    int          not null,
    service_id  int          not null comment '所属服务',
    volume_name varchar(255) not null comment '卷名称',
    size        int          null comment '卷大小 GB',
    mount_path  varchar(255) null comment '容器内挂载路径',
    create_user varchar(255) null,
    create_time datetime     null,
    modify_time datetime     null,
    modify_user varchar(255) null,
    delete_time datetime     null,
    delete_user varchar(255) null,
    status      tinyint      not null default 0,
    unique key uk_service_volume (service_id, volume_name)
) comment '卷表';

-- 用户
create table vanilla.t_user
(
    id          int auto_increment primary key,
    nike_name   varchar(255) null,
    login_name  varchar(255) not null,
    password    varchar(255) null comment '登录密码（BCrypt 哈希）',
    create_time datetime     null,
    status      tinyint      not null comment '用户状态0正常1封禁',
    constraint t_user_pk unique (login_name)
) comment '用户表';

-- 角色
create table vanilla.t_role
(
    id        int auto_increment primary key,
    role_name varchar(255) not null comment '角色名',
    unique key uk_role_name (role_name)
) comment '角色表';

-- 权限
create table vanilla.t_permission
(
    id              int auto_increment primary key,
    permission_name varchar(255) not null,
    constraint t_permission_pk2 unique (permission_name)
) comment '权限表';

-- 角色权限关联
create table vanilla.t_role_permission
(
    id            int auto_increment primary key,
    role_id       int not null,
    permission_id int not null
) comment '角色权限关联表';

-- 用户角色关联（继承 Base，含软删除字段）
create table vanilla.t_user_role
(
    id          int auto_increment primary key,
    user_id     int          not null comment '用户id',
    role_id     int          not null,
    stack_id    int          null,
    cluster_id  int          null,
    create_time datetime     null,
    create_user varchar(255) null,
    modify_time datetime     null,
    modify_user varchar(255) null,
    delete_time datetime     null,
    delete_user varchar(255) null,
    status      tinyint      not null default 0
) comment '用户角色表';

-- 操作历史
create table vanilla.t_operation_history
(
    id          int auto_increment primary key,
    stack_id    int          not null,
    event       varchar(500) null comment '操作事件',
    create_user varchar(255) null,
    create_time datetime     null
) comment '操作历史表';

-- 基础角色
insert into vanilla.t_role (role_name) values
    ('admin'), ('user'),
    ('cluster_admin'), ('cluster_user'),
    ('stack_admin'), ('stack_member'), ('stack_readonly')
    on duplicate key update role_name = values(role_name);

-- 管理员账号（初始密码 admin123，登录后请尽快修改）
insert into vanilla.t_user (nike_name, login_name, password, create_time, status)
values ('Administrator', 'admin',
        '$2a$10$FhdXj62bhe2bqtr/47dzK.vQRWEbMBtjvP0di7gEpM.z5dn3Ya7Wq', now(), 0)
    on duplicate key update nike_name = values(nike_name);

-- admin 用户绑定 admin 角色
insert into vanilla.t_user_role (user_id, role_id, create_time, create_user)
select u.id, r.id, now(), 'seed'
from vanilla.t_user u, vanilla.t_role r
where u.login_name = 'admin' and r.role_name = 'admin'
  and not exists (select 1 from vanilla.t_user_role ur
                  where ur.user_id = u.id and ur.role_id = r.id);
