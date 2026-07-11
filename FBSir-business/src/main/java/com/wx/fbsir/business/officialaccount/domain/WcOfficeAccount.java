package com.wx.fbsir.business.officialaccount.domain;

import com.wx.fbsir.common.annotation.Excel;
import com.wx.fbsir.common.core.domain.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 微信公众号配置对象 wc_office_account
 *
 * @author FBSir
 * @date 2025-12-08
 */
public class WcOfficeAccount extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 配置ID */
    private Long id;

    /** 用户ID */
    @Excel(name = "用户ID")
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 开发者ID（加密存储） */
    @Excel(name = "开发者ID")
    @NotBlank(message = "开发者ID不能为空")
    @Size(min = 0, max = 500, message = "开发者ID长度不能超过500个字符")
    private String appId;

    /** 开发者密钥（加密存储） */
    @Excel(name = "开发者密钥")
    @NotBlank(message = "开发者密钥不能为空")
    @Size(min = 0, max = 500, message = "开发者密钥长度不能超过500个字符")
    private String appSecret;

    /** 作者名称（发布文章时显示的作者） */
    @Excel(name = "作者名称")
    @Size(min = 0, max = 100, message = "作者名称长度不能超过100个字符")
    private String authorName;

    /** 素材封面图URL */
    @Excel(name = "素材封面图URL")
    @Size(min = 0, max = 500, message = "素材封面图URL长度不能超过500个字符")
    private String picUrl;

    /** 素材ID（验证时上传封面图获取） */
    @Excel(name = "素材ID")
    @Size(min = 0, max = 100, message = "素材ID长度不能超过100个字符")
    private String mediaId;

    /** 是否启用：0-禁用，1-启用 */
    @Excel(name = "是否启用", readConverterExp = "0=禁用,1=启用")
    private Integer isActive;


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

    public void setAppId(String appId)
    {
        this.appId = appId;
    }

    public String getAppId()
    {
        return appId;
    }

    public void setAppSecret(String appSecret)
    {
        this.appSecret = appSecret;
    }

    public String getAppSecret()
    {
        return appSecret;
    }

    public void setAuthorName(String authorName)
    {
        this.authorName = authorName;
    }

    public String getAuthorName()
    {
        return authorName;
    }

    public void setPicUrl(String picUrl)
    {
        this.picUrl = picUrl;
    }

    public String getPicUrl()
    {
        return picUrl;
    }

    public void setMediaId(String mediaId)
    {
        this.mediaId = mediaId;
    }

    public String getMediaId()
    {
        return mediaId;
    }

    public void setIsActive(Integer isActive)
    {
        this.isActive = isActive;
    }

    public Integer getIsActive()
    {
        return isActive;
    }


    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("userId", getUserId())
            .append("appId", "******") // ID不输出
            .append("appSecret", "******") // 密钥不输出
            .append("authorName", getAuthorName())
            .append("picUrl", getPicUrl())
            .append("mediaId", getMediaId())
            .append("isActive", getIsActive())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
