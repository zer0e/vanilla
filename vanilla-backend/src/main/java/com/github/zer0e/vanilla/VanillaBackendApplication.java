package com.github.zer0e.vanilla;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class VanillaBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(VanillaBackendApplication.class, args);
    }

}
