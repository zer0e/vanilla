package com.github.zer0e.vanilla.application.vo;

import com.github.zer0e.vanilla.domain.Port;
import com.github.zer0e.vanilla.domain.Service;
import com.github.zer0e.vanilla.domain.Volume;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class ServiceVo extends Service {
    private List<Port> ports;
    private List<Volume> volumes;
}
