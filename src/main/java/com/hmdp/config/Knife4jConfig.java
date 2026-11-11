package com.hmdp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / OpenAPI3 接口文档配置
 *
 * 启动后访问：http://localhost:8081/doc.html
 *
 * @author HM-DianPing
 */
@Configuration
public class Knife4jConfig {

    private static final String SECURITY_SCHEME_NAME = "Authorization";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("HM-DianPing 高并发点评系统 API 文档")
                        .description("## 项目简介\n" +
                                "基于 Spring Boot 3 的高并发本地生活服务平台，涵盖探店笔记、优惠券秒杀、商户推荐等核心业务场景。\n\n" +
                                "## 核心特性\n" +
                                "- Redis 五种数据结构实战\n" +
                                "- Redisson 分布式锁\n" +
                                "- RabbitMQ 异步削峰\n" +
                                "- 缓存三连完整解决方案\n" +
                                "- Sentinel 限流防刷\n" +
                                "- WebSocket 实时通知")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("HM-DianPing Team")
                                .email("your-email@example.com")
                                .url("https://github.com/your-username/hm-dianping"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("请在请求头添加：Authorization: Bearer {token}"))
                )
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}