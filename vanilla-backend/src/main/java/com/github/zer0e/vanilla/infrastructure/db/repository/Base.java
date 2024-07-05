package com.github.zer0e.vanilla.infrastructure.db.repository;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * The type Base.
 */
@Data
@FieldNameConstants
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class Base {
    /**
     * The Creation user.
     */
    private String createUser;
    /**
     * The Creation time.
     */
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createTime;

    /**
     * The Modify user.
     */
    private String modifyUser;
    /**
     * The Modify time.
     */
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime modifyTime;

    /**
     * The Delete user.
     */
    private String deleteUser;
    /**
     * The Delete time.
     */
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime deleteTime;
    /**
     * The Status.
     */
    private Integer status = 0;
}
