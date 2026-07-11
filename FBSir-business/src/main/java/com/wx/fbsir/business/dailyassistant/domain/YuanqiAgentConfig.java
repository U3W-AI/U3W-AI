package com.wx.fbsir.business.dailyassistant.domain;

import com.wx.fbsir.common.annotation.Excel;
import com.wx.fbsir.common.core.domain.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 腾讯元器智能体配置对象 yuanqi_agent_config
 *
 * @author FBSir
 * @date 2025-12-05
 */
public class YuanqiAgentConfig extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 配置ID */
    private Long id;

    /** 用户ID (由后端自动设置) */
    @Excel(name = "用户ID")
    private Long userId;

    /** 业务类型：daily_assistant-日更助手, document_parse-文档解析 */
    @Excel(name = "业务类型")
    @NotBlank(message = "业务类型不能为空")
    @Size(min = 0, max = 50, message = "业务类型长度不能超过50个字符")
    private String businessType;

    /** 腾讯元器智能体ID（appid，从智能体配置-应用发布-体验链接中获取） */
    @Excel(name = "智能体ID")
    @NotBlank(message = "智能体ID不能为空")
    @Size(min = 0, max = 200, message = "智能体ID长度不能超过200个字符")
    private String agentId;

    /** 智能体名称 */
    @Excel(name = "智能体名称")
    @Size(min = 0, max = 100, message = "智能体名称长度不能超过100个字符")
    private String agentName;

    /** API密钥（appkey，从智能体调试页面-应用发布-API管理中获取） */
    @Excel(name = "API密钥")
    @Size(min = 0, max = 500, message = "API密钥长度不能超过500个字符")
    private String apiKey;

    /** API端点URL（固定为：https://yuanqi.tencent.com/openapi/v1/agent/chat/completions） */
    @Excel(name = "API端点URL")
    @Size(min = 0, max = 500, message = "API端点URL长度不能超过500个字符")
    private String apiEndpoint;

    /** 是否启用：0-禁用，1-启用 */
    @Excel(name = "是否启用", readConverterExp = "0=禁用,1=启用")
    private Integer isActive;

    /** 其他配置（JSON格式） */
    private String configJson;

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setBusinessType(String businessType)
    {
        this.businessType = businessType;
    }

    public String getBusinessType()
    {
        return businessType;
    }

    public void setAgentId(String agentId)
    {
        this.agentId = agentId;
    }

    public String getAgentId()
    {
        return agentId;
    }

    public void setAgentName(String agentName)
    {
        this.agentName = agentName;
    }

    public String getAgentName()
    {
        return agentName;
    }

    public void setApiKey(String apiKey)
    {
        this.apiKey = apiKey;
    }

    public String getApiKey()
    {
        return apiKey;
    }

    public void setApiEndpoint(String apiEndpoint)
    {
        this.apiEndpoint = apiEndpoint;
    }

    public String getApiEndpoint()
    {
        return apiEndpoint;
    }

    public void setIsActive(Integer isActive)
    {
        this.isActive = isActive;
    }

    public Integer getIsActive()
    {
        return isActive;
    }

    public void setConfigJson(String configJson)
    {
        this.configJson = configJson;
    }

    public String getConfigJson()
    {
        return configJson;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("userId", getUserId())
            .append("businessType", getBusinessType())
            .append("agentId", getAgentId())
            .append("agentName", getAgentName())
            .append("apiKey", getApiKey())
            .append("apiEndpoint", getApiEndpoint())
            .append("isActive", getIsActive())
            .append("configJson", getConfigJson())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
