package com.hbsd.labselect.common;

/**
 * 业务异常：由全局异常处理器转成 {code, message}。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static BizException of(String message) {
        return new BizException(message);
    }

    public static BizException forbidden(String message) {
        return new BizException(403, message);
    }

    public static BizException unauthorized() {
        return new BizException(401, "登录状态失效，请重新登录");
    }
}
