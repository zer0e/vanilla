package com.github.zer0e.vanilla.application.config;

import com.github.zer0e.vanilla.common.RestResponse;
import com.github.zer0e.vanilla.common.Constants;
import com.github.zer0e.vanilla.common.exception.NoPermissionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({NoPermissionException.class, AuthorizationDeniedException.class})
    public RestResponse<String> NoPermissionHandler() {
        return RestResponse.fail(403, Constants.NO_PERMISSION);
    }

    @ExceptionHandler(Throwable.class)
    public RestResponse<String> exceptionHandler(Exception ex) {
        if (log.isDebugEnabled()) {
            log.warn("", ex);
        }
        return RestResponse.fail(500, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public RestResponse<String> methodArgumentNotValidExceptionHandler(MethodArgumentNotValidException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        List<String> collect = fieldErrors.stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.toList());
        return RestResponse.fail(String.join(";", collect));
    }
}
