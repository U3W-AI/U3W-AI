package com.wx.fbsir.business.systemprompt.service.impl;

import com.wx.fbsir.business.systemprompt.domain.SystemPrompt;
import com.wx.fbsir.business.systemprompt.mapper.SystemPromptMapper;
import com.wx.fbsir.business.systemprompt.service.SystemPromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SystemPromptServiceImpl implements SystemPromptService {
    private static final Logger logger = LoggerFactory.getLogger(SystemPromptServiceImpl.class);

    @Autowired
    private SystemPromptMapper systemPromptMapper;

    /**
     * 查询系统提示词列表
     */
    @Override
    public List<SystemPrompt> selectSystemPromptList() {
        logger.debug("开始查询系统提示词列表");
        List<SystemPrompt> result = systemPromptMapper.selectSystemPromptList();
        logger.info("查询系统提示词列表，共{}条", result.size());
        return result;
    }

    /**
     * 获取系统提示词
     *
     * @param id 系统提示词 ID
     * @return 系统提示词
     */
    @Override
    public SystemPrompt selectSystemPromptById(Long id) {
        logger.debug("开始查询系统提示词，ID：{}", id);
        SystemPrompt result = systemPromptMapper.selectSystemPromptById(id);
        if (result == null) {
            logger.warn("系统提示词不存在，ID：{}", id);
        }
        return result;
    }

    /**
     * 更新系统提示词
     *
     * @param systemPrompt 系统提示词
     * @return 更新结果
     */
    @Override
    public boolean updateSystemPrompt(SystemPrompt systemPrompt) {
        logger.debug("开始更新系统提示词，ID：{}，名称：{}", systemPrompt.getId(), systemPrompt.getName());
        boolean result = systemPromptMapper.updateSystemPrompt(systemPrompt);
        if (!result) {
            logger.warn("更新系统提示词失败，ID：{}，名称：{}", systemPrompt.getId(), systemPrompt.getName());
        }
        return result;
    }

    /**
     * 插入系统提示词
     *
     * @param systemPrompt 系统提示词
     * @return 插入结果
     */
    @Override
    public boolean insertSystemPrompt(SystemPrompt systemPrompt) {
        logger.debug("开始插入系统提示词，名称：{}", systemPrompt.getName());
        boolean result = systemPromptMapper.insertSystemPrompt(systemPrompt);
        if (!result) {
            logger.warn("插入系统提示词失败，名称：{}", systemPrompt.getName());
        }
        return result;
    }

    /**
     * 删除系统提示词
     *
     * @param id 系统提示词 ID
     * @return 删除结果
     */
    @Override
    public boolean deleteSystemPrompt(Long id) {
        logger.debug("开始删除系统提示词，ID：{}", id);
        boolean result = systemPromptMapper.deleteSystemPrompt(id);
        if (!result) {
            logger.warn("删除系统提示词失败，ID：{}", id);
        }
        return result;
    }



}
