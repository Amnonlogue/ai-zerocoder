package com.it.aizerocoder.service;

import com.it.aizerocoder.model.entity.ImageResource;
import com.mybatisflex.core.service.IService;

import java.util.List;

/**
 * 图片资源 服务层
 *
 * @author <a href="https://github.com/Amnonlogue">wanghf</a>
 */
public interface ImageResourceService extends IService<ImageResource> {

    /**
     * 根据应用ID查询图片资源
     *
     * @param appId 应用ID
     * @return 图片资源列表
     */
    List<ImageResource> getByAppId(Long appId);

    /**
     * 增强提示词（附加图片资源信息）
     *
     * @param message 原始消息
     * @param images  图片资源列表
     * @return 增强后的消息
     */
    String enhancePrompt(String message, List<ImageResource> images);

    /**
     * 根据应用ID删除图片资源
     *
     * @param appId 应用ID
     * @return 是否成功
     */
    boolean deleteByAppId(Long appId);

    /**
     * 流式收集图片资源并返回进度信息
     * 并发执行所有收集任务，哪个先完成就先反馈
     *
     * @param appId  应用ID
     * @param prompt 用户提示词
     * @return 包含进度信息的Flux流
     */
    reactor.core.publisher.Flux<String> collectImagesWithProgress(Long appId, String prompt);

}
