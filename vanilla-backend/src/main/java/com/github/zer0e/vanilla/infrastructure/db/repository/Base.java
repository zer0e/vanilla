package com.github.zer0e.vanilla.infrastructure.db.repository;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

import java.time.LocalDateTime;

/**
 * The type Base.
 */
@Data
@FieldNameConstants
public abstract class Base {
    /**
     * The Creation user.
     */
    private String createUser;
    /**
     * The Creation time.
     */
    private LocalDateTime createTime;

    /**
     * The Modify user.
     */
    private String modifyUser;
    /**
     * The Modify time.
     */
    private LocalDateTime modifyTime;

    /**
     * The Delete user.
     */
    private String deleteUser;
    /**
     * The Delete time.
     */
    private LocalDateTime deleteTime;
    /**
     * The Status.
     */
    private Integer status = 0;
}
