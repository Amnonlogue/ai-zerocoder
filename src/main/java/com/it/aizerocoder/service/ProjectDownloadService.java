package com.it.aizerocoder.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 下载代码服务
 */
public interface ProjectDownloadService {

    void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);
}
