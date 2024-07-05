package com.github.zer0e.vanilla.application;

import com.github.zer0e.vanilla.application.dto.CreateClusterDto;
import com.github.zer0e.vanilla.application.dto.UpdateClusterDto;
import com.github.zer0e.vanilla.application.vo.ClusterVo;
import com.github.zer0e.vanilla.common.exception.BusinessException;
import com.github.zer0e.vanilla.infrastructure.db.repository.UserRoleDo;

import java.util.List;

public interface ClusterService {
    ClusterVo createCluster(CreateClusterDto createClusterDto) throws BusinessException;

    ClusterVo updateCluster(UpdateClusterDto updateClusterDto) throws BusinessException;

    void deleteCluster(Integer id) throws BusinessException;

    /**
     * 获取有权限的集群列表
     *
     * @return the clusters
     * @throws BusinessException the business exception
     */
    List<ClusterVo> getClusters() throws BusinessException;

    List<UserRoleDo> getClusterUsers(Integer clusterId);
}
