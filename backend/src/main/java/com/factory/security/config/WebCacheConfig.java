package com.factory.security.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 库存相关数据（配件现存量、方案库存校验、缺口数字）全部为实时计算结果，
 * 对账时必须与数据库真实库存一致，故对所有接口响应统一标记 no-store：
 * 浏览器/中间代理不得复用旧响应，重新打开方案列表、库存缺口页时一定拿到最新重算结果。
 */
@Configuration
public class WebCacheConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                     Object handler) {
                response.setHeader("Cache-Control", "no-store");
                response.setHeader("Pragma", "no-cache");
                return true;
            }
        }).addPathPatterns("/**");
    }
}
