package com.cube.wechat.selfapp.app.service.impl;

import com.cube.common.core.domain.AjaxResult;
import com.cube.wechat.selfapp.app.domain.CallWord;
import com.cube.wechat.selfapp.app.mapper.CallWordMapper;
import com.cube.wechat.selfapp.app.service.CallWordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 提示词服务实现类
 * 
 * @author fuchen
 * @version 1.0
 * @date 2025-01-14
 */
@Service
public class CallWordServiceImpl implements CallWordService {

    @Autowired
    private CallWordMapper callWordMapper;

    @Override
    public String getCallWord(String platformId) {
        try {
            CallWord callWord = callWordMapper.getCallWordById(platformId);
            return callWord != null ? callWord.getWordContent() : getBackupPrompt(platformId);
        } catch (Exception e) {
            return getBackupPrompt(platformId);
        }
    }

    @Override
    public AjaxResult saveOrUpdateCallWord(String platformId, String wordContent) {
        try {
            CallWord callWord = new CallWord();
            callWord.setPlatformId(platformId);
            callWord.setWordContent(wordContent);
            
            CallWord existing = callWordMapper.getCallWordById(platformId);
            if (existing != null) {
                callWordMapper.updateCallWord(callWord);
            } else {
                callWordMapper.insertCallWord(callWord);
            }
            
            return AjaxResult.success("保存成功");
        } catch (Exception e) {
            return AjaxResult.error("保存失败：" + e.getMessage());
        }
    }

    @Override
    public AjaxResult updateDraftZhihuStatus(Long draftId, boolean isPushed) {
        try {
            callWordMapper.updateDraftZhihuStatus(draftId, isPushed ? 1 : 0);
            return AjaxResult.success("状态更新成功");
        } catch (Exception e) {
            return AjaxResult.error("状态更新失败：" + e.getMessage());
        }
    }

    @Override
    public String generateZhihuTitle(String aiName, String userId, int num) {
        try {
            String template = getCallWord("zhihu_title");
            if (template == null || template.trim().isEmpty()) {
                template = "{aiName}创作-{date}-{num}";
            }
            
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            return template.replace("{aiName}", aiName)
                          .replace("{userId}", userId)
                          .replace("{date}", date)
                          .replace("{num}", String.format("%03d", num));
        } catch (Exception e) {
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            return String.format("%s创作-%s-%03d", aiName, date, num);
        }
    }

    /**
     * 获取备用提示词（当数据库查询失败时使用）
     */
    private String getBackupPrompt(String platformId) {
        switch (platformId) {
            case "wechat_layout":
                return "请将以下内容整理为适合微信公众号发布的HTML格式文章。要求：\n" +
                       "1. 使用适当的HTML标签进行格式化\n" +
                       "2. 重要信息使用<strong>加粗</strong>标记\n" +
                       "3. 代码块使用<pre><code>标记\n" +
                       "4. 列表使用<ul><li>或<ol><li>格式\n" +
                       "5. 段落使用<p>标记，确保良好的可读性\n" +
                       "6. 目标是用于微信公众号\"草稿箱接口\"的 content 字段\n" +
                       "7. 删除不必要的格式标记，保持内容简洁\n\n" +
                       "请对以下内容进行排版：";
            
            case "zhihu_layout":
                return "请将以下内容整理为适合知乎发布的Markdown格式文章。要求：\n" +
                       "1. 保持内容的专业性和可读性\n" +
                       "2. 使用合适的标题层级（## ### #### 等）\n" +
                       "3. 代码块使用```标记，并指定语言类型\n" +
                       "4. 重要信息使用**加粗**标记\n" +
                       "5. 列表使用- 或1. 格式\n" +
                       "6. 删除不必要的格式标记\n" +
                       "7. 确保内容适合知乎的阅读习惯\n" +
                       "8. 文章结构清晰，逻辑连贯\n" +
                       "9. 目标是作为一篇专业文章投递到知乎草稿箱\n\n" +
                       "请对以下内容进行排版：";
            
            default:
                return "请对以下内容进行排版：";
        }
    }
} 