create table vanilla.t_cluster
(
    id          int auto_increment
        primary key,
    cluster_name        varchar(255) not null comment '集群名称',
    description varchar(255) null comment '集群描述',
    create_user varchar(255) not null comment '创建人',
    create_time datetime     null comment '创建时间',
    modify_time datetime     null comment '修改时间',
    modify_user varchar(255) null comment '修改人',
    delete_time datetime     null comment '删除时间',
    delete_user varchar(255) null comment '删除人',
    status      tinyint      not null comment '删除标记'
);

create table vanilla.t_permission
(
    id              int auto_increment
        primary key,
    permission_name varchar(255) not null,
    constraint t_permission_pk2
        unique (permission_name)
)
    comment '权限表';

create table vanilla.t_role
(
    id        int auto_increment
        primary key,
    role_name varchar(255) not null comment '角色名'
)
    comment '角色表';

create table vanilla.t_role_permission
(
    id            int auto_increment
        primary key,
    role_id       int not null,
    permission_id int not null
)
    comment '角色权限关联表';

create table vanilla.t_user
(
    id          int auto_increment
        primary key,
    nike_name   varchar(255) null,
    login_name  varchar(255) not null,
    create_time datetime     null,
    status      tinyint      not null comment '用户状态0正常1封禁',
    constraint t_user_pk
        unique (login_name)
);

create table vanilla.t_user_role
(
    id          int auto_increment
        primary key,
    user_id     int          not null comment '用户id',
    role_id     int          not null,
    stack_id    int          null,
    create_time datetime     null,
    create_user varchar(255) null
)
    comment '用户角色表';

