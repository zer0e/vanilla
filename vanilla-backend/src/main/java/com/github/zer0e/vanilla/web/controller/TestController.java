package com.github.zer0e.vanilla.web.controller;

import com.github.zer0e.vanilla.application.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/test/api/")
@RequiredArgsConstructor
public class TestController {

    private final UserService userService;

    @RequestMapping("/v1/start")
    @PreAuthorize("hasRole('user')")
    public String start() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @RequestMapping("/v1/error")
    @PreAuthorize("hasRole('user')")
    public String error() {
        throw new RuntimeException();
    }
    @RequestMapping("/v1/test")
    public String test() {
       return userService.getRoleByName("admin").getRoleName();
    }
}
