package com.shiwei;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@MapperScan("com.shiwei.**.mapper")
@SpringBootApplication
public class ShiweiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShiweiApplication.class, args);
    }
}
