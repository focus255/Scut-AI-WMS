/**
 * 统一响应包装类，所有控制器接口必须使用此类封装返回数据。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    /** 状态码：0=成功，非0=各类错误 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 响应数据载荷 */
    private T data;

    private Result() {}

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 成功响应（无数据）。
     */
    public static <T> Result<T> success() {
        return new Result<>(0, "success", null);
    }

    /**
     * 成功响应（携带数据）。
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data);
    }

    /**
     * 成功响应（自定义消息 + 携带数据）。
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(0, message, data);
    }

    /**
     * 失败响应。
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    // ==================== Getters / Setters ====================

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
