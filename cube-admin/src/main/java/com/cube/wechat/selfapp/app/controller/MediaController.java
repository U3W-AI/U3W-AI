package com.cube.wechat.selfapp.app.controller;

import com.cube.common.core.controller.BaseController;
import com.cube.common.core.domain.AjaxResult;
import com.cube.wechat.selfapp.app.service.CallWordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 媒体管理控制器
 * 处理媒体相关的业务逻辑
 * 
 * @author fuchen
 * @version 1.0
 * @date 2025-01-14
 */
@RestController
@RequestMapping("/media")
public class MediaController extends BaseController {

    @Autowired
    private CallWordService callWordService;

    /**
     * 获取指定媒体平台的提示词
     * 
     * @param platformId 平台标识（如：wechat_layout, zhihu_layout）
     * @return 提示词内容
     */
    @GetMapping("/getCallWord/{platformId}")
    public AjaxResult getCallWord(@PathVariable String platformId) {
        try {
            String wordContent = callWordService.getCallWord(platformId);
            return AjaxResult.success("获取成功", wordContent);
        } catch (Exception e) {
            logger.error("获取提示词失败", e);
            return AjaxResult.error("获取提示词失败：" + e.getMessage());
        }
    }

    /**
     * 更新指定媒体平台的提示词
     * 
     * @param platformId 平台标识
     * @param wordContent 提示词内容
     * @return 操作结果
     */
    @PostMapping("/updateCallWord/{platformId}")
    public AjaxResult updateCallWord(@PathVariable String platformId, @RequestBody String wordContent) {
        try {
            AjaxResult result = callWordService.saveOrUpdateCallWord(platformId, wordContent);
            return result;
        } catch (Exception e) {
            logger.error("更新提示词失败", e);
            return AjaxResult.error("更新提示词失败：" + e.getMessage());
        }
    }
} 