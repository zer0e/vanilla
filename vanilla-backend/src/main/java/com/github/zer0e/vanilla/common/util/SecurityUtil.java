package com.github.zer0e.vanilla.common.util;

import com.github.zer0e.vanilla.domain.User;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {

    public static String getCurrentUserName() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        }catch (Exception e) {
            return null;
        }
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
