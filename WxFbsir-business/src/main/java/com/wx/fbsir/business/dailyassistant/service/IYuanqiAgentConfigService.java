package com.wx.fbsir.business.dailyassistant.service;

import java.util.List;
import com.wx.fbsir.business.dailyassistant.domain.YuanqiAgentConfig;

/**
 * 腾讯元器智能体配置Service接口
 *
 * @author wxfbsir
 * @date 2025-12-05
 */
public interface IYuanqiAgentConfigService
{
    /**
     * 查询腾讯元器智能体配置
     *
     * @param id 腾讯元器智能体配置ID
     * @return 腾讯元器智能体配置
     */
    public YuanqiAgentConfig selectYuanqiAgentConfigById(Long id);

    /**
     * 查询腾讯元器智能体配置列表
     *
     * @param yuanqiAgentConfig 腾讯元器智能体配置
     * @return 腾讯元器智能体配置集合
     */
    public List<YuanqiAgentConfig> selectYuanqiAgentConfigList(YuanqiAgentConfig yuanqiAgentConfig);

    /**
     * 根据用户ID和业务类型查询启用的配置（返回脱敏数据，供前端使用）
     *
     * @param userId 用户ID
     * @param businessType 业务类型
     * @return 腾讯元器智能体配置（敏感字段已脱敏）
     */
    public YuanqiAgentConfig selectActiveConfigByUserId(Long userId, String businessType);

    /**
     * 根据用户ID和业务类型查询启用的配置（返回解密数据，仅供内部业务逻辑使用）
     * 注意：此方法返回真实的敏感信息，不应暴露给前端
     *
     * @param userId 用户ID
     * @param businessType 业务类型
     * @return 腾讯元器智能体配置（敏感字段已解密）
     */
    public YuanqiAgentConfig selectActiveConfigByUserIdDecrypted(Long userId, String businessType);

    /**
     * 新增腾讯元器智能体配置
     *
     * @param yuanqiAgentConfig 腾讯元器智能体配置
     * @return 结果
     */
    public int insertYuanqiAgentConfig(YuanqiAgentConfig yuanqiAgentConfig);

    /**
     * 修改腾讯元器智能体配置
     *
     * @param yuanqiAgentConfig 腾讯元器智能体配置
     * @return 结果
     */
    public int updateYuanqiAgentConfig(YuanqiAgentConfig yuanqiAgentConfig);

    /**
     * 批量删除腾讯元器智能体配置
     *
     * @param ids 需要删除的腾讯元器智能体配置ID
     * @return 结果
     */
    public int deleteYuanqiAgentConfigByIds(Long[] ids);

    /**
     * 删除腾讯元器智能体配置信息
     *
     * @param id 腾讯元器智能体配置ID
     * @return 结果
     */
    public int deleteYuanqiAgentConfigById(Long id);

    /**
     * 验证智能体配置是否可用
     * 通过调用腾讯元器API测试连通性
     *
     * @param agentId 智能体ID
     * @param apiKey API密钥
     * @param apiEndpoint API端点
     * @return 验证结果消息
     * @throws RuntimeException 验证失败时抛出异常，包含友好的错误信息
     */
    public String verifyAgentConfig(String agentId, String apiKey, String apiEndpoint);
}
