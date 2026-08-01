package com.github.zer0e.vanilla.infrastructure.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.zer0e.vanilla.common.Constants;
import com.github.zer0e.vanilla.common.exception.BusinessException;
import com.github.zer0e.vanilla.domain.ClusterType;
import com.github.zer0e.vanilla.domain.DataStatus;
import com.github.zer0e.vanilla.infrastructure.db.mapper.ClusterMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.ClusterDo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 按集群维护 DockerClient 连接缓存
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DockerClientFactory {

    private final ClusterMapper clusterMapper;

    private final ConcurrentHashMap<Integer, DockerClient> clients = new ConcurrentHashMap<>();

    /**
     * 获取集群对应的 DockerClient，不存在则创建
     */
    public DockerClient getClient(Integer clusterId) throws BusinessException {
        DockerClient client = clients.get(clusterId);
        if (client != null) {
            return client;
        }
        ClusterDo cluster = clusterMapper.selectById(clusterId);
        if (cluster == null || cluster.getStatus() != DataStatus.EXIST.ordinal()) {
            throw new BusinessException(Constants.CLUSTER_NOT_EXIST);
        }
        if (!ClusterType.DOCKER.name().equalsIgnoreCase(cluster.getType())) {
            throw new BusinessException(Constants.CLUSTER_TYPE_NOT_SUPPORT);
        }
        if (!StringUtils.hasText(cluster.getEndpoint())) {
            throw new BusinessException(Constants.CLUSTER_ENDPOINT_NOT_CONFIG);
        }
        DockerClient newClient = createClient(cluster);
        DockerClient existing = clients.putIfAbsent(clusterId, newClient);
        if (existing != null) {
            try {
                newClient.close();
            } catch (Exception e) {
                log.warn("close duplicate docker client err, clusterId={}", clusterId, e);
            }
            return existing;
        }
        return newClient;
    }

    /**
     * 集群信息变更后移除缓存
     */
    public void invalidate(Integer clusterId) {
        DockerClient old = clients.remove(clusterId);
        if (old != null) {
            try {
                old.close();
            } catch (Exception e) {
                log.warn("close docker client err, clusterId={}", clusterId, e);
            }
        }
    }

    private DockerClient createClient(ClusterDo cluster) {
        DefaultDockerClientConfig.Builder builder = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(cluster.getEndpoint())
                .withDockerTlsVerify(Boolean.TRUE.equals(cluster.getTlsVerify()));
        if (StringUtils.hasText(cluster.getDockerCertPath())) {
            builder.withDockerCertPath(cluster.getDockerCertPath());
        }
        DockerClientConfig config = builder.build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }
}
