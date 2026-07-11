package com.wx.fbsir.business.dailyassistant.domain;

import com.wx.fbsir.common.annotation.Excel;
import com.wx.fbsir.common.core.domain.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 日更助手文章对象 daily_article
 *
 * @author FBSir
 * @date 2025-12-05
 */
public class DailyArticle extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 文章ID */
    private Long id;

    /** 用户ID */
    @Excel(name = "用户ID")
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 原始文章标题 */
    @Excel(name = "原始文章标题")
    @NotBlank(message = "文章标题不能为空")
    @Size(min = 0, max = 500, message = "文章标题长度不能超过500个字符")
    private String articleTitle;

    /** 已选择的模型，多个用逗号分隔，如：1,2,3 */
    @Excel(name = "已选模型")
    @Size(min = 0, max = 100, message = "已选模型长度不能超过100个字符")
    private String selectedModels;

    /** 投递次数 */
    @Excel(name = "投递次数")
    private Integer publishCount;

    /** 优化后的文章内容（来自腾讯元器智能体） */
    @Excel(name = "优化后的文章内容")
    private String optimizedContent;

    /** 大模型1未优化的文章内容 */
    @Excel(name = "大模型1内容")
    private String model1Content;

    /** 大模型2未优化的文章内容 */
    @Excel(name = "大模型2内容")
    private String model2Content;

    /** 大模型3未优化的文章内容 */
    @Excel(name = "大模型3内容")
    private String model3Content;

    /** 大模型1名称 */
    @Excel(name = "大模型1名称")
    @Size(min = 0, max = 100, message = "大模型1名称长度不能超过100个字符")
    private String model1Name;

    /** 大模型2名称 */
    @Excel(name = "大模型2名称")
    @Size(min = 0, max = 100, message = "大模型2名称长度不能超过100个字符")
    private String model2Name;

    /** 大模型3名称 */
    @Excel(name = "大模型3名称")
    @Size(min = 0, max = 100, message = "大模型3名称长度不能超过100个字符")
    private String model3Name;

    /** 腾讯元器智能体任务ID */
    @Excel(name = "智能体任务ID")
    @Size(min = 0, max = 200, message = "智能体任务ID长度不能超过200个字符")
    private String agentTaskId;

    /** 处理状态：0-处理中，1-已完成，2-失败 */
    @Excel(name = "处理状态", readConverterExp = "0=处理中,1=已完成,2=失败")
    private Integer processStatus;

    /** 错误信息 */
    @Excel(name = "错误信息")
    @Size(min = 0, max = 1000, message = "错误信息长度不能超过1000个字符")
    private String errorMessage;


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

    public void setArticleTitle(String articleTitle)
    {
        this.articleTitle = articleTitle;
    }

    public String getArticleTitle()
    {
        return articleTitle;
    }

    public void setSelectedModels(String selectedModels)
    {
        this.selectedModels = selectedModels;
    }

    public String getSelectedModels()
    {
        return selectedModels;
    }

    public void setPublishCount(Integer publishCount)
    {
        this.publishCount = publishCount;
    }

    public Integer getPublishCount()
    {
        return publishCount;
    }

    public void setOptimizedContent(String optimizedContent)
    {
        this.optimizedContent = optimizedContent;
    }

    public String getOptimizedContent()
    {
        return optimizedContent;
    }

    public void setModel1Content(String model1Content)
    {
        this.model1Content = model1Content;
    }

    public String getModel1Content()
    {
        return model1Content;
    }

    public void setModel2Content(String model2Content)
    {
        this.model2Content = model2Content;
    }

    public String getModel2Content()
    {
        return model2Content;
    }

    public void setModel3Content(String model3Content)
    {
        this.model3Content = model3Content;
    }

    public String getModel3Content()
    {
        return model3Content;
    }

    public void setModel1Name(String model1Name)
    {
        this.model1Name = model1Name;
    }

    public String getModel1Name()
    {
        return model1Name;
    }

    public void setModel2Name(String model2Name)
    {
        this.model2Name = model2Name;
    }

    public String getModel2Name()
    {
        return model2Name;
    }

    public void setModel3Name(String model3Name)
    {
        this.model3Name = model3Name;
    }

    public String getModel3Name()
    {
        return model3Name;
    }

    public void setAgentTaskId(String agentTaskId)
    {
        this.agentTaskId = agentTaskId;
    }

    public String getAgentTaskId()
    {
        return agentTaskId;
    }

    public void setProcessStatus(Integer processStatus)
    {
        this.processStatus = processStatus;
    }

    public Integer getProcessStatus()
    {
        return processStatus;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }


    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("userId", getUserId())
            .append("articleTitle", getArticleTitle())
            .append("selectedModels", getSelectedModels())
            .append("publishCount", getPublishCount())
            .append("optimizedContent", getOptimizedContent())
            .append("model1Content", getModel1Content())
            .append("model2Content", getModel2Content())
            .append("model3Content", getModel3Content())
            .append("model1Name", getModel1Name())
            .append("model2Name", getModel2Name())
            .append("model3Name", getModel3Name())
            .append("agentTaskId", getAgentTaskId())
            .append("processStatus", getProcessStatus())
            .append("errorMessage", getErrorMessage())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
