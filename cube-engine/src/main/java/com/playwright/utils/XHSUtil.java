package com.playwright.utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.playwright.entity.ImageTextRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


//

@Component
public class XHSUtil {
    /**
     * 检测小红书登录状态并获取用户名
     * @param page Playwright页面实例
     * @return 用户名或"false"
     */
    public String checkLoginStatus(Page page)
    {
        try {
            // 检查用户头像和用户名（最常见的方式）
            // 知乎登录后，右上角通常有用户头像
            Thread.sleep(1000);
            Locator avatarArea = page.locator(".name-box");
            if (avatarArea.count() > 0) {
//                System.out.println("发现用户头像区域");
                // 点击用户头像，然后点击用户主页按钮
//                avatarArea.first().click();
                Thread.sleep(1000);
                String userName = page.locator(".name-box").textContent();

                return userName;
            }

            // 检查当前URL是否跳转到登录页面
            String currentUrl = page.url();
            if (currentUrl.contains("signin") || currentUrl.contains("login")) {
                System.out.println("页面跳转到登录页面，用户未登录");
                return "false";
            }


            return "false";

        } catch (Exception e) {
            System.out.println("登录状态检测异常: " + e.getMessage());
            return "false";
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
                List<String> lines = new ArrayList<>(Arrays.asList(textSegments.get(segmentIndex).split("[\\r\\n]")));

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
                String outputFilePath = generateOutputPath(request.getOutputPath(), segmentIndex + 1);
                File outputFile = new File(outputFilePath);
                ImageIO.write(image, getFileExtension(request.getImagePath()), outputFile);
                outputPaths.add(outputFilePath);
                System.out.println("已生成图片：" + outputFile.getAbsolutePath());
                g2d.dispose();
                segmentIndex++;
            }
            //返回结果
            return outputPaths;
        } catch (IOException e) {
            System.out.println( "写入图片失败：" + e.getMessage());
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
        return baseName + "_" + index + "." + extension;
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
