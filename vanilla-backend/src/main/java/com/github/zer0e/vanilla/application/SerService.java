package com.github.zer0e.vanilla.application;

import com.github.zer0e.vanilla.application.dto.CreateServiceDto;
import com.github.zer0e.vanilla.application.dto.DeleteServiceDto;
import com.github.zer0e.vanilla.application.dto.GetServicesDto;
import com.github.zer0e.vanilla.application.dto.UpdateServiceDto;
import com.github.zer0e.vanilla.application.vo.ServiceVo;
import com.github.zer0e.vanilla.common.PageData;
import com.github.zer0e.vanilla.common.exception.BusinessException;

public interface SerService {


    ServiceVo createService(CreateServiceDto createServiceDto) throws BusinessException;

    ServiceVo updateService(UpdateServiceDto updateServiceDto) throws BusinessException;

    void deleteService(DeleteServiceDto deleteServiceDto) throws BusinessException;

    PageData<ServiceVo> getServices(GetServicesDto getServicesDto) throws BusinessException;


}
