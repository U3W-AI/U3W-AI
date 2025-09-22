package com.playwright.utils;

import com.alibaba.fastjson.JSONObject;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.ViewportSize;
import com.microsoft.playwright.options.WaitForSelectorState;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * @author 优立方
 * @version JDK 17
 * @date 2025年03月31日 09:27
 */
@Component
public class ScreenshotUtil {

    @Value("${cube.uploadurl}")
    public String uploadUrl;

    public String screenshotElementAndUpload(Locator locator, String imageName) throws IOException {

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        try {
            // 检查页面是否已关闭
            if (locator.page().isClosed()) {
                return "";
            }

            // 🔥 优化：截取全屏截图，增加超时设置
            locator.screenshot(new Locator.ScreenshotOptions()
                    .setPath(Paths.get(imageName))
                    .setTimeout(45000) // 45秒超时，防止长时间等待
            );


            // 上传截图
            String response = uploadFile(uploadUrl, imageName);
            JSONObject jsonObject = JSONObject.parseObject(response);

            String url = jsonObject.get("url")+"";
            Files.delete(Paths.get(imageName));
            return url;
        } catch (com.microsoft.playwright.impl.TargetClosedError e) {
            return "";
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return "";
        } catch (Exception e) {
            throw e;
        }

    }

    public String screenshotAndUpload(Page page, String imageName) throws IOException {

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        try {
            // 检查页面是否已关闭
            if (page.isClosed()) {
                return "";
            }

            // 🔥 优化：截取全屏截图，增加超时设置
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get(imageName))
                    .setFullPage(true)
                    .setTimeout(45000) // 45秒超时，防止长时间等待
            );


            // 上传截图
            String response = uploadFile(uploadUrl, imageName);
            JSONObject jsonObject = JSONObject.parseObject(response);

            String url = jsonObject.get("url")+"";
            Files.delete(Paths.get(imageName));
            return url;
        } catch (com.microsoft.playwright.impl.TargetClosedError e) {
            return "";
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return "";
        } catch (Exception e) {
            throw e;
        }
    }

    public String screenShootAllDivAndUpload(Page page, String imageName, String bodyPath) throws IOException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        try {
            // 检查页面是否已关闭
            if (page.isClosed()) {
                return "";
            }

            // 等待目标元素加载完成
            Locator targetElement = page.locator(bodyPath);

            // 获取元素的边界信息
            BoundingBox boundingBox = targetElement.boundingBox();
            if (boundingBox == null) {
                System.err.println("无法获取元素边界信息");
                return null;
            }

            ViewportSize viewportSize = page.viewportSize();
            // 设置视口大小以匹配元素尺寸（添加适当的缓冲区）
            page.setViewportSize(
                    (int) Math.ceil(boundingBox.width),
                    (int) Math.ceil(boundingBox.height) * 2
            );

            Path path = Paths.get(imageName);
            // 截取元素的完整屏幕截图
            targetElement.screenshot(new Locator.ScreenshotOptions()
                    .setType(ScreenshotType.PNG)
                    .setPath(path));
            String response = uploadFile(uploadUrl, imageName);
            JSONObject jsonObject = JSONObject.parseObject(response);

            String url = jsonObject.get("url")+"";
            Files.delete(path);
            page.setViewportSize(
                    viewportSize.width,
                    viewportSize.height
            );
            return url;
        } catch (com.microsoft.playwright.impl.TargetClosedError e) {
            return "";
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return "";
        } catch (Exception e) {
            throw e;
        }
    }
    public static String uploadFile(String serverUrl, String filePath) throws IOException {
        OkHttpClient client = new OkHttpClient();
        File file = new File(filePath);

        // 根据文件扩展名自动判断 MIME 类型
        String mimeType;
        if (filePath.toLowerCase().endsWith(".png")) {
            mimeType = "image/png";
        } else if (filePath.toLowerCase().endsWith(".jpg") || filePath.toLowerCase().endsWith(".jpeg")) {
            mimeType = "image/jpeg";
        } else if (filePath.toLowerCase().endsWith(".pdf")) {
            mimeType = "application/pdf";
        } else {
            // 默认纯文本
            mimeType = "text/plain";
        }

        // 构建 Multipart 请求体
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(file, MediaType.parse(mimeType)))
                .build();

        // 构建 HTTP 请求
        Request request = new Request.Builder()
                .url(serverUrl)
                .post(requestBody)
                .build();

        // 发送请求并处理中断异常
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            return response.body().string();
        } catch (java.io.InterruptedIOException e) {
            // 返回一个默认的JSON响应，避免解析失败
            return "{\"url\":\"\",\"status\":\"interrupted\",\"message\":\"文件上传被中断\"}";
        } catch (Exception e) {
            e.printStackTrace();
            // 返回一个默认的JSON响应
            return "{\"url\":\"\",\"status\":\"error\",\"message\":\"文件上传失败: " + e.getMessage() + "\"}";
        }
    }


    public static String downloadAndUploadFile(Page page, String uploadUrl, Runnable downloadTrigger) throws IOException {
        try {
            // 检查页面是否已关闭
            if (page.isClosed()) {
                return "";
            }
        Download download = page.waitForDownload(downloadTrigger);
        Path tmpPath = download.path();
        if (tmpPath == null) {
            throw new IOException("下载文件失败，路径为空");
        }

        String originalName = download.suggestedFilename();
        String extension = "";
        int dotIndex = originalName.lastIndexOf(".");
        if (dotIndex != -1) {
            extension = originalName.substring(dotIndex);
        }

        String uuidFileName = UUID.randomUUID().toString() + extension;
        Path renamedFilePath = tmpPath.resolveSibling(uuidFileName);
        Files.move(tmpPath, renamedFilePath, StandardCopyOption.REPLACE_EXISTING);
        String result = uploadFile(uploadUrl, renamedFilePath.toString());
        Files.deleteIfExists(renamedFilePath);

        JSONObject jsonObject = JSONObject.parseObject(result);
        return jsonObject.getString("url");
        } catch (com.microsoft.playwright.impl.TargetClosedError e) {
            return "";
        } catch (com.microsoft.playwright.PlaywrightException e) {
            return "";
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}
