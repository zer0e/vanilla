package com.github.zer0e.vanilla.application;

import com.github.zer0e.vanilla.domain.UserRole;

import java.util.List;

public interface UserService {

    List<UserRole> getClusterUserRoles(Integer clusterId);
}
