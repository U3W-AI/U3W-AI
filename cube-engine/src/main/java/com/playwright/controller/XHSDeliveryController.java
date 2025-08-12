package com.playwright.controller;

import com.alibaba.fastjson.JSONObject;
import com.playwright.entity.ImageTextRequest;
import com.playwright.entity.UserInfoRequest;
import com.playwright.utils.LogMsgUtil;
import com.playwright.utils.ScreenshotUtil;
import com.playwright.websocket.WebSocketClientService;
import okio.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 小红书投递控制器
 * 统一处理小红书内容的排版和投递功能
 * @author Moyesan
 * @version JDK 17
 * @date 2025年 8月 8日 19:01
 */
@RestController
@RequestMapping("/api/browser")
@Tag(name = "小红书投递控制器", description = "统一处理小红书内容的排版和投递功能")
public class XHSDeliveryController {
    /*  bug驱散符  */

    // 依赖注入
    private final WebSocketClientService webSocketClientService;

    @Autowired
    private AIGCController aigcController;

    @Autowired
    private MediaController mediaController;

    @Autowired
    private LogMsgUtil logInfo;

    @Value("${cube.url}")
    private String url;

    @Value("${cube.datadir}")
    private String inputimg;


    // 构造器注入WebSocket服务
    public XHSDeliveryController(WebSocketClientService webSocketClientService) {
        this.webSocketClientService = webSocketClientService;
    }

    /**
     * 统一的小红书投递处理方法
     * 包含智能排版和小红书投递的完整流程
     * @param userInfoRequest 用户信息请求体
     * @return 处理结果
     */
    @PostMapping("/deliverToXHS")
    @Operation(summary = "投递内容到小红书", description = "处理小红书内容的排版和投递")
    public String deliverToXHS(@RequestBody UserInfoRequest userInfoRequest) {
        String userId = userInfoRequest.getUserId();
        String aiName = userInfoRequest.getAiName();

        try {
            // 1. 发送开始消息
            sendTaskLog("投递到小红书", "开始小红书内容排版...", userId);

            // 2. 内部调用智能排版
            logInfo.sendMediaTaskLog("正在调用豆包进行智能排版...", userId, "投递到小红书");
            String layoutResult = callInternalLayout(userInfoRequest);

            if (layoutResult == null || layoutResult.trim().isEmpty()) {
                logInfo.sendMediaTaskLog("排版失败，无法继续投递", userId, "投递到小红书");
                sendDeliveryResult("投递到小红书", "error", "排版失败", userId);
                return "error";
            }

            logInfo.sendMediaTaskLog("内容排版完成，准备投递...", userId, "投递到小红书");

            // 3. 构建小红书文章标题
            String title = buildXHSTitle(layoutResult);
            logInfo.sendMediaTaskLog("标题构建完成：" + title, userId, "投递到小红书");

            // 4.生成小红书笔记图片

            //设置图文格式和排版，二期进行用户自定义图文格式开发
            ImageTextRequest imageTextRequest = new ImageTextRequest();


            //imageTextRequest.setImagePath(inputimg + "/xiaohongshu_text_bg.jpg");
            String imageUrl = "https://u3w.com/chatfile/xiaohongshu.png";
            InputStream byteArrayInputStream = getImageStreamByHttpClient(imageUrl);

            if(!Files.exists(Paths.get(inputimg + "/xhs_img/"))){
                Files.createDirectories(Paths.get(inputimg + "/xhs_img/"));
            }
            File file = new File(inputimg + "/xhs_img/" + "xiaohongshu.jpg");
            OutputStream outputStream = new FileOutputStream(file);
            int len = 0;
            byte[] buf = new byte[1024];
            while ((len = byteArrayInputStream.read(buf)) != -1) {
                outputStream.write(buf, 0, len);
            }


            imageTextRequest.setImagePath(inputimg + "/xhs_img/" + "xiaohongshu.jpg");
            imageTextRequest.setOutputPath("xhs_img_" + (int)(Math.random() * 1000000)+ ".jpg");
            imageTextRequest.setText(layoutResult);
            imageTextRequest.setFontSize(25);
            imageTextRequest.setFontStyle("BOLD");
            imageTextRequest.setX1(150);
            imageTextRequest.setY1(210);
            imageTextRequest.setX2(920);
            imageTextRequest.setY2(1220);
            imageTextRequest.setCharSpacing(1.0f);
            imageTextRequest.setLineSpacing(1.6f);

            List<String> imgs = addMultilineTextToImage(imageTextRequest);

            // 4. 内部调用小红书投递
            logInfo.sendMediaTaskLog("正在投递内容到小红书平台...", userId, "投递到小红书");
            String deliveryResult = mediaController.sendToXHS(userId, title, layoutResult,imgs);


            if ("true".equals(deliveryResult)) {
                logInfo.sendMediaTaskLog("小红书投递完成！", userId, "投递到小红书");
                sendDeliveryResult("投递到小红书", "success", "小红书投递任务完成", userId);
            } else {
                logInfo.sendMediaTaskLog("小红书投递失败", userId, "投递到小红书");
                sendDeliveryResult("投递到小红书", "error", "小红书投递失败", userId);
            }

            return deliveryResult;
        } catch (Exception e) {
            String errorMsg = "投递失败";
            logInfo.sendMediaTaskLog(errorMsg, userId, "投递到小红书");
            sendDeliveryResult("投递到小红书", "error", errorMsg, userId);
            return "error";
        }
    }


    //感谢 Forever 大佬友情赞助的方法
    private static InputStream getImageStreamByHttpClient(String imageUrl) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
            );

            if (response.statusCode() == 200) {
                return response.body();
            }
        } catch (IOException | InterruptedException e) {
            return null;
        }
        return null;
    }

    /**
     * 内部调用智能排版功能
     * @param userInfoRequest 用户请求信息
     * @return 排版后的内容
     */
    private String callInternalLayout(UserInfoRequest userInfoRequest) {
        try {
            // 设置角色为豆包排版
            userInfoRequest.setRoles("db");

            // 调用AIGCController的内部排版方法
            String layoutResult = aigcController.startDBInternal(userInfoRequest);

            return layoutResult;
        } catch (Exception e) {
            logInfo.sendTaskLog("智能排版调用失败", userInfoRequest.getUserId(), "投递到小红书");
            return null;
        }
    }

    /**
     * 构建小红书文章标题
     * 格式：排版后的第一句
     * @param text 排版后的文字内容
     * @return 构建的标题
     */
    private String buildXHSTitle(String text) {
        try {
            String[] strings = text.split("\n");
            for(int i = 0;i < strings.length;i++){
                if(strings[i] != null && strings[i].length() <= 20){
                    return strings[i];
                }
            }
            return "小红书文章";
        } catch (Exception e) {
            // 如果标题构建失败，使用默认标题
            return "小红书文章";
        }
    }

    /**
     * 发送任务日志消息
     * @param aiName AI名称
     * @param content 日志内容
     * @param userId 用户ID
     */
    private void sendTaskLog(String aiName, String content, String userId) {
        try {
            JSONObject message = new JSONObject();
            message.put("type", "RETURN_PC_TASK_LOG");
            message.put("aiName", aiName);
            message.put("content", content);
            message.put("userId", userId);
            message.put("timestamp", System.currentTimeMillis());

            webSocketClientService.sendMessage(message.toJSONString());
        } catch (Exception e) {
            System.err.println("发送任务日志失败");
        }
    }

    /**
     * 发送投递完成结果消息
     * @param aiName AI名称
     * @param status 状态（success/error）
     * @param message 结果消息
     * @param userId 用户ID
     */
    private void sendDeliveryResult(String aiName, String status, String message, String userId) {
        try {
            JSONObject resultMessage = new JSONObject();
            resultMessage.put("type", "RETURN_XHS_DELIVERY_RES");
            resultMessage.put("aiName", aiName);
            resultMessage.put("status", status);
            resultMessage.put("message", message);
            resultMessage.put("userId", userId);
            resultMessage.put("timestamp", System.currentTimeMillis());

            webSocketClientService.sendMessage(resultMessage.toJSONString());
        } catch (Exception e) {
            System.err.println("发送投递结果失败");
        }
    }

    /**
     * 将内容排版后写入图片
     * @param request 文本内容，排本需求，图片输入输出位置
     * @return 处理后图片的URL
     */
    public List<String> addMultilineTextToImage(ImageTextRequest request) {
        try {
            // 读取原始图片
            File inputFile = new File(request.getImagePath());
            BufferedImage originalImage = ImageIO.read(inputFile);
            int imageWidth = originalImage.getWidth();
            int imageHeight = originalImage.getHeight();

            // 验证文本区域
            int x1 = request.getX1();
            int y1 = request.getY1();
            int x2 = (request.getX2() <= 0 || request.getX2() > imageWidth) ? imageWidth : request.getX2();
            int y2 = (request.getY2() <= 0 || request.getY2() > imageHeight) ? imageHeight : request.getY2();
            if (x1 >= x2 || y1 >= y2) {
                System.out.println("无效的文本区域：x1 必须小于 x2，y1 必须小于 y2。");
                return null;
            }

            // 设置字体
            int style;
            switch (request.getFontStyle().toUpperCase()) {
                case "BOLD":
                    style = Font.BOLD;
                    break;
                case "ITALIC":
                    style = Font.ITALIC;
                    break;
                default:
                    style = Font.PLAIN;
            }
            Font font = new Font(request.getFontName(), style, request.getFontSize());

            // 创建临时 Graphics2D 获取 FontMetrics
            Graphics2D tempG2d = originalImage.createGraphics();
            tempG2d.setFont(font);
            FontMetrics metrics = tempG2d.getFontMetrics(font);
            tempG2d.dispose();

            // 分割文本   [图片拆分]
            String decodedText = new String(request.getText().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            List<String> textSegments = new ArrayList<>(Arrays.asList(decodedText.split(request.getSplitMarker())));


            // 存储输出路径和警告信息
            List<String> outputPaths = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            int segmentIndex = 0;

            // 处理每个文本段
            while (segmentIndex < textSegments.size()) {
                // 创建新图片
                BufferedImage image = new BufferedImage(imageWidth, imageHeight, originalImage.getType());
                Graphics2D g2d = image.createGraphics();
                g2d.drawImage(originalImage, 0, 0, null);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2d.setFont(font);
                g2d.setColor(Color.BLACK);

                // 按换行符分割当前段并处理自动换行
                List<String> lines = new ArrayList<>(Arrays.asList(textSegments.get(segmentIndex).split("[\\r\\n]+")));

                List<String> currentLines = new ArrayList<>();
                List<String> wrappedLines = new ArrayList<>();
                int currentY = y1;

                // 自动换行处理
                for (String line : lines) {
                    if (line.trim().isEmpty()) {
                        wrappedLines.add(line);
                        continue;
                    }
                    List<String> wrapped = wrapLine(line, metrics, x2 - x1, request.getCharSpacing());
                    wrappedLines.addAll(wrapped);
                }

                // 检查是否适合文本区域
                for (String line : wrappedLines) {
                    if (line.trim().isEmpty()) {
                        if (currentY + (int) (metrics.getHeight() * request.getLineSpacing()) <= y2) {
                            currentLines.add(line);
                            currentY += (int) (metrics.getHeight() * request.getLineSpacing());
                        } else {
                            break; // 高度超出，停止添加
                        }
                        continue;
                    }

                    int lineWidth = calculateLineWidth(line, metrics, request.getCharSpacing());
                    if (currentY + (int) (metrics.getHeight() * request.getLineSpacing()) <= y2) {
                        currentLines.add(line);
                        currentY += (int) (metrics.getHeight() * request.getLineSpacing());
                    } else {
                        // 高度溢出：截断并移到新图片
                        if (!currentLines.isEmpty()) {
                            currentLines.add(line.substring(0, Math.min(line.length(), lineWidth)));
                            warnings.add("图片 " + (segmentIndex + 1) + " 文字被截断");
                        }
                        StringBuilder remainingText = new StringBuilder();
                        int startLine = currentLines.size();
                        for (int i = startLine; i < wrappedLines.size(); i++) {
                            remainingText.append(wrappedLines.get(i)).append("\n");
                        }
                        textSegments.add(segmentIndex + 1,remainingText.toString());
                        break;
                    }
                }

                if (currentLines.isEmpty()) {
                    segmentIndex++;
                    continue;
                }

                // 绘制文字
                currentY = y1;
                for (String line : currentLines) {
                    if (line.trim().isEmpty()) {
                        currentY += (int) (metrics.getHeight() * request.getLineSpacing());
                        continue;
                    }

                    // 计算 x 坐标（根据对齐）
                    int lineWidth = calculateLineWidth(line, metrics, request.getCharSpacing());
                    int drawX = x1;
                    if ("CENTER".equalsIgnoreCase(request.getAlignment())) {
                        drawX = x1 + (x2 - x1 - lineWidth) / 2;
                    } else if ("RIGHT".equalsIgnoreCase(request.getAlignment())) {
                        drawX = x2 - lineWidth;
                    }

                    // 逐字符绘制，应用字符间距
                    float currentX = drawX;
                    for (char c : line.toCharArray()) {
                        g2d.drawString(String.valueOf(c), currentX, currentY);
                        currentX += metrics.charWidth(c) + request.getCharSpacing();
                    }
                    currentY += (int) (metrics.getHeight() * request.getLineSpacing());
                }

                // 保存图片
                String outputFileName = generateOutputPath(request.getOutputPath(), segmentIndex + 1);

                File outputFile = File.createTempFile(outputFileName,".jpg");
                ImageIO.write(image, "jpg", outputFile);
                outputPaths.add(outputFile.getAbsolutePath());
                System.out.println("已生成图片：" + outputFile.getAbsolutePath());
                g2d.dispose();
                segmentIndex++;
            }
            //返回结果
            return outputPaths;
        } catch (IOException e) {

            System.out.println( "写入图片失败");
            return null;
        }
    }

    /**
     * 计算行宽（含字符间距）
     * @param line 文本内容
     * @param metrics 文本格式信息
     * @param charSpacing 字符间距
     * @return 返回该行的宽度
     */
    private int calculateLineWidth(String line, FontMetrics metrics, float charSpacing) {
        int width = 0;
        for (char c : line.toCharArray()) {
            width += metrics.charWidth(c) + charSpacing;
        }
        return width;
    }

    /**
     * 自动换行
     * @param line  文本内容
     * @param metrics 文本格式信息
     * @param maxWidth 最大行宽
     * @param charSpacing 字符间距
     * @return 返回自动换行后的字符串数组
     */
    private List<String> wrapLine(String line, FontMetrics metrics, int maxWidth, float charSpacing) {
        List<String> wrapped = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        int currentWidth = 0;

        for (char c : line.toCharArray()) {
            int charWidth = metrics.charWidth(c) + (int) charSpacing;
            if (currentWidth + charWidth <= maxWidth) {
                currentLine.append(c);
                currentWidth += charWidth;
            } else {
                if (currentLine.length() > 0) {
                    wrapped.add(currentLine.toString());
                    currentLine = new StringBuilder(String.valueOf(c));
                    currentWidth = charWidth;
                } else {
                    // 单字符仍超宽，强制添加
                    wrapped.add(String.valueOf(c));
                    currentWidth = 0;
                }
            }
        }
        if (currentLine.length() > 0) {
            wrapped.add(currentLine.toString());
        }
        return wrapped;
    }

    /**
     * 获取唯一地址
     * @param basePath 图片根目录
     * @param index 图片索引
     * @return 返回图片URL
     */
    private String generateOutputPath(String basePath, int index) {
        String extension = getFileExtension(basePath);
        String baseName = basePath.substring(0, basePath.lastIndexOf("."));
        return baseName + "_" + index; //+ "." + extension;
    }

    /**
     * 辅助方法：获取文件扩展名
     * @param fileName 文件名
     * @return 返回文件后缀
     */
    private String getFileExtension(String fileName) {
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }


}