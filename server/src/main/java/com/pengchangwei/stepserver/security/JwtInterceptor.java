package com.pengchangwei.stepserver.security;

import com.pengchangwei.stepserver.entity.User;
import com.pengchangwei.stepserver.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 在Controller执行前拦截请求，从Authorization头里取Bearer token校验。
 * 校验通过后把userId写入LoginUser上下文，校验失败直接返回401。
 * 注册和登录接口在WebConfig里配置了放行，不会经过这里。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtInterceptor.class);

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    public JwtInterceptor(JwtUtil jwtUtil, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(401);
            return false;
        }

        String token = authHeader.substring(7);

        try {
            Long userId = jwtUtil.getUserIdFromToken(token);
            User user = userMapper.selectById(userId);
            if (user == null) {
                response.setStatus(401);
                return false;
            }

            if (!jwtUtil.validateAccessToken(token, user.getTokenVersion())) {
                response.setStatus(401);
                return false;
            }

            LoginUser.set(userId);
            return true;
        } catch (Exception e) {
            // JWT解析失败（过期、签名错误等），统一返回401让客户端刷新Token
            log.debug("Token校验异常: {}", e.getMessage());
            response.setStatus(401);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        LoginUser.clear();
    }
}
