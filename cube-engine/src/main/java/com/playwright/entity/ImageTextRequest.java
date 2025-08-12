package com.playwright.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author Moyesan
 * Create on 2025/8/9
 *
 */

@Data
@Schema(name = "ImageTextRequest", description = "用于小红书图文生成")
public class ImageTextRequest {
    @Schema(description = "输入图片路径", example = "/path/to/image.jpg")
    private String imagePath;

    @Schema(description = "输出图片路径", example = "/path/to/outputImage.jpg")
    private String outputPath;

    @Schema(description = "要添加的文字（支持换行、空格和标记）", example = "Text")
    private String text;

    @Schema(description = "字体名称，默认宋体", example = "SimSun")
    private String fontName = "SimSun";

    @Schema(description = "字体大小，默认20", example = "30")
    private Integer fontSize = 20;

    @Schema(description = "字体样式，默认PLAIN", example = "PLAIN BOLD ITALIC")
    private String fontStyle = "PLAIN";

    @Schema(description = "输入图片路径", example = "10")
    private Integer x1 = 10;

    @Schema(description = "文本区域左上角y坐标", example = "50")
    private Integer y1 = 50;

    @Schema(description = "文本区域右下角x坐标（0表示图片宽度）", example = "0")
    private Integer x2 = 0;

    @Schema(description = "文本区域右下角y坐标（0表示图片高度）", example = "0")
    private Integer y2 = 0;

    @Schema(description = "对齐方式，默认LEFT", example = "LEFT左对齐 CENTER居中 REIGHT右对齐")
    private String alignment = "LEFT";

    @Schema(description = "分割标记，默认#split#", example = "#split#")
    private String splitMarker = "#split#";

    @Schema(description = "字符间距（像素）", example = "0.0")
    private Float charSpacing = 1.0f;

    @Schema(description = "行距倍数（1.0=正常，1.5=1.5倍）", example = "1.0")
    private Float lineSpacing = 1.0f;
}
