package com.wx.fbsir.business.fbs.mapper;

import com.wx.fbsir.business.fbs.domain.entity.FbsApiKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * API Key Mapper接口
 *
 * @author wxfbsir
 * @date 2026-04-11
 */
@Mapper
public interface FbsApiKeyMapper {

    /**
     * 根据API Key字符串查询（认证用，只查启用状态的Key）
     *
     * @param apiKey API Key字符串
     * @return API Key记录（NULL=不存在或已禁用）
     */
    FbsApiKey selectActiveByKey(@Param("apiKey") String apiKey);

    /**
     * 根据API Key字符串查询（不限状态，管理用）
     *
     * @param apiKey API Key字符串
     * @return API Key记录
     */
    FbsApiKey selectByApiKey(@Param("apiKey") String apiKey);

    /**
     * 根据ID查询
     *
     * @param id 主键
     * @return API Key记录
     */
    FbsApiKey selectById(@Param("id") Long id);

    /**
     * 列表查询（支持按status筛选）
     *
     * @param filter 过滤条件
     * @return API Key列表
     */
    List<FbsApiKey> selectApiKeyList(@Param("filter") FbsApiKey filter);

    /**
     * 新增API Key
     *
     * @param fbsApiKey API Key记录
     * @return 影响行数
     */
    int insertApiKey(FbsApiKey fbsApiKey);

    /**
     * 更新API Key
     *
     * @param fbsApiKey API Key记录
     * @return 影响行数
     */
    int updateApiKey(FbsApiKey fbsApiKey);

    /**
     * 删除API Key
     *
     * @param id 主键
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}
