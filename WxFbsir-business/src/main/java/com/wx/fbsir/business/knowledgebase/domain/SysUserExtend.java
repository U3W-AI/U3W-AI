package com.wx.fbsir.business.knowledgebase.domain;

import com.wx.fbsir.common.core.domain.entity.SysUser;

/**
 * 用户扩展类（继承SysUser，添加知识库相关字段）
 * 
 * @author wxfbsir
 * @date 2025-12-20
 */
public class SysUserExtend extends SysUser
{
    private static final long serialVersionUID = 1L;



    /** 知识库空间额度（按MB计算，1024为默认额度） */
    private Long kbSpaceQuota;

    /** 空间内包含的知识库ID，多个用逗号分隔（如1,2,3） */
    private String kbSpaceIncludeKbIds;

    /** 拥有的知识库ID（用户自己创建的知识库），多个用逗号分隔（如1,2,3） */
    private String hasKnowledgeBase;

    /** 收藏的知识库ID，多个用逗号分隔（如1,2,3） */
    private String kbLikesIds;

    /** 是否为超级账户：0-普通账户，1-超级账户 */
    private Integer isSuper;

    /** 账户权限管理权限是否开放：0-关闭，1-开放 */
    private Integer isOpenAccountPerm;

    /** 模块功能操作权限是否开放：0-关闭，1-开放 */
    private Integer isOpenModulePerm;

    public SysUserExtend()
    {
    }



    public Long getKbSpaceQuota()
    {
        return kbSpaceQuota;
    }

    public void setKbSpaceQuota(Long kbSpaceQuota)
    {
        this.kbSpaceQuota = kbSpaceQuota;
    }

    public String getKbSpaceIncludeKbIds()
    {
        return kbSpaceIncludeKbIds;
    }

    public void setKbSpaceIncludeKbIds(String kbSpaceIncludeKbIds)
    {
        this.kbSpaceIncludeKbIds = kbSpaceIncludeKbIds;
    }

    public Integer getIsSuper()
    {
        return isSuper;
    }

    public void setIsSuper(Integer isSuper)
    {
        this.isSuper = isSuper;
    }

    public Integer getIsOpenAccountPerm()
    {
        return isOpenAccountPerm;
    }

    public void setIsOpenAccountPerm(Integer isOpenAccountPerm)
    {
        this.isOpenAccountPerm = isOpenAccountPerm;
    }

    public Integer getIsOpenModulePerm()
    {
        return isOpenModulePerm;
    }

    public void setIsOpenModulePerm(Integer isOpenModulePerm)
    {
        this.isOpenModulePerm = isOpenModulePerm;
    }

    public String getHasKnowledgeBase()
    {
        return hasKnowledgeBase;
    }

    public void setHasKnowledgeBase(String hasKnowledgeBase)
    {
        this.hasKnowledgeBase = hasKnowledgeBase;
    }

    public String getKbLikesIds()
    {
        return kbLikesIds;
    }

    public void setKbLikesIds(String kbLikesIds)
    {
        this.kbLikesIds = kbLikesIds;
    }
}
