package com.hmdp.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 配置类
 *
 * 包含两个功能：
 * 1. 启用 Sentinel 的 @SentinelResource 注解支持
 * 2. 应用启动时初始化限流规则（兜底方式，规则也可以通过 Dashboard 动态配置）
 *
 * @author HM-DianPing
 */
@Slf4j
@Configuration
public class SentinelConfig {

    /**
     * 注册 Sentinel 切面（支持 @SentinelResource 注解）
     */
    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    /**
     * 初始化限流规则
     *
     * 启动时加载代码中配置的兜底规则（生产环境推荐使用 Dashboard 动态配置）
     */
    @PostConstruct
    public void initFlowRules() {
        log.info("正在初始化 Sentinel 限流规则...");
        List<FlowRule> rules = new ArrayList<>();

        // 1. 秒杀接口限流：100 QPS（生产环境应从配置中心读）
        FlowRule seckillRule = new FlowRule();
        seckillRule.setResource("seckill");
        seckillRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        seckillRule.setCount(100);
        seckillRule.setLimitApp("default");
        rules.add(seckillRule);

        // 2. 博客发布限流：20 QPS
        FlowRule blogRule = new FlowRule();
        blogRule.setResource("blog");
        blogRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        blogRule.setCount(20);
        blogRule.setLimitApp("default");
        rules.add(blogRule);

        // 3. 登录接口限流：10 QPS（防止撞库）
        FlowRule loginRule = new FlowRule();
        loginRule.setResource("login");
        loginRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        loginRule.setCount(10);
        loginRule.setLimitApp("default");
        rules.add(loginRule);

        FlowRuleManager.loadRules(rules);
        log.info("Sentinel 限流规则加载完成：{} 条规则", rules.size());
    }
}