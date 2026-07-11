package com.wx.fbsir.business.knowledgebase.enums;

/**
 * 上传类型枚举
 * 
 * @author FBSir
 * @date 2025-12-20
 */
public enum UploadType
{
    /** 上传到智能体元器 */
    YUANQI(1, "智能体元器"),
    
    /** 上传到企业微信机器人 */
    WECHAT_BOT(2, "企业微信机器人"),
    
    /** 上传到两者 */
    BOTH(3, "两者");

    private final Integer code;
    private final String desc;

    UploadType(Integer code, String desc)
    {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode()
    {
        return code;
    }

    public String getDesc()
    {
        return desc;
    }

    /**
     * 根据code获取枚举
     */
    public static UploadType getByCode(Integer code)
    {
        if (code == null)
        {
            return null;
        }
        for (UploadType type : values())
        {
            if (type.getCode().equals(code))
            {
                return type;
            }
        }
        return null;
    }
}
