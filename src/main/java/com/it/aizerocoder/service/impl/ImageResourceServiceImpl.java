package com.it.aizerocoder.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.it.aizerocoder.langgraph4j.ai.ImageCollectionPlanService;
import com.it.aizerocoder.langgraph4j.ai.ImageCollectionPlanServiceFactory;
import com.it.aizerocoder.langgraph4j.tools.ImageSearchTool;
import com.it.aizerocoder.langgraph4j.tools.LogoGeneratorTool;
import com.it.aizerocoder.langgraph4j.tools.MermaidDiagramTool;
import com.it.aizerocoder.langgraph4j.tools.UndrawIllustrationTool;
import com.it.aizerocoder.manger.CosManager;
import com.it.aizerocoder.mapper.ImageResourceMapper;
import com.it.aizerocoder.model.entity.ImageResource;
import com.it.aizerocoder.service.ImageResourceService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 图片资源 服务实现类
 *
 * @author <a href="https://github.com/Amnonlogue">wanghf</a>
 */
@Slf4j
@Service
public class ImageResourceServiceImpl extends ServiceImpl<ImageResourceMapper, ImageResource>
        implements ImageResourceService {

    @Resource
    private ImageCollectionPlanServiceFactory imageCollectionPlanServiceFactory;

    @Resource
    private ImageSearchTool imageSearchTool;

    @Resource
    private UndrawIllustrationTool undrawIllustrationTool;

    @Resource
    private MermaidDiagramTool mermaidDiagramTool;

    @Resource
    private LogoGeneratorTool logoGeneratorTool;

    @Resource
    private CosManager cosManager;

    @Override
    public List<ImageResource> getByAppId(Long appId) {
        if (appId == null || appId <= 0) {
            return Collections.emptyList();
        }
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId);
        return this.list(queryWrapper);
    }

    @Override
    public String enhancePrompt(String message, List<ImageResource> images) {
        if (CollUtil.isEmpty(images)) {
            return message;
        }
        StringBuilder enhanced = new StringBuilder(message);
        enhanced.append("\n\n## 可用图片资源（JSON格式，URL必须原样使用）\n");
        enhanced.append("```json\n[\n");

        for (int i = 0; i < images.size(); i++) {
            ImageResource img = images.get(i);
            enhanced.append("  {\n");
            enhanced.append("    \"id\": ").append(i + 1).append(",\n");
            enhanced.append("    \"type\": \"").append(img.getCategory()).append("\",\n");
            enhanced.append("    \"description\": \"").append(img.getDescription()).append("\",\n");
            enhanced.append("    \"exactUrl\": \"").append(img.getUrl()).append("\"\n");
            enhanced.append("  }");
            if (i < images.size() - 1) {
                enhanced.append(",");
            }
            enhanced.append("\n");
        }

        enhanced.append("]\n```\n");
        enhanced.append("⚠️ 使用图片时，必须从上述JSON中复制exactUrl的值，不得修改任何字符（包括连字符-和下划线_）！\n");

        return enhanced.toString();
    }

    @Override
    public boolean deleteByAppId(Long appId) {
        if (appId == null || appId <= 0) {
            return false;
        }
        // 1. 先查询该应用的所有图片资源
        List<ImageResource> images = getByAppId(appId);
        // 2. 删除 COS 上的图片文件（Logo 和架构图存储在自己的 COS 上）
        if (CollUtil.isNotEmpty(images)) {
            for (ImageResource image : images) {
                String url = image.getUrl();
                // 只删除存储在自己 COS 上的图片
                if (StrUtil.isNotBlank(url) && isCosUrl(url)) {
                    try {
                        boolean deleted = cosManager.deleteFileByUrl(url);
                        if (deleted) {
                            log.info("删除图片资源成功: {}", url);
                        }
                    } catch (Exception e) {
                        log.error("删除图片资源失败: {}, 错误: {}", url, e.getMessage());
                    }
                }
            }
        }
        // 3. 删除数据库记录
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId);
        return this.remove(queryWrapper);
    }

    /**
     * 判断 URL 是否为自己 COS 的 URL
     */
    private boolean isCosUrl(String url) {
        return url != null && url.contains("myqcloud.com");
    }

    @Override
    public Flux<String> collectImagesWithProgress(Long appId, String prompt) {
        // 1. 开始消息流
        Flux<String> startFlux = Flux.just("🎨 正在准备图片素材...\n\n");

        // 2. 收集过程中保存结果
        List<com.it.aizerocoder.langgraph4j.model.ImageResource> allCollectedImages = Collections
                .synchronizedList(new ArrayList<>());

        Flux<String> collectAndSaveFlux = Mono
                .fromCallable(() -> {
                    // 为每次调用创建新的 AI 服务实例，支持并发
                    ImageCollectionPlanService planService = imageCollectionPlanServiceFactory
                            .createImageCollectionPlanService();
                    return planService.planImageCollection(prompt);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(plan -> {
                    List<Mono<CollectResult>> monos = new ArrayList<>();

                    // 内容图片
                    if (plan.getContentImageTasks() != null && !plan.getContentImageTasks().isEmpty()) {
                        monos.add(Flux.fromIterable(plan.getContentImageTasks())
                                .flatMap(task -> Mono
                                        .fromCallable(() -> imageSearchTool.searchContentImages(task.query()))
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .onErrorReturn(Collections.emptyList()))
                                .collectList()
                                .map(lists -> {
                                    List<com.it.aizerocoder.langgraph4j.model.ImageResource> all = new ArrayList<>();
                                    lists.forEach(all::addAll);
                                    allCollectedImages.addAll(all);
                                    return new CollectResult("内容图片", all);
                                }));
                    }

                    // 插画图片
                    if (plan.getIllustrationTasks() != null && !plan.getIllustrationTasks().isEmpty()) {
                        monos.add(Flux.fromIterable(plan.getIllustrationTasks())
                                .flatMap(task -> Mono
                                        .fromCallable(() -> undrawIllustrationTool.searchIllustrations(task.query()))
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .onErrorReturn(Collections.emptyList()))
                                .collectList()
                                .map(lists -> {
                                    List<com.it.aizerocoder.langgraph4j.model.ImageResource> all = new ArrayList<>();
                                    lists.forEach(all::addAll);
                                    allCollectedImages.addAll(all);
                                    return new CollectResult("插画图片", all);
                                }));
                    }

                    // 架构图
                    if (plan.getDiagramTasks() != null && !plan.getDiagramTasks().isEmpty()) {
                        monos.add(Flux.fromIterable(plan.getDiagramTasks())
                                .flatMap(task -> Mono
                                        .fromCallable(() -> mermaidDiagramTool
                                                .generateMermaidDiagram(task.mermaidCode(), task.description()))
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .onErrorReturn(Collections.emptyList()))
                                .collectList()
                                .map(lists -> {
                                    List<com.it.aizerocoder.langgraph4j.model.ImageResource> all = new ArrayList<>();
                                    lists.forEach(all::addAll);
                                    allCollectedImages.addAll(all);
                                    return new CollectResult("架构图", all);
                                }));
                    }

                    // Logo
                    if (plan.getLogoTasks() != null && !plan.getLogoTasks().isEmpty()) {
                        monos.add(Flux.fromIterable(plan.getLogoTasks())
                                .flatMap(task -> Mono
                                        .fromCallable(() -> logoGeneratorTool.generateLogos(task.description()))
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .onErrorReturn(Collections.emptyList()))
                                .collectList()
                                .map(lists -> {
                                    List<com.it.aizerocoder.langgraph4j.model.ImageResource> all = new ArrayList<>();
                                    lists.forEach(all::addAll);
                                    allCollectedImages.addAll(all);
                                    return new CollectResult("Logo", all);
                                }));
                    }

                    return Flux.merge(monos);
                })
                .map(result -> "✓ " + result.type() + "：已收集 " + result.images().size() + " 张\n")
                .concatWith(Mono.fromCallable(() -> {
                    // 保存到数据库
                    List<ImageResource> entities = convertToEntities(allCollectedImages);
                    if (!entities.isEmpty()) {
                        saveImageResources(appId, entities);
                    }
                    return "\n✅ 素材准备完成，共 " + entities.size() + " 张图片可用\n\n---\n\n";
                }).subscribeOn(Schedulers.boundedElastic()));

        return startFlux.concatWith(collectAndSaveFlux)
                .onErrorResume(e -> {
                    log.error("图片收集失败: {}", e.getMessage(), e);
                    return Flux.just("⚠️ 素材收集失败，将继续生成代码\n\n---\n\n");
                });
    }

    /**
     * 收集结果记录
     */
    private record CollectResult(String type, List<com.it.aizerocoder.langgraph4j.model.ImageResource> images) {
    }

    /**
     * 将 langgraph4j 的 ImageResource 转换为实体类
     */
    private List<ImageResource> convertToEntities(List<com.it.aizerocoder.langgraph4j.model.ImageResource> sources) {
        if (CollUtil.isEmpty(sources)) {
            return Collections.emptyList();
        }
        List<ImageResource> entities = new ArrayList<>();
        for (com.it.aizerocoder.langgraph4j.model.ImageResource source : sources) {
            ImageResource entity = ImageResource.builder()
                    .category(source.getCategory() != null ? source.getCategory().getValue() : null)
                    .description(source.getDescription())
                    .url(source.getUrl())
                    .createTime(LocalDateTime.now())
                    .build();
            entities.add(entity);
        }
        return entities;
    }

    /**
     * 批量保存图片资源
     */
    private void saveImageResources(Long appId, List<ImageResource> images) {
        for (ImageResource image : images) {
            image.setAppId(appId);
        }
        this.saveBatch(images);
    }

}
