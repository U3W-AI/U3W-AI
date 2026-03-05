package com.wx.fbsir.business.systemprompt.service.impl;

import com.wx.fbsir.business.systemprompt.domain.SystemPrompt;
import com.wx.fbsir.business.systemprompt.mapper.SystemPromptMapper;
import com.wx.fbsir.business.systemprompt.service.SystemPromptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class SystemPromptServiceImpl implements SystemPromptService {

    @Autowired
    private SystemPromptMapper messageMapper;

    /**
     * 获取系统提示词
     *
     * @param id 系统提示词 ID
     * @return 系统提示词
     */
    @Override
    public SystemPrompt selectSystemPromptById(Long id) {
        return messageMapper.selectSystemPromptById(id);
    }

    /**
     * 更新系统提示词
     *
     * @param systemPrompt 系统提示词 ID
     * @return 更新结果
     */
    @Override
    public boolean updateSystemPrompt(SystemPrompt systemPrompt) {
        return messageMapper.updateSystemPrompt(systemPrompt);
    }

    /**
     * 插入系统提示词
     *
     * @param systemPrompt 系统提示词
     * @return 插入结果
     */
    @Override
    public boolean insertSystemPrompt(SystemPrompt systemPrompt) {
        return messageMapper.insertSystemPrompt(systemPrompt);
    }

    /**
     * 删除系统提示词
     *
     * @param id 系统提示词 ID
     * @return 删除结果
     */
    @Override
    public boolean deleteSystemPrompt(Long id) {
        return messageMapper.deleteSystemPrompt(id);
    }



}
