package com.example.common.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：拦截所有 Controller 抛出的异常，统一转成 Result 结构返回。
 * 有了它，Controller / Service 里就不需要写 try-catch 了。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：返回对应的业务错误码和提示 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getErrorCode().getCode(), e.getMessage());
    }

    /** 参数校验失败（@Valid 注解触发）：返回 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("参数不合法");
        log.warn("参数校验失败: {}", detail);
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), detail);
    }

    /** 缺少必填请求参数（如 GET /orders 没带 userId）：返回 400 而不是笼统的 500 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParameter(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), "缺少必填参数: " + e.getParameterName());
    }

    /** 请求体 JSON 解析失败（常见原因：中文被 GBK 编码发送）：返回 400 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), "请求体不是合法的 UTF-8 JSON");
    }

    /** 兜底：未预料到的异常，返回 500（完整堆栈只打在日志里，不暴露给前端） */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }
}
