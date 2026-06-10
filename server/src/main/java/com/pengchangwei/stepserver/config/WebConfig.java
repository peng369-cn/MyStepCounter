package com.pengchangwei.stepserver.config;

import com.pengchangwei.stepserver.security.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册JWT拦截器，/api/**下所有接口都需要带token访问，
 * 只有注册和登录两个接口放行，不然没注册的用户连登录都调不了。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    public WebConfig(JwtInterceptor jwtInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/user/register", "/api/user/login",
                        "/api/user/refresh", "/api/user/global-version");
    }
}
