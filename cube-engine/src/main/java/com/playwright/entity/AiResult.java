package com.playwright.entity;

import lombok.Data;

/**
 * @author muyou
 * description:  ai执行结果
 * dateStart 2024/8/4 9:34
 * dateNow   2025/9/27 15:16
 */
@Data
public class AiResult {
    private String htmlContent;

    private String textContent;
    public static AiResult success(String htmlContent, String textContent) {
        AiResult aiResult = new AiResult();
        aiResult.setHtmlContent(htmlContent);
        aiResult.setTextContent(textContent);
        return aiResult;
    }
}
