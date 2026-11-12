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
                        "/user/login",
                        // 接口文档：不放行就是 401。
                        // knife4j 的页面和它依赖的静态资源全部走不到登录逻辑，
                        // 之前 doc.html 一直打不开就是被这里拦掉的。
                        // 注意：这几个路径不校验登录，所以 prod 环境要配合网关或
                        // springdoc.api-docs.enabled=false 一起关掉，别在公网裸奔。
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-resources/**",
                        "/favicon.ico"
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
