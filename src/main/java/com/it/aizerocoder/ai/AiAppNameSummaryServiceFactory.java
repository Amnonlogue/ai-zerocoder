package com.it.aizerocoder.ai;

import com.it.aizerocoder.utils.SpringContextUtil;
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

    /**
     * 创建AI应用名称总结服务实例
     */
    public AiAppNameSummaryService createAiAppNameSummaryService() {
        // 动态获取多例的路由 ChatModel，支持并发
        ChatModel chatModel = SpringContextUtil.getBean("chatModelPrototype", ChatModel.class);
        return AiServices.builder(AiAppNameSummaryService.class)
                .chatModel(chatModel)
                .build();
    }

    /**
     * 默认提供一个 Bean
     */
    @Bean
    public AiAppNameSummaryService aiAppNameSummaryService() {
        return createAiAppNameSummaryService();
    }
}
