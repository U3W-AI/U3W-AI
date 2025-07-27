package com.cube.wechat.selfapp.app.mapper;

import com.cube.wechat.selfapp.app.domain.CallWord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 提示词配置Mapper接口
 * 
 * @author fuchen
 * @version 1.0
 * @date 2025-01-14
 */
@Mapper
public interface CallWordMapper {

    /**
     * 根据平台ID查询提示词
     * 
     * @param platformId 平台标识
     * @return 提示词配置
     */
    CallWord getCallWordById(@Param("platformId") String platformId);

    /**
     * 插入新的提示词配置
     * 
     * @param callWord 提示词配置
     * @return 影响行数
     */
    int insertCallWord(CallWord callWord);

    /**
     * 更新提示词配置
     * 
     * @param callWord 提示词配置
     * @return 影响行数
     */
    int updateCallWord(CallWord callWord);

    /**
     * 更新草稿的知乎投递状态
     * 
     * @param draftId 草稿ID
     * @param isPushed 是否已投递（0-未投递，1-已投递）
     * @return 影响行数
     */
    int updateDraftZhihuStatus(@Param("draftId") Long draftId, @Param("isPushed") Integer isPushed);
} 