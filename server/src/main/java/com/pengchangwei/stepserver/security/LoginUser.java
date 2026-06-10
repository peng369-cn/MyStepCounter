package com.pengchangwei.stepserver.security;

/**
 * 用ThreadLocal存当前请求的用户ID。
 * 拦截器校验JWT后把userId塞进来，Controller/Service里随时get，
 * 请求结束拦截器调clear清理，防止内存泄漏。
 */
public class LoginUser {

    private static final ThreadLocal<Long> USER_HOLDER = new ThreadLocal<>();

    public static void set(Long userId) {
        USER_HOLDER.set(userId);
    }

    public static Long get() {
        return USER_HOLDER.get();
    }

    public static void clear() {
        USER_HOLDER.remove();
    }
}
