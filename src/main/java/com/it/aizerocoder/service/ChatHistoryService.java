package com.it.aizerocoder.service;

import com.it.aizerocoder.model.dto.chathistory.ChatHistoryQueryRequest;
import com.it.aizerocoder.model.entity.User;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.it.aizerocoder.model.entity.ChatHistory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author <a href="https://github.com/Amnonlogue">wanghf</a>
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 添加对话历史
     * 
     * @param appId
     * @param message
     * @param messageType
     * @param userId
     * @return
     */
    boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    /**
     * 添加工具调用请求消息到对话历史
     * 
     * @param appId           应用ID
     * @param toolRequestJson 工具调用请求的JSON字符串（包含id、name、arguments）
     * @param userId          用户ID
     * @return 是否添加成功
     */
    boolean addToolRequestMessage(Long appId, String toolRequestJson, Long userId);

    /**
     * 添加工具执行结果消息到对话历史
     * 
     * @param appId            应用ID
     * @param toolExecutedJson 工具执行结果的JSON字符串（包含id、name、arguments、result）
     * @param userId           用户ID
     * @return 是否添加成功
     */
    boolean addToolExecutedMessage(Long appId, String toolExecutedJson, Long userId);

    /**
     * 根据应用id删除对话历史
     * 
     * @param appId
     * @return
     */
    boolean deleteByAppId(Long appId);

    /**
     * 分页查询对话历史
     * 
     * @param appId
     * @param pageSize
     * @param lastCreateTime
     * @param loginUser
     * @return
     */
    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
            LocalDateTime lastCreateTime,
            User loginUser);

    /**
     * 加载应用对话历史到内存
     * 
     * @param appId
     * @param chatMemory
     * @param maxCount   最多加载多少条
     * @return 加载成功的条数
     */
    int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);

    /**
     * 获取查询历史对话包装类
     *
     * @param chatHistoryQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);
}
