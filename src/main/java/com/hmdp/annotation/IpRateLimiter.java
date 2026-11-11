package com.hmdp.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * IP 限流注解（针对单一 IP 的限流，防止恶意刷接口）
 *
 * 适用于：登录、发送验证码、文章发布等容易被刷的接口
 *
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * @IpRateLimiter(qps = 5, message = "操作过于频繁，请 1 分钟后再试")
 * @PostMapping("/login")
 * public Result login(@RequestBody LoginForm form) { ... }
 * }
 * </pre>
 *
 * @author HM-DianPing
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IpRateLimiter {

    /**
     * 资源名称（默认自动生成："ip:类名.方法名"）
     */
    String name() default "";

    /**
     * 单个 IP 每秒允许的请求数
     */
    double qps() default 5.0;

    /**
     * 限流触发后的提示消息
     */
    String message() default "操作过于频繁，请稍后再试";
}