package com.it.aizerocoder.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.it.aizerocoder.langgraph4j.ai.ImageCollectionPlanService;
import com.it.aizerocoder.langgraph4j.model.ImageCollectionPlan;
import com.it.aizerocoder.langgraph4j.tools.ImageSearchTool;
import com.it.aizerocoder.langgraph4j.tools.LogoGeneratorTool;
import com.it.aizerocoder.langgraph4j.tools.MermaidDiagramTool;
import com.it.aizerocoder.langgraph4j.tools.UndrawIllustrationTool;
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
import java.util.concurrent.CompletableFuture;

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
    private ImageCollectionPlanService imageCollectionPlanService;

    @Resource
    private ImageSearchTool imageSearchTool;

    @Resource
    private UndrawIllustrationTool undrawIllustrationTool;

    @Resource
    private MermaidDiagramTool mermaidDiagramTool;

    @Resource
    private LogoGeneratorTool logoGeneratorTool;

    @Override
    public void collectImagesAsync(Long appId, String prompt) {
        // 使用 CompletableFuture 异步执行图片收集
        CompletableFuture.runAsync(() -> {
            try {
                log.info("开始异步收集图片，appId: {}", appId);
                List<ImageResource> collectedImages = doCollectImages(prompt);
                if (CollUtil.isNotEmpty(collectedImages)) {
                    saveImageResources(appId, collectedImages);
                    log.info("图片收集完成，appId: {}, 收集数量: {}", appId, collectedImages.size());
                } else {
                    log.info("图片收集结果为空，appId: {}", appId);
                }
            } catch (Exception e) {
                log.warn("图片收集失败，不影响主流程，appId: {}, error: {}", appId, e.getMessage());
            }
        });
    }

    @Override
    public List<ImageResource> waitForCollection(Long appId, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            List<ImageResource> images = getByAppId(appId);
            if (CollUtil.isNotEmpty(images)) {
                return images;
            }
            try {
                Thread.sleep(500); // 每500ms轮询一次
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("等待图片收集超时，appId: {}", appId);
        return Collections.emptyList();
    }

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
        enhanced.append("\n\n## 可用素材资源\n");
        enhanced.append("请在生成网站时使用以下图片资源，将这些图片合理地嵌入到网站的相应位置中。\n");
        for (ImageResource img : images) {
            enhanced.append("- ")
                    .append(img.getCategory())
                    .append("：")
                    .append(img.getDescription())
                    .append("（").append(img.getUrl()).append("）\n");
        }
        return enhanced.toString();
    }

    @Override
    public boolean deleteByAppId(Long appId) {
        if (appId == null || appId <= 0) {
            return false;
        }
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId);
        return this.remove(queryWrapper);
    }

    @Override
    public Flux<String> collectImagesWithProgress(Long appId, String prompt) {
        // 1. 开始消息流
        Flux<String> startFlux = Flux.just("🎨 正在准备图片素材...\n\n");

        // 2. 收集过程中保存结果
        List<com.it.aizerocoder.langgraph4j.model.ImageResource> allCollectedImages = Collections
                .synchronizedList(new ArrayList<>());

        Flux<String> collectAndSaveFlux = Mono
                .fromCallable(() -> imageCollectionPlanService.planImageCollection(prompt))
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
     * 从计划同步执行收集（用于异步方法复用）
     */
    private List<com.it.aizerocoder.langgraph4j.model.ImageResource> doCollectImagesFromPlan(ImageCollectionPlan plan) {
        List<com.it.aizerocoder.langgraph4j.model.ImageResource> allImages = new ArrayList<>();

        if (plan.getContentImageTasks() != null) {
            for (var task : plan.getContentImageTasks()) {
                try {
                    allImages.addAll(imageSearchTool.searchContentImages(task.query()));
                } catch (Exception e) {
                    log.warn("内容图片搜索失败: {}", e.getMessage());
                }
            }
        }
        if (plan.getIllustrationTasks() != null) {
            for (var task : plan.getIllustrationTasks()) {
                try {
                    allImages.addAll(undrawIllustrationTool.searchIllustrations(task.query()));
                } catch (Exception e) {
                    log.warn("插画图片搜索失败: {}", e.getMessage());
                }
            }
        }
        if (plan.getDiagramTasks() != null) {
            for (var task : plan.getDiagramTasks()) {
                try {
                    allImages.addAll(mermaidDiagramTool.generateMermaidDiagram(task.mermaidCode(), task.description()));
                } catch (Exception e) {
                    log.warn("架构图生成失败: {}", e.getMessage());
                }
            }
        }
        if (plan.getLogoTasks() != null) {
            for (var task : plan.getLogoTasks()) {
                try {
                    allImages.addAll(logoGeneratorTool.generateLogos(task.description()));
                } catch (Exception e) {
                    log.warn("Logo生成失败: {}", e.getMessage());
                }
            }
        }
        return allImages;
    }

    /**
     * 执行图片收集（复用 ImageCollectorNode 的并发逻辑）
     *
     * @param prompt 用户提示词
     * @return 收集到的图片资源列表
     */
    private List<ImageResource> doCollectImages(String prompt) {
        List<com.it.aizerocoder.langgraph4j.model.ImageResource> collectedImages = new ArrayList<>();

        try {
            // 第一步：AI规划收集任务
            ImageCollectionPlan plan = imageCollectionPlanService.planImageCollection(prompt);
            log.info("获取到图片收集计划，开始并发执行");

            // 第二步：并发执行各种图片收集任务
            List<CompletableFuture<List<com.it.aizerocoder.langgraph4j.model.ImageResource>>> futures = new ArrayList<>();

            // 并发执行内容图片搜索
            if (plan.getContentImageTasks() != null) {
                for (ImageCollectionPlan.ImageSearchTask task : plan.getContentImageTasks()) {
                    futures.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            return imageSearchTool.searchContentImages(task.query());
                        } catch (Exception e) {
                            log.warn("内容图片搜索失败: {}", e.getMessage());
                            return Collections.emptyList();
                        }
                    }));
                }
            }

            // 并发执行插画图片搜索
            if (plan.getIllustrationTasks() != null) {
                for (ImageCollectionPlan.IllustrationTask task : plan.getIllustrationTasks()) {
                    futures.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            return undrawIllustrationTool.searchIllustrations(task.query());
                        } catch (Exception e) {
                            log.warn("插画图片搜索失败: {}", e.getMessage());
                            return Collections.emptyList();
                        }
                    }));
                }
            }

            // 并发执行架构图生成
            if (plan.getDiagramTasks() != null) {
                for (ImageCollectionPlan.DiagramTask task : plan.getDiagramTasks()) {
                    futures.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            return mermaidDiagramTool.generateMermaidDiagram(task.mermaidCode(), task.description());
                        } catch (Exception e) {
                            log.warn("架构图生成失败: {}", e.getMessage());
                            return Collections.emptyList();
                        }
                    }));
                }
            }

            // 并发执行Logo生成
            if (plan.getLogoTasks() != null) {
                for (ImageCollectionPlan.LogoTask task : plan.getLogoTasks()) {
                    futures.add(CompletableFuture.supplyAsync(() -> {
                        try {
                            return logoGeneratorTool.generateLogos(task.description());
                        } catch (Exception e) {
                            log.warn("Logo生成失败: {}", e.getMessage());
                            return Collections.emptyList();
                        }
                    }));
                }
            }

            // 等待所有任务完成并收集结果
            CompletableFuture<Void> allTasks = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allTasks.join();

            // 收集所有结果
            for (CompletableFuture<List<com.it.aizerocoder.langgraph4j.model.ImageResource>> future : futures) {
                List<com.it.aizerocoder.langgraph4j.model.ImageResource> images = future.get();
                if (images != null) {
                    collectedImages.addAll(images);
                }
            }
            log.info("并发图片收集完成，共收集到 {} 张图片", collectedImages.size());

        } catch (Exception e) {
            log.error("图片收集失败: {}", e.getMessage(), e);
        }

        // 转换为实体类
        return convertToEntities(collectedImages);
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
