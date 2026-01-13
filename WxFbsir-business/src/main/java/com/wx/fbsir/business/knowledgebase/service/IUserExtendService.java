package com.wx.fbsir.business.knowledgebase.service;

import java.util.List;
import com.wx.fbsir.business.knowledgebase.domain.SysUserExtend;

/**
 * 用户扩展Service接口
 * 
 * @author wxfbsir
 * @date 2025-12-20
 */
public interface IUserExtendService
{
    /**
     * 查询用户扩展信息
     * 
     * @param userId 用户ID
     * @return 用户扩展信息
     */
    public SysUserExtend selectUserExtendByUserId(Long userId);

    /**
     * 修改空间限额
     * 
     * @param userId 被操作用户ID
     * @param quota 修改后的额度
     * @return 结果
     */
    public boolean updateSpaceQuota(Long userId, Long quota);

    /**
     * 修改账户权限
     * 
     * @param userId 账户ID
     * @param permissionType 权限类型（1-账户权限管理权限，2-模块功能操作权限）
     * @param isOpen 是否开放（0-关闭，1-开放）
     * @return 结果
     */
    public boolean updateAccountPermission(Long userId, Integer permissionType, Integer isOpen);

    /**
     * 查询所有用户的权限信息列表
     * 
     * @return 用户扩展信息列表（包含权限信息）
     */
    public List<SysUserExtend> selectAllUserPermissions();
}
