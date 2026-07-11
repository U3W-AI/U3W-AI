package com.wx.fbsir.business.knowledgebase.service.impl;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wx.fbsir.business.knowledgebase.domain.SysUserExtend;
import com.wx.fbsir.business.knowledgebase.enums.PermissionType;
import com.wx.fbsir.business.knowledgebase.mapper.SysUserExtendMapper;
import com.wx.fbsir.business.knowledgebase.service.IUserExtendService;
import com.wx.fbsir.common.utils.SecurityUtils;

/**
 * 用户扩展Service业务层处理
 * 
 * @author FBSir
 * @date 2025-12-20
 */
@Service
public class UserExtendServiceImpl implements IUserExtendService
{
    private static final Logger log = LoggerFactory.getLogger(UserExtendServiceImpl.class);

    @Autowired
    private SysUserExtendMapper sysUserExtendMapper;

    /**
     * 查询用户扩展信息
     * 
     * @param userId 用户ID
     * @return 用户扩展信息
     */
    @Override
    public SysUserExtend selectUserExtendByUserId(Long userId)
    {
        return sysUserExtendMapper.selectUserExtendByUserId(userId);
    }

    /**
     * 修改空间限额
     * 
     * 业务逻辑：
     * 1. 权限检查：只有拥有模块功能操作权限的用户或超级账户才能修改空间限额
     * 2. 参数校验：确保额度值有效（非空且大于等于0）
     * 3. 更新目标用户的空间限额
     * 
     * 权限说明：
     * - 模块功能操作权限（is_open_module_perm = 1）：可以修改任何用户的空间限额
     * - 超级账户（is_super = 1）：拥有所有权限，包括修改空间限额
     * 
     * @param userId 被操作用户ID（要修改空间限额的目标用户）
     * @param quota 修改后的额度（按MB计算，单位：MB）
     * @return true-修改成功，false-修改失败（权限不足或参数无效）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateSpaceQuota(Long userId, Long quota)
    {
        // 1. 权限检查：获取当前登录用户，检查是否有模块功能操作权限或超级账户权限
        Long currentUserId = SecurityUtils.getUserId();
        SysUserExtend currentUser = sysUserExtendMapper.selectUserExtendByUserId(currentUserId);
        
        // 检查模块功能操作权限
        if (currentUser == null || currentUser.getIsOpenModulePerm() == null || currentUser.getIsOpenModulePerm() == 0)
        {
            // 如果不是模块功能操作权限，再检查是否为超级账户
            if (currentUser == null || currentUser.getIsSuper() == null || currentUser.getIsSuper() == 0)
            {
                log.error("用户无权限修改空间限额，userId: {}", currentUserId);
                return false;
            }
        }

        // 2. 参数校验：确保额度值有效（单位：MB）
        if (quota == null || quota < 0)
        {
            log.error("无效的空间限额，quota: {} MB", quota);
            return false;
        }

        // 3. 更新目标用户的空间限额
        int result = sysUserExtendMapper.updateKbSpaceQuota(userId, quota);
        return result > 0;
    }

    /**
     * 修改账户权限
     * 
     * 业务逻辑：
     * 1. 权限检查：只有拥有账户权限管理权限的用户或超级账户才能修改其他用户的权限
     * 2. 参数校验：确保权限类型和开关状态有效
     * 3. 根据权限类型更新对应的权限字段
     * 
     * 权限说明：
     * - 账户权限管理权限（is_open_account_perm = 1）：可以修改其他用户的权限
     * - 超级账户（is_super = 1）：拥有所有权限，包括修改账户权限
     * 
     * 权限类型：
     * - 1: 账户权限管理权限（is_open_account_perm）
     * - 2: 模块功能操作权限（is_open_module_perm）
     * 
     * @param userId 账户ID（要修改权限的目标用户）
     * @param permissionType 权限类型（1-账户权限管理权限，2-模块功能操作权限）
     * @param isOpen 是否开放（0-关闭，1-开放）
     * @return true-修改成功，false-修改失败（权限不足或参数无效）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateAccountPermission(Long userId, Integer permissionType, Integer isOpen)
    {
        // 1. 权限检查：获取当前登录用户，检查是否有账户权限管理权限或超级账户权限
        Long currentUserId = SecurityUtils.getUserId();
        SysUserExtend currentUser = sysUserExtendMapper.selectUserExtendByUserId(currentUserId);
        
        // 检查账户权限管理权限
        if (currentUser == null || currentUser.getIsOpenAccountPerm() == null || currentUser.getIsOpenAccountPerm() == 0)
        {
            // 如果不是账户权限管理权限，再检查是否为超级账户
            if (currentUser == null || currentUser.getIsSuper() == null || currentUser.getIsSuper() == 0)
            {
                log.error("用户无权限修改账户权限，userId: {}", currentUserId);
                return false;
            }
        }

        // 2. 参数校验：确保权限类型和开关状态有效
        if (permissionType == null || isOpen == null)
        {
            log.error("无效的参数，permissionType: {}, isOpen: {}", permissionType, isOpen);
            return false;
        }

        // 验证权限类型是否有效
        PermissionType type = PermissionType.getByCode(permissionType);
        if (type == null)
        {
            log.error("无效的权限类型，permissionType: {}", permissionType);
            return false;
        }

        // 3. 根据权限类型更新对应的权限字段
        Integer isSuper = null; // 不修改超级账户标识
        Integer isOpenAccountPerm = null;
        Integer isOpenModulePerm = null;

        if (type == PermissionType.ACCOUNT_PERM)
        {
            // 更新账户权限管理权限
            isOpenAccountPerm = isOpen;
        }
        else if (type == PermissionType.MODULE_PERM)
        {
            // 更新模块功能操作权限
            isOpenModulePerm = isOpen;
        }

        int result = sysUserExtendMapper.updateUserPermissions(userId, isSuper, isOpenAccountPerm, isOpenModulePerm);
        return result > 0;
    }

    /**
     * 查询所有用户的权限信息列表
     * 
     * 业务逻辑：
     * 1. 权限检查：只有拥有账户权限管理权限的用户或超级账户才能查看所有用户的权限
     * 2. 查询所有用户的权限信息
     * 
     * @return 用户扩展信息列表（包含权限信息）
     */
    @Override
    public List<SysUserExtend> selectAllUserPermissions()
    {
        // 1. 权限检查：获取当前登录用户，检查是否有账户权限管理权限或超级账户权限
        Long currentUserId = SecurityUtils.getUserId();
        SysUserExtend currentUser = sysUserExtendMapper.selectUserExtendByUserId(currentUserId);
        
        // 检查账户权限管理权限
        if (currentUser == null || currentUser.getIsOpenAccountPerm() == null || currentUser.getIsOpenAccountPerm() == 0)
        {
            // 如果不是账户权限管理权限，再检查是否为超级账户
            if (currentUser == null || currentUser.getIsSuper() == null || currentUser.getIsSuper() == 0)
            {
                log.error("用户无权限查看所有用户权限，userId: {}", currentUserId);
                throw new RuntimeException("无权限查看所有用户权限，需要账户权限管理权限或超级账户权限");
            }
        }

        // 2. 查询所有用户的权限信息
        return sysUserExtendMapper.selectAllUserPermissions();
    }
}
