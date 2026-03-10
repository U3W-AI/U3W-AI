package com.wx.fbsir.business.systemprompt.controller;

import com.wx.fbsir.business.systemprompt.domain.SystemPrompt;
import com.wx.fbsir.business.systemprompt.service.SystemPromptService;
import com.wx.fbsir.common.annotation.Anonymous;
import com.wx.fbsir.common.core.domain.AjaxResult;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/business/prompt")
public class SystemPromptController {
    private static final Logger logger = LoggerFactory.getLogger(SystemPromptController.class);
    
    @Resource
    private SystemPromptService systemPromptService;

    /**
     * 获取系统提示词列表
     */
    @PreAuthorize("@ss.hasPermi('business:prompt:list')")
    @GetMapping("/list")
    public AjaxResult listSystemPrompt() {
        try {
            logger.info("开始获取系统提示词列表");
            List<SystemPrompt> systemPromptList = systemPromptService.selectSystemPromptList();
            logger.info("获取系统提示词列表成功，共{}条", systemPromptList.size());
            return AjaxResult.success(systemPromptList);
        } catch (Exception e) {
            logger.error("获取系统提示词列表失败", e);
            return AjaxResult.error("获取系统提示词列表失败," + e.getMessage());
        }
    }

    /**
     * 获取系统提示词（工作流调用）
     * 直接返回提示词内容，不在外层封装，便于大模型理解
     *
     * @param id 系统提示词 ID
     * @return 系统提示词
     */
    @GetMapping("/system")
    @Anonymous
    public String getPrompt(@RequestParam Long id) {
        try {
            if (id == null) {
                logger.warn("获取系统提示词失败：ID为空");
                return "获取系统提示词失败：ID不能为空";
            }
            logger.info("开始获取系统提示词（工作流调用），ID：{}", id);
            SystemPrompt systemPrompt = systemPromptService.selectSystemPromptById(id);
            if (systemPrompt == null) {
                logger.warn("获取系统提示词失败：提示词不存在，ID：{}", id);
                return "获取系统提示词失败：提示词不存在";
            }
            logger.info("获取系统提示词（工作流调用）成功，ID：{}", id);
            return systemPrompt.getContent();
        } catch (Exception e) {
            logger.error("获取系统提示词（工作流调用）失败，ID：{}", id, e);
            return "获取系统提示词失败";
        }
    }

    /**
     * 获取系统提示词（前端接口调用）
     * 封装一层返回结果
     *
     * @param id 系统提示词 ID
     * @return 系统提示词
     */
    @PreAuthorize("@ss.hasPermi('business:prompt:query')")
    @GetMapping("/get")
    public AjaxResult getSystemPrompt(@RequestParam Long id) {
        try {
            if (id == null) {
                return AjaxResult.error("提示词ID不能为空");
            }
            logger.info("开始获取系统提示词（前端调用），ID：{}", id);
            SystemPrompt systemPrompt = systemPromptService.selectSystemPromptById(id);
            if (systemPrompt == null) {
                return AjaxResult.error("提示词不存在");
            }
            logger.info("获取系统提示词（前端调用）成功，ID：{}", id);
            return AjaxResult.success(systemPrompt);
        } catch (Exception e) {
            logger.error("获取系统提示词（前端调用）失败，ID：{}", id, e);
            return AjaxResult.error("获取系统提示词失败," + e.getMessage());
        }

    }

    /**
     * 插入系统提示词
     *
     * @param systemPrompt 系统提示词
     * @return 插入结果
     */
    @PreAuthorize("@ss.hasPermi('business:prompt:add')")
    @PostMapping("/insert")
    public AjaxResult insertSystemPrompt(@RequestBody SystemPrompt systemPrompt) {
        try {
            if (systemPrompt == null) {
                return AjaxResult.error("提示词信息不能为空");
            }
            if (systemPrompt.getName() == null || systemPrompt.getName().trim().isEmpty()) {
                return AjaxResult.error("提示词名称不能为空");
            }
            if (systemPrompt.getContent() == null || systemPrompt.getContent().trim().isEmpty()) {
                return AjaxResult.error("提示词内容不能为空");
            }
            logger.info("开始插入系统提示词，名称：{}", systemPrompt.getName());
            boolean result = systemPromptService.insertSystemPrompt(systemPrompt);
            if (result) {
                logger.info("插入系统提示词成功，名称：{}", systemPrompt.getName());
                return AjaxResult.success("插入成功");
            } else {
                logger.warn("插入系统提示词失败，名称：{}", systemPrompt.getName());
                return AjaxResult.error("插入失败");
            }
        } catch (Exception e) {
            logger.error("插入系统提示词失败", e);
            return AjaxResult.error("插入系统提示词失败" + e.getMessage());
        }
    }

    /**
     * 更新系统提示词
     *
     * @param systemPrompt 系统提示词
     * @return 更新结果
     */
    @PreAuthorize("@ss.hasPermi('business:prompt:edit')")
    @PostMapping("/update")
    public AjaxResult updateSystemPrompt(@RequestBody SystemPrompt systemPrompt) {
        try {
            if (systemPrompt == null) {
                return AjaxResult.error("提示词信息不能为空");
            }
            if (systemPrompt.getId() == null) {
                return AjaxResult.error("提示词ID不能为空");
            }
            if (systemPrompt.getName() == null || systemPrompt.getName().trim().isEmpty()) {
                return AjaxResult.error("提示词名称不能为空");
            }
            if (systemPrompt.getContent() == null || systemPrompt.getContent().trim().isEmpty()) {
                return AjaxResult.error("提示词内容不能为空");
            }
            logger.info("开始更新系统提示词，ID：{}，名称：{}", systemPrompt.getId(), systemPrompt.getName());
            boolean result = systemPromptService.updateSystemPrompt(systemPrompt);
            if (result) {
                logger.info("更新系统提示词成功，ID：{}，名称：{}", systemPrompt.getId(), systemPrompt.getName());
                return AjaxResult.success("更新成功");
            } else {
                logger.warn("更新系统提示词失败，ID：{}，名称：{}", systemPrompt.getId(), systemPrompt.getName());
                return AjaxResult.error("更新失败");
            }
        } catch (Exception e) {
            logger.error("更新系统提示词失败", e);
            return AjaxResult.error("更新系统提示词失败" + e.getMessage());
        }
    }

    /**
     * 删除系统提示词
     *
     * @param id 系统提示词 ID
     * @return 删除结果
     */
    @PreAuthorize("@ss.hasPermi('business:prompt:remove')")
    @PostMapping("/delete")
    public AjaxResult deleteSystemPrompt(@RequestParam Long id) {
        try {
            if (id == null) {
                return AjaxResult.error("提示词ID不能为空");
            }
            logger.info("开始删除系统提示词，ID：{}", id);
            boolean result = systemPromptService.deleteSystemPrompt(id);
            if (result) {
                logger.info("删除系统提示词成功，ID：{}", id);
                return AjaxResult.success("删除成功");
            } else {
                logger.warn("删除系统提示词失败，ID：{}", id);
                return AjaxResult.error("删除失败");
            }
        } catch (Exception e) {
            logger.error("删除系统提示词失败，ID：{}", id, e);
            return AjaxResult.error("删除系统提示词失败" + e.getMessage());
        }
    }
}
