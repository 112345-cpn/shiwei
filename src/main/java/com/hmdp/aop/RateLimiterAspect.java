package com.hmdp.aop;

import cn.hutool.core.util.StrUtil;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.hmdp.annotation.IpRateLimiter;
import com.hmdp.annotation.RateLimiter;
import com.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * Sentinel 限流 AOP 切面（支持 @RateLimiter 和 @IpRateLimiter 注解）
 *
 * 三级限流防护：
 * 1. 全局限流：基于方法名限流（如：秒杀全局 100 QPS）
 * 2. 用户级限流：基于用户 ID 限流（防单个用户刷接口）
 * 3. IP 级限流：基于 IP 限流（防脚本攻击）
 *
 * @author HM-DianPing
 */
@Slf4j
@Aspect
@Component
public class RateLimiterAspect {

    // ============================================
    // 处理 @RateLimiter 注解（方法级限流）
    // ============================================

    @Around("@annotation(com.hmdp.annotation.RateLimiter)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimiter annotation = method.getAnnotation(RateLimiter.class);

        if (annotation == null) {
            return joinPoint.proceed();
        }

        String resourceName = annotation.name();
        String message = annotation.message();

        // Sentinel 入口（try-with-resources 自动退出）
        try (Entry entry = SphU.entry(resourceName)) {
            // 通过限流，执行原方法
            return joinPoint.proceed();
        } catch (BlockException e) {
            // 被限流了
            log.warn("触发限流：resource={}, message={}", resourceName, message);
            return com.hmdp.dto.Result.fail(message);
        } catch (Throwable e) {
            // 业务异常，继续抛出
            throw e;
        }
    }

    // ============================================
    // 处理 @IpRateLimiter 注解（IP 级限流）
    // ============================================

    @Around("@annotation(com.hmdp.annotation.IpRateLimiter)")
    public Object aroundIp(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        IpRateLimiter annotation = method.getAnnotation(IpRateLimiter.class);

        if (annotation == null) {
            return joinPoint.proceed();
        }

        // 1. 获取客户端 IP
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String clientIp = getClientIp(request);

        // 2. 构造资源名（IP + 方法名）
        String name = StrUtil.isBlank(annotation.name())
                ? "ip:" + method.getDeclaringClass().getSimpleName() + "." + method.getName()
                : annotation.name();
        String resourceName = name + ":" + clientIp;

        // 3. 动态注册限流规则（首次访问时）
        registerIpRuleIfNeeded(resourceName, annotation.qps());

        // 4. 进入 Sentinel
        try (Entry entry = SphU.entry(resourceName)) {
            return joinPoint.proceed();
        } catch (BlockException e) {
            log.warn("IP 触发限流：resource={}, ip={}", resourceName, clientIp);
            return com.hmdp.dto.Result.fail(annotation.message());
        } catch (Throwable e) {
            throw e;
        }
    }

    /**
     * 获取客户端真实 IP（支持反向代理场景）
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 动态注册 IP 限流规则
     */
    private void registerIpRuleIfNeeded(String resourceName, double qps) {
        // 简化处理：每次都重新加载规则（实际项目用 ConcurrentHashSet 缓存只初始化一次）
        var rules = FlowRuleManager.getRules();
        boolean exists = rules.stream()
                .anyMatch(rule -> rule.getResource().equals(resourceName));
        if (!exists) {
            com.alibaba.csp.sentinel.slots.block.flow.FlowRule rule =
                    new com.alibaba.csp.sentinel.slots.block.flow.FlowRule(resourceName);
            rule.setGrade(com.alibaba.csp.sentinel.slots.block.RuleConstant.FLOW_GRADE_QPS);
            rule.setCount(qps);
            rules.add(rule);
            FlowRuleManager.loadRules(rules);
            log.info("动态注册 IP 限流规则：{}, QPS={}", resourceName, qps);
        }
    }
}