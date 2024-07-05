package com.github.zer0e.vanilla.application;

import com.github.zer0e.vanilla.infrastructure.db.repository.RoleDo;
import com.github.zer0e.vanilla.infrastructure.db.repository.UserRoleDo;

import java.util.List;

public interface UserService {

    List<UserRoleDo> getClusterUserRoles(Integer userId);

    RoleDo getRoleByName(String roleName);
}
