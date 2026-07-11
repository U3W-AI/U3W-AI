package com.wx.fbsir.business.fbs.service.business;

import com.wx.fbsir.business.fbs.domain.entity.FbsApiKey;

import java.util.List;

/**
 * API Key 运营管理 BusinessService 接口
 *
 * @author FBSir
 * @date 2026-04-11
 */
public interface IFbsApiKeyBusinessService {

    /**
     * 生成 API Key
     * 前缀 fbs_ + 32位随机串，创建时返回完整 Key（仅此一次）
     *
     * @param name     Key 名称
     * @param packCode 关联场景包编码（NULL=全局Key）
     * @param rateLimitPerMin 速率限制
     * @param remark   备注
     * @return 新建的 API Key 实体（含完整明文 Key，仅此一次返回）
     */
    FbsApiKey generateApiKey(String name, String packCode, Integer rateLimitPerMin, String remark);

    /**
     * 禁用 API Key
     *
     * @param id Key ID
     */
    void disableApiKey(Long id);

    /**
     * 启用 API Key
     *
     * @param id Key ID
     */
    void enableApiKey(Long id);

    /**
     * 删除 API Key
     *
     * @param id Key ID
     */
    void deleteApiKey(Long id);

    /**
     * 查询 API Key 列表（脱敏）
     *
     * @param filter 过滤条件
     * @return 脱敏后的 API Key 列表
     */
    List<FbsApiKey> listApiKeys(FbsApiKey filter);

    /**
     * 根据 ID 查询 API Key（脱敏）
     *
     * @param id Key ID
     * @return 脱敏后的 API Key
     */
    FbsApiKey getApiKeyById(Long id);

    // ========== 用户侧接口 ==========

    /**
     * 用户创建 API Key
     * 自动绑定当前用户
     *
     * @param name Key 名称
     * @return 新建的 API Key 实体（含完整明文 Key，仅此一次返回）
     */
    FbsApiKey createByUser(String name);

    /**
     * 查询当前用户的 API Key 列表
     * 脱敏显示密钥
     *
     * @return 脱敏后的 API Key 列表
     */
    List<FbsApiKey> listMyKeys();

    /**
     * 禁用/启用 API Key
     * 只能操作自己的 Key
     *
     * @param id     Key ID
     * @param status 目标状态（1=启用, 0=禁用）
     */
    void toggleStatus(Long id, Integer status);

    /**
     * 删除 API Key（校验归属）
     * 只能删除自己的 Key
     *
     * @param id Key ID
     */
    void deleteById(Long id);
}
