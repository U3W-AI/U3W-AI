package com.wx.fbsir.business.documentparse.domain;

import com.wx.fbsir.common.annotation.Excel;
import com.wx.fbsir.common.core.domain.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 文档解析对象 document_parse
 *
 * @author FBSir
 * @date 2025-12-26
 */
public class DocumentParse extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 文档解析ID */
    private Long id;

    /** 用户ID */
    @Excel(name = "用户ID")
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 文档ID（自动生成） */
    @Excel(name = "文档ID")
    @NotBlank(message = "文档ID不能为空")
    @Size(min = 0, max = 200, message = "文档ID长度不能超过200个字符")
    private String documentId;

    /** 文档名称 */
    @Excel(name = "文档名称")
    @Size(min = 0, max = 500, message = "文档名称长度不能超过500个字符")
    private String documentName;

    /** 提示词 */
    @Excel(name = "提示词")
    private String prompt;

    /** 解析后的内容（来自腾讯元器智能体） */
    @Excel(name = "解析后的内容")
    private String parsedContent;

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

    public void setDocumentId(String documentId)
    {
        this.documentId = documentId;
    }

    public String getDocumentId()
    {
        return documentId;
    }

    public void setDocumentName(String documentName)
    {
        this.documentName = documentName;
    }

    public String getDocumentName()
    {
        return documentName;
    }

    public void setPrompt(String prompt)
    {
        this.prompt = prompt;
    }

    public String getPrompt()
    {
        return prompt;
    }

    public void setParsedContent(String parsedContent)
    {
        this.parsedContent = parsedContent;
    }

    public String getParsedContent()
    {
        return parsedContent;
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
            .append("documentId", getDocumentId())
            .append("documentName", getDocumentName())
            .append("prompt", getPrompt())
            .append("parsedContent", getParsedContent())
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

