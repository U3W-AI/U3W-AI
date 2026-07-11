package com.wx.fbsir.business.officialaccount.domain;

import com.wx.fbsir.common.annotation.Excel;
import com.wx.fbsir.common.core.domain.BaseEntity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 公众号文章发布记录对象 wc_office_publish_record
 *
 * @author FBSir
 * @date 2025-12-08
 */
public class WcOfficePublishRecord extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    private Long id;

    /** 用户ID */
    @Excel(name = "用户ID")
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 关联的日更助手文章ID */
    @Excel(name = "文章ID")
    @NotNull(message = "文章ID不能为空")
    private Long articleId;

    /** 公众号配置ID */
    @Excel(name = "公众号配置ID")
    @NotNull(message = "公众号配置ID不能为空")
    private Long officeAccountId;

    /** 发布的内容类型：optimized/model1/model2/model3/layout */
    @Excel(name = "内容类型")
    @Size(min = 0, max = 50, message = "内容类型长度不能超过50个字符")
    private String contentType;

    /** 发布状态：0-发布中，1-成功，2-失败 */
    @Excel(name = "发布状态", readConverterExp = "0=发布中,1=成功,2=失败")
    private Integer publishStatus;

    /** 微信素材ID */
    @Excel(name = "微信素材ID")
    @Size(min = 0, max = 200, message = "微信素材ID长度不能超过200个字符")
    private String mediaId;

    /** 失败原因 */
    @Excel(name = "失败原因")
    @Size(min = 0, max = 1000, message = "失败原因长度不能超过1000个字符")
    private String errorMessage;

    /** 删除标志（0代表存在 1代表删除） */
    private String delFlag;


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

    public void setArticleId(Long articleId)
    {
        this.articleId = articleId;
    }

    public Long getArticleId()
    {
        return articleId;
    }

    public void setOfficeAccountId(Long officeAccountId)
    {
        this.officeAccountId = officeAccountId;
    }

    public Long getOfficeAccountId()
    {
        return officeAccountId;
    }

    public void setContentType(String contentType)
    {
        this.contentType = contentType;
    }

    public String getContentType()
    {
        return contentType;
    }

    public void setPublishStatus(Integer publishStatus)
    {
        this.publishStatus = publishStatus;
    }

    public Integer getPublishStatus()
    {
        return publishStatus;
    }

    public void setMediaId(String mediaId)
    {
        this.mediaId = mediaId;
    }

    public String getMediaId()
    {
        return mediaId;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public String getDelFlag()
    {
        return delFlag;
    }


    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("userId", getUserId())
            .append("articleId", getArticleId())
            .append("officeAccountId", getOfficeAccountId())
            .append("contentType", getContentType())
            .append("publishStatus", getPublishStatus())
            .append("mediaId", getMediaId())
            .append("errorMessage", getErrorMessage())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .append("delFlag", getDelFlag())
            .toString();
    }
}
