package com.it.aizerocoder.langgraph4j.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.it.aizerocoder.langgraph4j.enums.ImageCategoryEnum;
import com.it.aizerocoder.langgraph4j.model.ImageResource;
import com.it.aizerocoder.manger.CosManager;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;

/**
 * Logo 图片生成工具
 */
@Slf4j
@Component
public class LogoGeneratorTool {

    @Value("${dashscope.api-key:}")
    private String dashScopeApiKey;

    @Value("${dashscope.image-model:wan2.2-t2i-flash}")
    private String imageModel;

    @Resource
    private CosManager cosManager;

    //使用wan2.2-t2i-flash模型代码
   /* @Tool("根据描述生成 Logo 设计图片，用于网站品牌标识")
    public List<ImageResource> generateLogos(@P("Logo 设计描述，如名称、行业、风格等，尽量详细") String description) {
        List<ImageResource> logoList = new ArrayList<>();
        try {
            // 构建 Logo 设计提示词
            String logoPrompt = String.format("生成 Logo，Logo 中禁止包含任何文字！Logo 介绍：%s", description);
            ImageSynthesisParam param = ImageSynthesisParam.builder()
                    .apiKey(dashScopeApiKey)
                    .model(imageModel)
                    .prompt(logoPrompt)
                    .size("512*512")
                    .n(1) // 生成 1 张足够，因为 AI 不知道哪张最好
                    .build();
            ImageSynthesis imageSynthesis = new ImageSynthesis();
            ImageSynthesisResult result = imageSynthesis.call(param);
            if (result != null && result.getOutput() != null && result.getOutput().getResults() != null) {
                List<Map<String, String>> results = result.getOutput().getResults();
                for (Map<String, String> imageResult : results) {
                    String imageUrl = imageResult.get("url");
                    String cosUrl = downloadAndUploadToCos(imageUrl, description);
                    if (StrUtil.isNotBlank(imageUrl)) {
                        logoList.add(ImageResource.builder()
                                .category(ImageCategoryEnum.LOGO)
                                .description(description)
                                .url(cosUrl)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("生成 Logo 失败: {}", e.getMessage(), e);
        }
        return logoList;
    }*/

    //使用MultiModalConversation模型代码
    @Tool("根据描述生成 Logo 设计图片，用于网站品牌标识")
    public List<ImageResource> generateLogos(@P("Logo 设计描述，如名称、行业、风格等，尽量详细") String description) {
        List<ImageResource> logoList = new ArrayList<>();
        try {
            // 构建 Logo 设计提示词
            String logoPrompt = String.format("生成 Logo，Logo 中禁止包含任何文字！Logo 介绍：%s", description);

            // 构建 MultiModalMessage 消息格式（
            MultiModalMessage userMessage = MultiModalMessage.builder()
                    .role(Role.USER.getValue())
                    .content(Arrays.asList(
                            Collections.singletonMap("text", logoPrompt)
                    ))
                    .build();

            // 构建 parameters 参数
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("watermark", false);           // 不添加水印
            parameters.put("prompt_extend", true);        // 扩展提示词
            parameters.put("negative_prompt", "");        // 负面提示词（可选）
            parameters.put("size", "512*512");            // 图片尺寸，可改为 "1328*1328" 或 "1664*928"

            // 构建参数
            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .apiKey(dashScopeApiKey)
                    .model(imageModel)
                    .messages(Collections.singletonList(userMessage))
                    .parameters(parameters)  // 使用 parameters 而不是单独的参数
                    .build();

            //调用 API
            MultiModalConversation conv = new MultiModalConversation();
            MultiModalConversationResult result = conv.call(param);

            //解析响应
            if (result != null && result.getOutput() != null) {
                // 从 choices 中提取图片
                if (result.getOutput().getChoices() != null && !result.getOutput().getChoices().isEmpty()) {
                    var choices = result.getOutput().getChoices();
                    for (var choice : choices) {
                        if (choice.getMessage() != null && choice.getMessage().getContent() != null) {
                            for (var content : choice.getMessage().getContent()) {
                                if (content instanceof Map) {
                                    Map<String, Object> contentMap = (Map<String, Object>) content;
                                    String imageUrl = extractImageUrl(contentMap);
                                    if (StrUtil.isNotBlank(imageUrl)) {
                                        String cosUrl = downloadAndUploadToCos(imageUrl, description);
                                        if (StrUtil.isNotBlank(cosUrl)) {
                                            logoList.add(ImageResource.builder()
                                                    .category(ImageCategoryEnum.LOGO)
                                                    .description(description)
                                                    .url(cosUrl)
                                                    .build());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("生成 Logo 失败: {}", e.getMessage(), e);
        }
        return logoList;
    }

    //辅助方法：提取图片 URL
    private String extractImageUrl(Map<String, Object> contentMap) {
        // 尝试多种可能的字段名
        String[] possibleKeys = {"image", "image_url", "url", "imageUrl"};
        for (String key : possibleKeys) {
            Object value = contentMap.get(key);
            if (value instanceof String && StrUtil.isNotBlank((String) value)) {
                return (String) value;
            }
        }
        return null;
    }

    /**
     * 下载远程图片到本地，上传到 COS，然后清理本地文件
     *
     * @param imageUrl    远程图片 URL
     * @param description 图片描述（用于日志）
     * @return COS 访问 URL，失败返回 null
     */
    private String downloadAndUploadToCos(String imageUrl, String description) {
        File tempFile = null;
        try {
            // 1. 创建临时文件
            tempFile = FileUtil.createTempFile("logo_", ".png", true);
            log.info("开始下载 Logo 图片: {} -> {}", imageUrl, tempFile.getAbsolutePath());

            // 2. 下载远程图片到本地临时文件
            HttpUtil.downloadFile(imageUrl, tempFile);

            // 3. 检查文件是否下载成功
            if (!tempFile.exists() || tempFile.length() == 0) {
                log.error("Logo 图片下载失败或文件为空: {}", imageUrl);
                return null;
            }
            log.info("Logo 图片下载成功，文件大小: {} bytes", tempFile.length());

            // 4. 上传到 COS
            String keyName = String.format("/logo/%s/%s",
                    RandomUtil.randomString(5), tempFile.getName());
            String cosUrl = cosManager.uploadFile(keyName, tempFile);

            if (StrUtil.isNotBlank(cosUrl)) {
                log.info("Logo 上传 COS 成功: {} -> {}", description, cosUrl);
                return cosUrl;
            } else {
                log.error("Logo 上传 COS 失败: {}", description);
                return null;
            }
        } catch (Exception e) {
            log.error("下载或上传 Logo 失败: {}, 错误: {}", imageUrl, e.getMessage(), e);
            return null;
        } finally {
            // 5. 清理本地临时文件
            if (tempFile != null && tempFile.exists()) {
                FileUtil.del(tempFile);
                log.info("已清理临时文件: {}", tempFile.getAbsolutePath());
            }
        }
    }
}
