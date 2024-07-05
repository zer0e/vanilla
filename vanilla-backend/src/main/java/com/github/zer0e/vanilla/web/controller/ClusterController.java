package com.github.zer0e.vanilla.web.controller;

import com.github.zer0e.vanilla.application.ClusterService;
import com.github.zer0e.vanilla.application.dto.CreateClusterDto;
import com.github.zer0e.vanilla.application.dto.UpdateClusterDto;
import com.github.zer0e.vanilla.application.vo.ClusterVo;
import com.github.zer0e.vanilla.common.RestResponse;
import com.github.zer0e.vanilla.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "集群相关")
@RequestMapping("/cluster/api")
@RequiredArgsConstructor
public class ClusterController {

    private final ClusterService clusterService;

    @PostMapping("/v1/create")
    @Operation(summary = "创建集群")
    @PreAuthorize("hasRole('admin')")
    public RestResponse<ClusterVo> createCluster(@Valid @RequestBody CreateClusterDto createClusterDto) throws BusinessException {
        return RestResponse.ok(clusterService.createCluster(createClusterDto));
    }

    @PostMapping("/v1/update")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "修改集群")
    public RestResponse<ClusterVo> updateCluster(@Valid @RequestBody UpdateClusterDto updateClusterDto) throws BusinessException {
        return RestResponse.ok(clusterService.updateCluster(updateClusterDto));
    }

    @PostMapping("/v1/delete")
    @PreAuthorize("hasRole('admin')")
    @Operation(summary = "删除集群")
    public RestResponse<ClusterVo> deleteCluster(@Valid @RequestBody UpdateClusterDto updateClusterDto) throws BusinessException {
        clusterService.deleteCluster(updateClusterDto.getId());
        return RestResponse.ok(null);
    }

    @GetMapping("/v1/list")
    @Operation(summary = "获取有权限的集群")
    public RestResponse<List<ClusterVo>> getClusters() throws BusinessException {
        return RestResponse.ok(clusterService.getClusters());
    }
}
