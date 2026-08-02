package com.github.zer0e.vanilla.infrastructure.cert;

import com.github.zer0e.vanilla.infrastructure.db.repository.ClusterDo;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB 证书 → 临时目录落盘：K8s 与 Docker 两套命名、回退 dockerCertPath、无证书返回 null
 */
class ClusterCertMaterializerTest {

    private final ClusterCertMaterializer materializer = new ClusterCertMaterializer();

    @Test
    void materialize_writesBothNamingSchemesForDbCerts() throws Exception {
        ClusterDo cluster = ClusterDo.builder().id(1)
                .caCert("-----BEGIN CA-----\nxxx\n-----END CA-----")
                .clientCert("-----BEGIN CERT-----\nyyy\n-----END CERT-----")
                .clientKey("-----BEGIN PRIVATE KEY-----\nzzz\n-----END PRIVATE KEY-----")
                .build();

        String dir = materializer.materialize(cluster);

        assertThat(dir).isNotBlank();
        Path p = Path.of(dir);
        // K8s 命名
        assertThat(Files.readString(p.resolve("ca.crt"))).contains("BEGIN CA");
        assertThat(Files.readString(p.resolve("client.crt"))).contains("BEGIN CERT");
        assertThat(Files.readString(p.resolve("client.key"))).contains("PRIVATE KEY");
        // Docker 命名（docker-java withDockerCertPath 要求）
        assertThat(Files.readString(p.resolve("ca.pem"))).contains("BEGIN CA");
        assertThat(Files.readString(p.resolve("cert.pem"))).contains("BEGIN CERT");
        assertThat(Files.readString(p.resolve("key.pem"))).contains("PRIVATE KEY");
    }

    @Test
    void materialize_noDbCerts_fallsBackToDockerCertPath() {
        ClusterDo cluster = ClusterDo.builder().id(1).dockerCertPath("/etc/vanilla/certs").build();

        assertThat(materializer.materialize(cluster)).isEqualTo("/etc/vanilla/certs");
    }

    @Test
    void materialize_nothing_returnsNull() {
        assertThat(materializer.materialize(ClusterDo.builder().id(1).build())).isNull();
    }
}