package com.github.zer0e.vanilla.web.controller;

import com.github.zer0e.vanilla.application.ClusterService;
import com.github.zer0e.vanilla.application.dto.ClusterDto;
import com.github.zer0e.vanilla.application.vo.ClusterVo;
import com.github.zer0e.vanilla.common.RestResponse;
import com.github.zer0e.vanilla.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "集群相关")
@RequestMapping("/cluster/api")
@RequiredArgsConstructor
public class ClusterController {

    private final ClusterService clusterService;

    @PostMapping("/v1/create")
    @PreAuthorize("hasRole('admin')")
    public RestResponse<ClusterVo> createCluster(@Valid @RequestBody ClusterDto clusterDto) throws BusinessException {
        return RestResponse.ok(clusterService.createCluster(clusterDto));
    }
}
