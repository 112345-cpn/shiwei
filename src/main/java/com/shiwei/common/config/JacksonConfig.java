package com.shiwei.common.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;

@Configuration
public class JacksonConfig {

    private static final String DATE_TIME = "yyyy-MM-dd HH:mm:ss";
    private static final String DATE = "yyyy-MM-dd";
    private static final String TIME = "HH:mm:ss";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            // 雪花/自增 id 超出 JS 安全整数范围，统一转字符串
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);

            DateTimeFormatter dt = DateTimeFormatter.ofPattern(DATE_TIME);
            DateTimeFormatter d = DateTimeFormatter.ofPattern(DATE);
            DateTimeFormatter t = DateTimeFormatter.ofPattern(TIME);

            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(dt));
            builder.deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(dt));
            builder.serializerByType(LocalDate.class, new LocalDateSerializer(d));
            builder.deserializerByType(LocalDate.class, new LocalDateDeserializer(d));
            builder.serializerByType(LocalTime.class, new LocalTimeSerializer(t));
            builder.deserializerByType(LocalTime.class, new LocalTimeDeserializer(t));
        };
    }
}
