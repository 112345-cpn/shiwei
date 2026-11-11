package com.hmdp.config;

import com.hmdp.interceptor.AntiBrushInterceptor;
import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 刷新 token 拦截器（全局）
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0);

        // 2. 登录拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login"
                ).order(1);

        // 3. 防刷拦截器（Step 7 新增，针对会被刷的接口）
        registry.addInterceptor(new AntiBrushInterceptor(stringRedisTemplate))
                .addPathPatterns(
                        "/blog/**",      // 博客相关
                        "/follow/**",    // 关注
                        "/sign",         // 签到
                        "/voucher-order/seckill/**"  // 秒杀入口重点防护
                )
                .excludePathPatterns("/blog/hot")
                .order(2);
    }
}
