package com.github.zer0e.vanilla.common;

public interface Constants {
    String NO_PERMISSION = "No Permission";
    String DEFAULT_CONTENT_TYPE = "application/json;charset=utf-8";

    String ROLE_PREFIX = "ROLE_";
    String USER_CACHE_PREFIX = "USER_INFO_";
    String CLUSTER_NOT_EXIST = "集群不存在";
    String ROLE_NOT_EXIST = "角色不存在";
    String USER_INFO_NOT_EXIST = "用户信息获取失败";
    String LOCK_PREFIX = "lock-";
    String UNDER_LINE = "_";
    String STACK_DUPLICATE = "栈名重复";
    String STACK_NOT_EXIST = "栈不存在";
    String SERVICE_DUPLICATE = "服务名重复";
    String SERVICE_NOT_EXIST = "服务不存在";
    String PORT_DUPLICATE = "端口已存在";
    String PORT_NOT_EXIST = "端口不存在";
    String VOLUME_DUPLICATE = "卷名重复";
    String VOLUME_NOT_EXIST = "卷不存在";
    String CLUSTER_TYPE_NOT_SUPPORT = "暂不支持该集群类型";
    String CLUSTER_ENDPOINT_NOT_CONFIG = "集群连接地址未配置";
    String DEPLOY_FAIL = "部署失败";
    String CONTAINER_NAME_PREFIX = "vanilla-";
    String STACK_ID_LABEL = "com.vanilla.stack_id";
    String SERVICE_ID_LABEL = "com.vanilla.service_id";

    int NUM_24 = 24;
    int NUM_60 = 60;
}
