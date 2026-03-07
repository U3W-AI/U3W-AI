package com.wx.fbsir.business.systemprompt.service;


import com.wx.fbsir.business.systemprompt.domain.SystemPrompt;

import java.util.List;

public interface SystemPromptService {
    /**
     * 查询系统提示词列表
     */
    List<SystemPrompt> selectSystemPromptList();

    /**
     * 获取系统提示词
     *
     * @param id 系统提示词 ID
     * @return 系统提示词
     */
    SystemPrompt selectSystemPromptById(Long id);

    /**
     * 更新系统提示词
     *
     * @param systemPrompt 系统提示词 ID
     * @return 更新结果
     */
    boolean updateSystemPrompt(SystemPrompt systemPrompt);

    /**
     * 插入系统提示词
     *
     * @param systemPrompt 系统提示词
     * @return 插入结果
     */
    boolean insertSystemPrompt(SystemPrompt systemPrompt);

    /**
     * 删除系统提示词
     *
     * @param id 系统提示词 ID
     * @return 删除结果
     */
    boolean deleteSystemPrompt(Long id);



}
