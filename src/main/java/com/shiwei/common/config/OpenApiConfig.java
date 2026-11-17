package com.shiwei.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI shiweiOpenApi() {
        return new OpenAPI().info(new Info()
                .title("拾味 SHIWEI · 本地生活平台 API")
                .version("2.0.0")
                .description("消费者 / 商家 / 平台运营三端接口")
                .contact(new Contact().name("shiwei")));
    }
}
