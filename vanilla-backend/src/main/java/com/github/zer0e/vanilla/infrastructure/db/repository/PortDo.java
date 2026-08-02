package com.github.zer0e.vanilla.infrastructure.db.repository;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_port")
public class PortDo extends Base {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer serviceId;

    private Integer stackId;

    private String protocol;

    private Integer port;

    /**
     * K8s Service 类型：ClusterIP / NodePort / LoadBalancer；空 = 自动（每个端口对应一个 SVC）
     */
    private String serviceType;
}
