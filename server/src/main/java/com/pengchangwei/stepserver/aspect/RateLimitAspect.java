package com.pengchangwei.stepserver.aspect;

import com.pengchangwei.stepserver.annotation.RateLimit;
import com.pengchangwei.stepserver.common.BusinessException;
import com.pengchangwei.stepserver.common.ErrorCode;
import com.pengchangwei.stepserver.security.LoginUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 接口限流切面，拦截带@RateLimit注解的方法。
 * 用Redis INCR做计数器，首次请求设过期时间，超限抛异常。
 */
@Aspect
@Component
public class RateLimitAspect {

    private final StringRedisTemplate redisTemplate;

    public RateLimitAspect(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        Long userId = LoginUser.get();
        String redisKey = "rate_limit:" + rateLimit.key() + ":" + userId;

        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1) {
            redisTemplate.expire(redisKey, rateLimit.windowSeconds(), TimeUnit.SECONDS);
        }

        if (count != null && count > rateLimit.maxCount()) {
            throw new BusinessException(ErrorCode.STEP_UPLOAD_TOO_FREQUENT);
        }

        return joinPoint.proceed();
    }
}
