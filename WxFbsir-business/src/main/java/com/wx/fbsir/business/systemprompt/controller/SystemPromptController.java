package com.wx.fbsir.business.systemprompt.controller;

import com.wx.fbsir.business.systemprompt.domain.SystemPrompt;
import com.wx.fbsir.business.systemprompt.service.SystemPromptService;
import com.wx.fbsir.common.annotation.Anonymous;
import com.wx.fbsir.common.core.domain.AjaxResult;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/business/prompt")
public class SystemPromptController {
    @Resource
    private SystemPromptService messageService;

    /**
     * 获取系统提示词（工作流调用）
     *
     * @param id 系统提示词 ID
     * @return 系统提示词
     */
    @GetMapping("/system")
    @Anonymous
    public String getPrompt(@RequestParam Long id) {
        try {
            SystemPrompt systemPrompt = messageService.selectSystemPromptById(id);
            return systemPrompt.getContent();
        } catch (Exception e) {
            return "获取系统提示词失败";
        }
    }

    /**
     * 获取系统提示词（前端接口调用）
     *
     * @param id 系统提示词 ID
     * @return 系统提示词
     */
    @GetMapping("/get")
    @Anonymous
    public AjaxResult getSystemPrompt(@RequestParam Long id) {
        try {
            SystemPrompt systemPrompt = messageService.selectSystemPromptById(id);
            return AjaxResult.success(systemPrompt);
        } catch (Exception e) {
            return AjaxResult.error("获取系统提示词失败," + e.getMessage());
        }

    }

    /**
     * 插入系统提示词
     *
     * @param systemPrompt 系统提示词
     * @return 插入结果
     */
    @PostMapping("/insert")
    @Anonymous
    public AjaxResult insertSystemPrompt(@RequestBody SystemPrompt systemPrompt) {
        try {
            boolean result = messageService.insertSystemPrompt(systemPrompt);
            return result ? AjaxResult.success("插入成功") : AjaxResult.error("插入失败");
        } catch (Exception e) {
            return AjaxResult.error("插入系统提示词失败" + e.getMessage());
        }
    }

    /**
     * 更新系统提示词
     *
     * @param systemPrompt 系统提示词
     * @return 更新结果
     */
    @PostMapping("/update")
    @Anonymous
    public AjaxResult updateSystemPrompt(@RequestBody SystemPrompt systemPrompt) {
        try {
            boolean result = messageService.updateSystemPrompt(systemPrompt);
            return result ? AjaxResult.success("更新成功") : AjaxResult.error("更新失败");
        } catch (Exception e) {
            return AjaxResult.error("更新系统提示词失败" + e.getMessage());
        }
    }

    /**
     * 删除系统提示词
     *
     * @param id 系统提示词 ID
     * @return 删除结果
     */
    @PostMapping("/delete")
    @Anonymous
    public AjaxResult deleteSystemPrompt(@RequestParam Long id) {
        try {
            boolean result = messageService.deleteSystemPrompt(id);
            return result ? AjaxResult.success("删除成功") : AjaxResult.error("删除失败");
        } catch (Exception e) {
            return AjaxResult.error("删除系统提示词失败" + e.getMessage());
        }
    }
}
