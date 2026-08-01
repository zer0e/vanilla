package com.github.zer0e.vanilla;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("需要本地 MySQL(3306) 与 Redis(6379)，仅在有完整环境时运行")
class VanillaBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
