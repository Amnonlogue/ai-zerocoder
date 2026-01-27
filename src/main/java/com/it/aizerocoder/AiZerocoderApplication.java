package com.it.aizerocoder;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
@MapperScan("com.it.aizerocoder.mapper")
public class AiZerocoderApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiZerocoderApplication.class, args);
    }

}
