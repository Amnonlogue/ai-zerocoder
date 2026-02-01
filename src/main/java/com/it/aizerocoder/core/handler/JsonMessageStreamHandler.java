package com.it.aizerocoder.core.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.it.aizerocoder.ai.model.message.*;
import com.it.aizerocoder.ai.tools.BaseTool;
import com.it.aizerocoder.ai.tools.ToolManager;
import com.it.aizerocoder.constant.AppConstant;
import com.it.aizerocoder.core.builder.VueProjectBuilder;
import com.it.aizerocoder.model.entity.User;
import com.it.aizerocoder.model.enums.ChatHistoryMessageTypeEnum;
import com.it.aizerocoder.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;

/**
 * JSON 消息流处理器
 * 处理 VUE_PROJECT 类型的复杂流式响应，包含工具调用信息
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private ToolManager toolManager;

    /**
     * 处理 TokenStream（VUE_PROJECT）
     * 解析 JSON 消息并重组为完整的响应格式
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
            ChatHistoryService chatHistoryService,
            long appId, User loginUser) {
        // 收集数据用于生成后端记忆格式
        StringBuilder aiTextResponseBuilder = new StringBuilder();
        // 用于跟踪已经见过的工具ID，判断是否是第一次调用
        Set<String> seenToolIds = new HashSet<>();

        return originFlux
                .map(chunk -> {
                    // 解析每个 JSON 消息块，传入 chatHistoryService 用于实时保存工具调用记录
                    return handleJsonMessageChunk(chunk, aiTextResponseBuilder, seenToolIds,
                            chatHistoryService, appId, loginUser.getId());
                })
                .filter(StrUtil::isNotEmpty) // 过滤空字串
                .doOnComplete(() -> {
                    // 流式响应完成后，添加 AI 消息到对话历史
                    String aiTextResponse = aiTextResponseBuilder.toString();
                    if (StrUtil.isNotBlank(aiTextResponse)) {
                        chatHistoryService.addChatMessage(appId, aiTextResponse,
                                ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    }
                    // 异步构造Vue 项目
                    String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_" + appId;
                    vueProjectBuilder.buildProjectAsync(projectPath);
                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage,
                            ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }

    /**
     * 解析并收集 TokenStream 数据
     * 新增参数用于实时保存工具调用记录
     */
    private String handleJsonMessageChunk(String chunk,
            StringBuilder aiTextResponseBuilder,
            Set<String> seenToolIds,
            ChatHistoryService chatHistoryService,
            long appId,
            Long userId) {
        // 解析 JSON
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        switch (typeEnum) {
            case AI_RESPONSE -> {
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiMessage.getData();
                // 拼接 AI 文本响应
                aiTextResponseBuilder.append(data);
                return data;
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                // 检查是否是第一次看到这个工具 ID
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    // 第一次调用这个工具，记录 ID 并完整返回工具信息
                    seenToolIds.add(toolId);

                    // 保存工具调用请求到数据库
                    JSONObject toolRequestJson = new JSONObject();
                    toolRequestJson.set("id", toolRequestMessage.getId());
                    toolRequestJson.set("name", toolRequestMessage.getName());
                    toolRequestJson.set("arguments", toolRequestMessage.getArguments());
                    chatHistoryService.addToolRequestMessage(appId, toolRequestJson.toString(), userId);

                    // 根据工具名称获取工具实例
                    BaseTool tool = toolManager.getTool(toolRequestMessage.getName());
                    return tool.generateToolRequestResponse();
                } else {
                    // 不是第一次调用这个工具，直接返回空
                    return "";
                }
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                JSONObject jsonObject = JSONUtil.parseObj(toolExecutedMessage.getArguments());
                // 根据工具名称获取工具实例
                BaseTool tool = toolManager.getTool(toolExecutedMessage.getName());
                String result = tool.generateToolExecutedResult(jsonObject);

                // 保存工具执行结果到数据库（用于记忆恢复）
                JSONObject toolExecutedJson = new JSONObject();
                toolExecutedJson.set("id", toolExecutedMessage.getId());
                toolExecutedJson.set("name", toolExecutedMessage.getName());
                toolExecutedJson.set("arguments", toolExecutedMessage.getArguments());
                toolExecutedJson.set("result", result);
                chatHistoryService.addToolExecutedMessage(appId, toolExecutedJson.toString(), userId);

                // 输出前端和要持久化的内容
                String output = String.format("\n\n%s\n\n", result);
                aiTextResponseBuilder.append(output);
                return output;
            }
            default -> {
                log.error("不支持的消息类型: {}", typeEnum);
                return "";
            }
        }
    }
}
