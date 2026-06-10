package com.pengchangwei.stepserver.common;

/**
 * 业务异常，Service 层遇到业务规则不满足时抛出，
 * 由 GlobalExceptionHandler 统一捕获并转成 Result 返回给前端。
 * <p>
 * 用法示例：
 * <pre>{@code
 * if (userMapper.selectCount(wrapper) > 0) {
 *     throw new BusinessException(ErrorCode.USERNAME_EXISTS);
 * }
 * }</pre>
 */
public class BusinessException extends RuntimeException {

    private final int code;

    /**
     * 用 ErrorCode 枚举创建异常，code 和 message 自动从枚举取值
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 自定义 code 和 message，用于 ErrorCode 枚举覆盖不到的场景
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
