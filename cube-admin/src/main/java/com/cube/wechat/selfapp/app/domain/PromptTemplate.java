package com.cube.wechat.selfapp.app.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * ClassName: PromptTemplate
 * Package: com.cube.wechat.selfapp.app.domain
 * Description:
 *
 * @Author pupil
 * @Create 2025/9/20 13:08
 * @Version 1.0
 */
public class PromptTemplate {
    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String prompt;

    private Long type;

    private Long userId;

    private Long isdel;

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }
    public void setName(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }
    public void setPrompt(String prompt)
    {
        this.prompt = prompt;
    }

    public String getPrompt()
    {
        return prompt;
    }
    public void setType(Long type)
    {
        this.type = type;
    }

    public Long getType()
    {
        return type;
    }
    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getUserId()
    {
        return userId;
    }
    public void setIsdel(Long isdel)
    {
        this.isdel = isdel;
    }

    public Long getIsdel()
    {
        return isdel;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("id", getId())
                .append("name", getName())
                .append("prompt", getPrompt())
                .append("type", getType())
                .append("userId", getUserId())
                .append("isdel", getIsdel())
                .toString();
    }
}
