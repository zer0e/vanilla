package com.github.zer0e.vanilla.application.vo;

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
    // 端口（SVC）不再内嵌于服务列表，统一在「端口访问」页按栈查询
    private List<Volume> volumes;
}
