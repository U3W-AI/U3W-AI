package com.cube.wechat.selfapp.app.service;

import com.cube.common.core.domain.AjaxResult;

/**
 * 提示词服务接口
 * 
 * @author fuchen
 * @version 1.0
 * @date 2025-01-14
 */
public interface CallWordService {

    /**
     * 获取指定平台的提示词
     * 
     * @param platformId 平台标识
     * @return 提示词内容
     */
    String getCallWord(String platformId);

    /**
     * 保存或更新提示词
     * 
     * @param platformId 平台标识
     * @param wordContent 提示词内容
     * @return 操作结果
     */
    AjaxResult saveOrUpdateCallWord(String platformId, String wordContent);

    /**
     * 更新草稿的知乎投递状态
     * 
     * @param draftId 草稿ID
     * @param isPushed 是否已投递
     * @return 操作结果
     */
    AjaxResult updateDraftZhihuStatus(Long draftId, boolean isPushed);

    /**
     * 生成知乎标题
     * 
     * @param aiName AI名称
     * @param userId 用户ID
     * @param num 序号
     * @return 格式化的标题
     */
    String generateZhihuTitle(String aiName, String userId, int num);
} 