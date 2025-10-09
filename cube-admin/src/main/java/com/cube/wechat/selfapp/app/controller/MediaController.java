package com.cube.wechat.selfapp.app.controller;

import com.cube.common.core.controller.BaseController;
import com.cube.common.core.domain.AjaxResult;
import com.cube.common.core.page.TableDataInfo;
import com.cube.wechat.selfapp.app.domain.CallWord;
import com.cube.wechat.selfapp.app.domain.PromptTemplate;
import com.cube.wechat.selfapp.app.domain.query.CallWordQuery;
import com.cube.wechat.selfapp.app.mapper.PromptTemplateMapper;
import com.cube.wechat.selfapp.app.service.CallWordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    @Autowired
    private PromptTemplateMapper promptTemplateMapper;

    /**
     * 获取指定媒体平台的提示词
     *
     * @param platformId 平台标识（如：wechat_layout, zhihu_layout）
     * @return 提示词内容
     */
    @GetMapping("/getCallWord/{platformId}")
    public AjaxResult getCallWord(@PathVariable String platformId) {
        try {
            CallWord callWord = callWordService.getCallWord(platformId);
            return AjaxResult.success("获取成功", callWord);
        } catch (Exception e) {
            logger.error("获取提示词失败", e);
            return AjaxResult.error("获取提示词失败：" + e.getMessage());
        }
    }

    /**
     * 获取评分拼接提示词
     *
     * @return 提示词内容
     */
    @GetMapping("/getScoreWord")
    public AjaxResult getScoreWord() {
        try {
            PromptTemplate promptTemplate = promptTemplateMapper.getScoreWord();
            if(promptTemplate == null){
                return AjaxResult.success("获取成功", "初稿：\n");
            }
            return AjaxResult.success("获取成功", promptTemplate.getPrompt());
        } catch (Exception e) {
            logger.error("获取提示词失败", e);
            return AjaxResult.error("获取提示词失败：" + e.getMessage());
        }
    }

    /**
     * 获取指定媒体平台的提示词列表
     *
     * @return 提示词内容
     */
    @GetMapping("/getCallWordList")
    public TableDataInfo getCallWordList(CallWordQuery callWordQuery)
    {
        startPage();
        List<CallWord> list = callWordService.getCallWordList(callWordQuery);
        return getDataTable(list);
    }

    /**
     * 更新指定媒体平台的提示词
     *
     * @param callWord 平台提示词
     * @return 操作结果
     */
    @PutMapping("/updateCallWord")
    public AjaxResult updateCallWord(@RequestBody CallWord callWord) {
        try {
            AjaxResult result = callWordService.saveOrUpdateCallWord(callWord);
            return result;
        } catch (Exception e) {
            logger.error("更新提示词失败", e);
            return AjaxResult.error("更新提示词失败：" + e.getMessage());
        }
    }

    /**
     * 删除指定媒体平台的提示词
     *
     * @param platformIds 平台标识集合
     * @return 提示词内容
     */
    @DeleteMapping("/deleteCallWord")
    public AjaxResult deleteCallWord(@RequestBody String[] platformIds) {
        try {
            AjaxResult result = callWordService.deleteCallWord(platformIds);
            return result;
        } catch (Exception e) {
            logger.error("删除提示词失败", e);
            return AjaxResult.error("删除提示词失败：" + e.getMessage());
        }
    }

} 