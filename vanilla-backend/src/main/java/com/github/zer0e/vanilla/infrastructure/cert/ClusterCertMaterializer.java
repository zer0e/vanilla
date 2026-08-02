package com.github.zer0e.vanilla.infrastructure.cert;

import com.github.zer0e.vanilla.infrastructure.db.repository.ClusterDo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将集群存储于数据库的 PEM 证书落盘为临时文件，供 docker-java / fabric8 客户端使用。
 *
 * <p>同时写两套命名：K8s 命名（ca.crt / client.crt / client.key）与 Docker 命名
 * （ca.pem / cert.pem / key.pem），一份上传内容两个运行时通用。
 * 优先使用 DB 证书内容；无 DB 证书时回退集群配置的 dockerCertPath。
 */
@Component
@Slf4j
public class ClusterCertMaterializer {

    /**
     * @param cluster 集群实体
     * @return 证书目录：DB 证书落盘的临时目录，或集群配置的 dockerCertPath；两者皆无返回 null
     */
    public String materialize(ClusterDo cluster) {
        if (cluster == null) {
            return null;
        }
        if (!hasDbCerts(cluster)) {
            return StringUtils.hasText(cluster.getDockerCertPath()) ? cluster.getDockerCertPath() : null;
        }
        try {
            Path dir = Files.createTempDirectory("vanilla-cluster-" + cluster.getId() + "-");
            Map<String, String> contents = new LinkedHashMap<>();
            contents.put("ca.crt", cluster.getCaCert());
            contents.put("client.crt", cluster.getClientCert());
            contents.put("client.key", cluster.getClientKey());
            for (Map.Entry<String, String> entry : contents.entrySet()) {
                if (StringUtils.hasText(entry.getValue())) {
                    Files.writeString(dir.resolve(entry.getKey()), entry.getValue(), StandardCharsets.UTF_8);
                }
            }
            // Docker 命名副本（docker-java withDockerCertPath 要求 ca.pem/cert.pem/key.pem）
            copyIfPresent(dir, "ca.crt", "ca.pem");
            copyIfPresent(dir, "client.crt", "cert.pem");
            copyIfPresent(dir, "client.key", "key.pem");
            return dir.toString();
        } catch (IOException e) {
            log.error("materialize cluster cert err, clusterId={}", cluster.getId(), e);
            return StringUtils.hasText(cluster.getDockerCertPath()) ? cluster.getDockerCertPath() : null;
        }
    }

    private boolean hasDbCerts(ClusterDo cluster) {
        return StringUtils.hasText(cluster.getCaCert())
                || StringUtils.hasText(cluster.getClientCert())
                || StringUtils.hasText(cluster.getClientKey());
    }

    private void copyIfPresent(Path dir, String from, String to) throws IOException {
        Path source = dir.resolve(from);
        if (Files.exists(source)) {
            Files.copy(source, dir.resolve(to));
        }
    }
}