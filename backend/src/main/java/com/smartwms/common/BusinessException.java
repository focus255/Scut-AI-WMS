/**
 * 业务异常类，用于在 Service 层抛出已知业务错误，由 GlobalExceptionHandler 统一捕获处理。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.common;

public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
