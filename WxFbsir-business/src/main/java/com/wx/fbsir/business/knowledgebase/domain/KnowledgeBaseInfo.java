package com.wx.fbsir.business.knowledgebase.domain;

import com.wx.fbsir.common.annotation.Excel;
import java.util.Date;

/**
 * 知识库对象 kb_base
 * 
 * @author wxfbsir
 * @date 2025-12-20
 */
public class KnowledgeBaseInfo
{
    /** 知识库ID（自增且唯一） */
    private Long kbId;

    /** 知识库名字 */
    @Excel(name = "知识库名字")
    private String kbName;

    /** 知识库内容（JSON格式数据） */
    @Excel(name = "知识库内容")
    private String kbContent;

    /** 是否为公共模板：0-私有，1-公共 */
    @Excel(name = "是否为公共模板", readConverterExp = "0=私有,1=公共")
    private Integer isPublicTemplate;

    /** 创建者用户ID */
    @Excel(name = "创建者ID")
    private Long creatorId;

    /** 知识库大小（MB） */
    @Excel(name = "知识库大小(MB)")
    private Long kbSize;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

    public KnowledgeBaseInfo()
    {
    }

    public KnowledgeBaseInfo(Long kbId)
    {
        this.kbId = kbId;
    }

    public Long getKbId()
    {
        return kbId;
    }

    public void setKbId(Long kbId)
    {
        this.kbId = kbId;
    }

    public String getKbName()
    {
        return kbName;
    }

    public void setKbName(String kbName)
    {
        this.kbName = kbName;
    }

    public String getKbContent()
    {
        return kbContent;
    }

    public void setKbContent(String kbContent)
    {
        this.kbContent = kbContent;
    }

    public Integer getIsPublicTemplate()
    {
        return isPublicTemplate;
    }

    public void setIsPublicTemplate(Integer isPublicTemplate)
    {
        this.isPublicTemplate = isPublicTemplate;
    }

    public Long getCreatorId()
    {
        return creatorId;
    }

    public void setCreatorId(Long creatorId)
    {
        this.creatorId = creatorId;
    }

    public Long getKbSize()
    {
        return kbSize;
    }

    public void setKbSize(Long kbSize)
    {
        this.kbSize = kbSize;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }

    @Override
    public String toString()
    {
        return "KnowledgeBaseInfo{" +
                "kbId=" + kbId +
                ", kbName='" + kbName + '\'' +
                ", kbContent='" + kbContent + '\'' +
                ", isPublicTemplate=" + isPublicTemplate +
                ", creatorId=" + creatorId +
                ", kbSize=" + kbSize +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
