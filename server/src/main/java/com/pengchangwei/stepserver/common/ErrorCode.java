package com.pengchangwei.stepserver.common;

/**
 * 统一业务错误码，所有业务异常都从这里取，
 * 方便前端根据 code 做不同的错误提示。
 * <p>
 * 错误码分段规则：
 * 1xxx — 用户模块（注册、登录、token）
 * 2xxx — 步数模块（上报、查询）
 */
public enum ErrorCode {

    /** 通用错误 */
    PARAM_ERROR(400, "参数校验失败"),

    /** 用户模块 1xxx */
    USERNAME_EXISTS(1001, "用户名已被注册"),
    LOGIN_FAILED(1002, "用户名或密码错误"),
    TOKEN_INVALID(1003, "令牌无效"),
    TOKEN_EXPIRED(1004, "令牌已过期，请重新登录"),
    USER_NOT_FOUND(1005, "用户不存在"),

    /** 步数模块 2xxx */
    STEP_UPLOAD_TOO_FREQUENT(2001, "步数上报过于频繁，请稍后再试"),

    /** 系统错误 */
    INTERNAL_ERROR(500, "服务器内部错误");

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
