package com.pengchangwei.stepserver.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解，配合RateLimitAspect使用。
 * 基于Redis INCR + EXPIRE实现固定窗口限流。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 限流key前缀，会和userId拼接成 redis key */
    String key() default "rate_limit";

    /** 时间窗口内最大请求次数 */
    int maxCount() default 10;

    /** 时间窗口，单位秒 */
    int windowSeconds() default 60;
}
