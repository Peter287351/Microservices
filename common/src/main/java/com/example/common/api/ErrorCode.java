package com.example.common.api;

/**
 * 全局业务错误码。
 * 编码规则：0 成功；4xx 通用请求错误；1xxx 业务错误；5xxx 系统错误。
 */
public enum ErrorCode {

    SUCCESS(0, "success"),
    BAD_REQUEST(400, "请求参数错误"),
    NOT_FOUND(404, "资源不存在"),
    SYSTEM_ERROR(500, "系统内部错误"),

    // ── 用户服务业务错误 1xxx ──
    USER_NOT_FOUND(1001, "用户不存在"),
    USERNAME_DUPLICATED(1002, "用户名已存在"),

    // ── 订单服务业务错误 2xxx ──
    ORDER_NOT_FOUND(2001, "订单不存在");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
