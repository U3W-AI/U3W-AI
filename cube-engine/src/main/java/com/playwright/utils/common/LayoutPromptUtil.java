package com.playwright.utils.common;

import com.alibaba.fastjson.JSONObject;
import com.playwright.entity.CallWord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


/**
 * @author muyou
 * dateStart 2024/8/4 9:34
 * dateNow   2025/9/27 14:16
 */
@Component
public class LayoutPromptUtil {
    @Value("${cube.url}")
    private String url;

    public String getLayoutPrompt(String layoutName) throws Exception {
        try {
            String json = HttpUtil.doGet(url.substring(0, url.lastIndexOf("/")) + "/media/getCallWord/" + layoutName, null);
            JSONObject jsonObject = JSONObject.parseObject(json);
            Object o = jsonObject.get("data");
            CallWord callWord = JSONObject.parseObject(o.toString(), CallWord.class);
            if (callWord == null || callWord.getWordContent() == null || callWord.getWordContent().isEmpty()) {
                return "";
            }
            return callWord.getWordContent();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
