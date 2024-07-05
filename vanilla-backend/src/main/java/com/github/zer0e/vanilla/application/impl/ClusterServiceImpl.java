package com.github.zer0e.vanilla.application.impl;

import com.github.zer0e.vanilla.application.ClusterService;
import com.github.zer0e.vanilla.application.dto.ClusterDto;
import com.github.zer0e.vanilla.application.vo.ClusterVo;
import com.github.zer0e.vanilla.common.util.SecurityUtil;
import com.github.zer0e.vanilla.infrastructure.converter.ClusterConverter;
import com.github.zer0e.vanilla.infrastructure.db.mapper.ClusterMapper;
import com.github.zer0e.vanilla.infrastructure.db.repository.ClusterDo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClusterServiceImpl implements ClusterService {

    private final ClusterMapper clusterMapper;

    @Override
    @PreAuthorize("hasRole('admin')")
    public ClusterVo createCluster(ClusterDto clusterDto) {
        ClusterDo clusterDo = ClusterConverter.INSTANCE.toDo(clusterDto);
        clusterDo.setCreateUser(SecurityUtil.getCurrentUser());
        clusterDo.setCreateTime(LocalDateTime.now());
        clusterMapper.insert(clusterDo);
        return ClusterConverter.INSTANCE.toVo(clusterDo);
    }
}
