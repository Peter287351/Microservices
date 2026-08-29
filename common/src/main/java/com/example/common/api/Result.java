package com.example.common.api;

/**
 * 统一返回结果包装类。
 * 所有 Controller 的返回值都用它包一层，前端拿到固定结构：
 * { "code": 0, "message": "success", "data": ... }
 *
 * @param <T> 业务数据的类型
 */
public class Result<T> {

    /** 业务状态码：0 表示成功，非 0 表示各种失败（见 ErrorCode） */
    private int code;
    /** 提示信息 */
    private String message;
    /** 业务数据 */
    private T data;

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

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
