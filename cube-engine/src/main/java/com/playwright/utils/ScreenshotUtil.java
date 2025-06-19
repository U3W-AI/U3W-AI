package com.playwright.utils;

import com.alibaba.fastjson.JSONObject;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
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
    private String uploadUrl;

    public String screenshotAndUpload(Page page, String imageName){

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        try {
            // 截取全屏截图
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get(imageName))
                    .setFullPage(true)
            );

            System.out.println("当前时间：" + simpleDateFormat.format(new Date()));

            // 上传截图
            String response = uploadFile(uploadUrl, imageName);
            JSONObject jsonObject = JSONObject.parseObject(response);

            String url = jsonObject.get("url")+"";
            Files.delete(Paths.get(imageName));
            return url;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";

    }

    public static String uploadFile(String serverUrl, String filePath) throws IOException {
        System.out.println("原文件："+filePath);
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
            // 默认二进制流
            mimeType = "application/pdf";
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

        // 发送请求
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            return response.body().string();
        }
    }


    public static String downloadAndUploadFile(Page page, String uploadUrl, Runnable downloadTrigger) throws IOException {
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
    }
}
