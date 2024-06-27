package com.github.zer0e.vanilla.application.config;

import com.github.zer0e.vanilla.common.RestResponse;
import com.github.zer0e.vanilla.common.StringConstant;
import com.github.zer0e.vanilla.common.exception.NoPermissionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoPermissionException.class)
    public RestResponse<String> NoPermissionHandler() {
        return RestResponse.fail(403, StringConstant.NO_PERMISSION);
    }
}
