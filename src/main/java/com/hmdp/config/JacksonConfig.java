package com.hmdp.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigInteger;

/**
 * Jackson 序列化配置。
 *
 * 解决一个很隐蔽的坑：JavaScript 的 Number 只能精确表示到 2^53-1（9007199254740991），
 * 而雪花算法生成的订单 ID 是 19 位（约 1.9×10^18），直接按数字序列化给前端，
 * 前端 JSON.parse 之后末几位会被抹平 —— 订单号变成 1234567890123456800 这种。
 *
 * 表现是：页面上显示的订单号和数据库里的对不上，用户拿着这个号去找客服查不到单。
 * 金额大的 ID 甚至可能出现两个不同订单号「看起来一样」。
 *
 * 业界通用做法：把所有 Long 序列化成字符串。前端拿字符串展示、回传都能原样保留；
 * Spring 反序列化时也能把 "123" 正确还原成 Long，不影响接口参数。
 *
 * 注意 total 也会变成字符串，前端比较分页时要用 Number(total) 转一下。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            module.addSerializer(BigInteger.class, ToStringSerializer.instance);
            builder.modulesToInstall(module);
        };
    }
}
