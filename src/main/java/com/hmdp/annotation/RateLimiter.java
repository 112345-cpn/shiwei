package com.hmdp.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义限流注解（基于 Sentinel）
 *
 * 使用方法：
 * <pre>
 * {@code
 * @RateLimiter(name = "seckill", qps = 100, message = "当前访问过于拥挤，请稍后再试")
 * public Result seckillVoucher(Long voucherId) { ... }
 * }
 * </pre>
 *
 * <p>
 * 注意：Sentinel 的资源名必须全局唯一，建议使用"业务名:方法名"格式
 *
 * @author HM-DianPing
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimiter {

    /**
     * 资源名称（唯一标识，对应 Sentinel 的 Resource Name）
     */
    String name();

    /**
     * 每秒通过的请求数量（QPS 阈值）
     */
    double qps() default 100.0;

    /**
     * 限流触发后的提示消息
     */
    String message() default "访问过于拥挤，请稍后再试";
}