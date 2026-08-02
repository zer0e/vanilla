package com.github.zer0e.vanilla.application.support;

import com.github.zer0e.vanilla.application.vo.ServiceStatusVo;

import java.util.List;

/**
 * 运行时状态映射（RUNNING / STOPPED / PARTIAL / NONE），Docker 与 K8s 共用
 */
public final class RuntimeStateResolver {

    private RuntimeStateResolver() {
    }

    public static String resolveStatus(int total, long running) {
        if (total == 0) {
            return "NONE";
        }
        if (running == total) {
            return "RUNNING";
        }
        if (running == 0) {
            return "STOPPED";
        }
        return "PARTIAL";
    }

    public static String resolveStackStatus(List<ServiceStatusVo> services) {
        if (services.isEmpty() || services.stream().allMatch(s -> "NONE".equals(s.getStatus()))) {
            return "NONE";
        }
        if (services.stream().allMatch(s -> "RUNNING".equals(s.getStatus()))) {
            return "RUNNING";
        }
        if (services.stream().allMatch(s -> "STOPPED".equals(s.getStatus()) || "NONE".equals(s.getStatus()))) {
            return "STOPPED";
        }
        return "PARTIAL";
    }
}