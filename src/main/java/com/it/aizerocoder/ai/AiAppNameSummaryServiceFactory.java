package com.it.aizerocoder.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI应用名称总结服务工厂
 */
@Slf4j
@Configuration
public class AiAppNameSummaryServiceFactory {

    @Resource
    private ChatModel chatModel;

    /**
     * 创建AI应用名称总结服务实例
     */
    @Bean
    public AiAppNameSummaryService aiAppNameSummaryService() {
        log.info("创建 AiAppNameSummaryService 实例");
        return AiServices.builder(AiAppNameSummaryService.class)
                .chatModel(chatModel)
                .build();
    }
}
