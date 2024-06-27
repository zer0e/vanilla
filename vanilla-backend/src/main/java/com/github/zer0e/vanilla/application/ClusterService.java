package com.github.zer0e.vanilla.application;

import com.github.zer0e.vanilla.application.dto.ClusterDto;
import com.github.zer0e.vanilla.application.vo.ClusterVo;
import com.github.zer0e.vanilla.common.exception.BusinessException;

public interface ClusterService {
    ClusterVo createCluster(ClusterDto clusterDto) throws BusinessException;
}
