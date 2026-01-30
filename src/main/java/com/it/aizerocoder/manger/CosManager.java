package com.it.aizerocoder.manger;

import cn.hutool.core.util.StrUtil;
import com.it.aizerocoder.config.CosClientConfig;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * COS对象存储管理器
 *
 * @author yupi
 */
@Component
@Slf4j
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传对象
     *
     * @param key  唯一键
     * @param file 文件
     * @return 上传结果
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 上传文件到 COS 并返回访问 URL
     *
     * @param key  COS对象键（完整路径）
     * @param file 要上传的文件
     * @return 文件的访问URL，失败返回null
     */
    public String uploadFile(String key, File file) {
        // 上传文件
        PutObjectResult result = putObject(key, file);
        if (result != null) {
            // 构建访问URL
            String url = String.format("%s%s", cosClientConfig.getHost(), key);
            log.info("文件上传COS成功: {} -> {}", file.getName(), url);
            return url;
        } else {
            log.error("文件上传COS失败，返回结果为空");
            return null;
        }
    }

    /**
     * 从 COS 删除对象
     *
     * @param key 唯一键（完整路径）
     * @return 是否删除成功
     */
    public boolean deleteObject(String key) {
        try {
            cosClient.deleteObject(cosClientConfig.getBucket(), key);
            log.info("文件从COS删除成功: {}", key);
            return true;
        } catch (Exception e) {
            log.error("文件从COS删除失败: {}, 错误: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 从 URL 中提取 COS key 并删除文件
     *
     * @param url COS文件的完整访问URL
     * @return 是否删除成功
     */
    public boolean deleteFileByUrl(String url) {
        if (StrUtil.isBlank(url)) {
            log.warn("删除文件失败: URL为空");
            return false;
        }
        try {
            // 从URL中提取key（去掉host部分）
            String host = cosClientConfig.getHost();
            if (url.startsWith(host)) {
                String key = url.substring(host.length());
                return deleteObject(key);
            } else {
                log.warn("URL不匹配COS host: {}", url);
                return false;
            }
        } catch (Exception e) {
            log.error("从URL删除文件失败: {}, 错误: {}", url, e.getMessage());
            return false;
        }
    }

}
