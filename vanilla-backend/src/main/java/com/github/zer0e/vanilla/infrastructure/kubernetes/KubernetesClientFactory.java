package com.github.zer0e.vanilla.infrastructure.kubernetes;

import com.github.zer0e.vanilla.common.Constants;
import com.github.zer0e.vanilla.common.exception.BusinessException;
import com.github.zer0e.vanilla.infrastructure.cert.ClusterCertMaterializer;
import com.github.zer0e.vanilla.infrastructure.db.repository.ClusterDo;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 按 clusterId 缓存 KubernetesClient。
 * 连接信息复用 t_cluster 的 endpoint（K8s API Server 地址）与证书目录（dockerCertPath）。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KubernetesClientFactory {

    private final ClusterCertMaterializer certMaterializer;

    private final ConcurrentMap<Integer, KubernetesClient> cache = new ConcurrentHashMap<>();

    public KubernetesClient getClient(ClusterDo cluster) throws BusinessException {
        KubernetesClient client = cache.get(cluster.getId());
        if (client != null) {
            return client;
        }
        synchronized (this) {
            client = cache.get(cluster.getId());
            if (client == null) {
                client = createClient(cluster);
                cache.put(cluster.getId(), client);
            }
        }
        return client;
    }

    /**
     * 集群信息变更/删除后调用：关闭并移除缓存的连接，下次操作重建
     */
    public void invalidate(Integer clusterId) {
        KubernetesClient removed = cache.remove(clusterId);
        if (removed != null) {
            removed.close();
        }
    }

    private KubernetesClient createClient(ClusterDo cluster) throws BusinessException {
        ConfigBuilder configBuilder = new ConfigBuilder()
                // 关闭 TLS 校验时 K8s 客户端同样信任自签证书
                .withMasterUrl(normalizeMasterUrl(cluster.getEndpoint()))
                .withTrustCerts(!Boolean.TRUE.equals(cluster.getTlsVerify()));
        // 优先使用数据库里用户上传的证书（materialize 落盘），否则回退 dockerCertPath 目录
        String certDir = certMaterializer.materialize(cluster);
        if (certDir != null) {
            File dir = new File(certDir);
            // 同时兼容 K8s（ca.crt/client.crt/client.key）与 Docker TLS（ca.pem/cert.pem/key.pem）命名
            configBuilder.withCaCertFile(filePath(dir, "ca.crt", "ca.pem"))
                    .withClientCertFile(filePath(dir, "client.crt", "cert.pem"))
                    .withClientKeyFile(filePath(dir, "client.key", "key.pem"));
        }
        return new KubernetesClientBuilder().withConfig(configBuilder.build()).build();
    }

    /**
     * 归一化 K8s API Server 地址：tcp:// 前缀按 https 处理，缺 scheme 时默认 https
     */
    private String normalizeMasterUrl(String endpoint) throws BusinessException {
        if (!StringUtils.hasText(endpoint)) {
            throw new BusinessException(Constants.CLUSTER_ENDPOINT_NOT_CONFIG);
        }
        String url = endpoint.trim();
        if (url.startsWith("tcp://")) {
            url = "https://" + url.substring("tcp://".length());
        } else if (!url.startsWith("https://") && !url.startsWith("http://")) {
            url = "https://" + url;
        }
        return url;
    }

    private String filePath(File dir, String... names) {
        for (String name : names) {
            File file = new File(dir, name);
            if (file.isFile()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }
}