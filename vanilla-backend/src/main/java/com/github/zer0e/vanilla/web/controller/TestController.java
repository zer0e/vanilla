package com.github.zer0e.vanilla.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/test/api/")
public class TestController {

    @RequestMapping("/v1/start")
    public String start() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
