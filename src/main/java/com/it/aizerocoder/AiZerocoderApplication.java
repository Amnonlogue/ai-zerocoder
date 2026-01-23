package com.it.aizerocoder;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.it.aizerocoder.mapper")
public class AiZerocoderApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiZerocoderApplication.class, args);
    }

}
