package com.example.common.api;

/**
 * 业务异常：在 Service 层遇到"不满足业务规则"的情况时抛出，
 * 由 GlobalExceptionHandler 统一捕获并转换成 Result 返回，
 * 避免 Controller 里到处写 try-catch。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
