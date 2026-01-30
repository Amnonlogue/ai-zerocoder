package com.it.aizerocoder.service;

/**
 * 截图服务
 */
public interface ScreenshotService {


    /**
     * 通用的截图服务,可以得到访问地址
     * @param webUrl
     * @return
     */
    String generateAndUploadScreenshot(String webUrl);

    /**
     * 删除COS上的截图
     *
     * @param screenshotUrl 截图URL
     * @return 是否删除成功
     */
    boolean deleteScreenshot(String screenshotUrl);

}
