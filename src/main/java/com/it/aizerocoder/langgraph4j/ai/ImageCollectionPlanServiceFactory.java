package com.it.aizerocoder.langgraph4j.ai;

import com.it.aizerocoder.ai.guardrail.PromptSafetyInputGuardrail;
import com.it.aizerocoder.utils.SpringContextUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 图片收集规划服务工厂
 */
@Slf4j
@Configuration
public class ImageCollectionPlanServiceFactory {
    /**
     * 创建图片收集规划服务实例（支持并发）
     * 每次调用都会创建新的实例，使用通用多例 ChatModel
     */
    public ImageCollectionPlanService createImageCollectionPlanService() {
        // 动态获取通用多例 ChatModel，支持并发
        ChatModel chatModel = SpringContextUtil.getBean("chatModelPrototype", ChatModel.class);
        return AiServices.builder(ImageCollectionPlanService.class)
                .chatModel(chatModel)
                .inputGuardrails(new PromptSafetyInputGuardrail()) // 添加输入护轨
                .build();
    }
    /**
     * 默认提供一个 Bean（向后兼容，非并发场景使用）
     */
    @Bean
    public ImageCollectionPlanService imageCollectionPlanService() {
        return createImageCollectionPlanService();
    }
}