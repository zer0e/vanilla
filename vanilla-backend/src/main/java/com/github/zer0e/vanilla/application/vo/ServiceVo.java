package com.github.zer0e.vanilla.application.vo;

import com.github.zer0e.vanilla.domain.Service;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class ServiceVo extends Service {
}
