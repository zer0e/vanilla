package com.github.zer0e.vanilla.common;

import lombok.*;

@Data
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RestResponse<T> {
    private Boolean success;
    private Integer code;
    private String msg;
    private T data;

    public static <T> RestResponse<T> ok(T data) {
        RestResponse<T> response = new RestResponse<>();
        response.setSuccess(true);
        response.setCode(0);
        response.setData(data);
        return response;
    }

    public static <T> RestResponse<T> fail(String msg) {
        RestResponse<T> response = new RestResponse<>();
        response.setSuccess(false);
        response.setMsg(msg);
        return response;
    }

    public static <T> RestResponse<T> fail(Integer code, String msg) {
        RestResponse<T> response = new RestResponse<>();
        response.setSuccess(false);
        response.setCode(code);
        response.setMsg(msg);
        return response;
    }
}
