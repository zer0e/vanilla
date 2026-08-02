package com.github.zer0e.vanilla.common.util;

import com.github.zer0e.vanilla.common.Constants;
import com.github.zer0e.vanilla.domain.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {

    public static String getCurrentUserName() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        }catch (Exception e) {
            return null;
        }
    }

    /**
     * 判断当前登录用户是否具备指定 authority（如 ROLE_admin / ROLE_cluster_1_cluster_admin）
     */
    public static boolean hasRole(String role) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getAuthorities() == null) {
                return false;
            }
            return authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(role::equals);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isGlobalAdmin() {
        return hasRole(Constants.ROLE_PREFIX + "admin");
    }

    public static boolean isClusterAdmin(Integer clusterId) {
        return hasRole(Constants.ROLE_PREFIX + "cluster_" + clusterId + "_cluster_admin");
    }

    public static User getCurrentUser() {
        try {
            return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        } catch (Exception e) {
            return null;
        }
    }

    public static Integer getCurrentUserId() {
        try {
            User details = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return details.getId();
        }catch (Exception e) {
            return null;
        }
    }
}
