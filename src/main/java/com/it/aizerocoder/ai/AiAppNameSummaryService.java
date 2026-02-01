package com.it.aizerocoder.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * AI应用名称总结服务
 * 根据用户输入的 prompt 生成简洁的应用名称（12字以内）
 */
public interface AiAppNameSummaryService {

    /**
     * 根据用户需求总结应用名称
     *
     * @param userPrompt 用户输入的需求描述
     * @return 总结后的应用名称（12字以内）
     */
    @SystemMessage(fromResource = "prompt/app-name-summary-system-prompt.txt")
    @UserMessage("请为以下需求生成应用名称：{{it}}")
    String summarizeAppName(String userPrompt);
}
