package com.wx.fbsir.business.knowledgebase.enums;

/**
 * 权限类型枚举
 * 
 * @author FBSir
 * @date 2025-12-20
 */
public enum PermissionType
{
    /** 账户权限管理权限 */
    ACCOUNT_PERM(1, "账户权限管理权限"),
    
    /** 模块功能操作权限 */
    MODULE_PERM(2, "模块功能操作权限");

    private final Integer code;
    private final String desc;

    PermissionType(Integer code, String desc)
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
    public static PermissionType getByCode(Integer code)
    {
        if (code == null)
        {
            return null;
        }
        for (PermissionType type : values())
        {
            if (type.getCode().equals(code))
            {
                return type;
            }
        }
        return null;
    }
}
