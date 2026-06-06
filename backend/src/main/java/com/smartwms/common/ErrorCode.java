/**
 * 全局异常码常量定义。
 *
 * @author Focus
 * @date 2026-06-03
 */
package com.smartwms.common;

public final class ErrorCode {

    private ErrorCode() {}

    /** 成功 */
    public static final int SUCCESS = 0;

    /** 请求参数有误 / 校验失败 */
    public static final int BAD_REQUEST = 400;

    /** 未登录或 JWT 令牌失效 */
    public static final int UNAUTHORIZED = 401;

    /** 已登录但无权限 */
    public static final int FORBIDDEN = 403;

    /** 资源不存在 */
    public static final int NOT_FOUND = 404;

    /** 后端未知异常 */
    public static final int INTERNAL_ERROR = 500;

    /** AI 线程池满或拒绝提交 */
    public static final int AI_THREAD_POOL_FULL = 2001;

    /** AI 大模型 API 调用超时/网络中断，已触发 Mock 兜底 */
    public static final int AI_API_TIMEOUT = 2002;

    /** 库存扣减失败（出库数大于现有物理库存） */
    public static final int STOCK_INSUFFICIENT = 3001;
}
