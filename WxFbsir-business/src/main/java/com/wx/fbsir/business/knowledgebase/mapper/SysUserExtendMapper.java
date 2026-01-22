package com.wx.fbsir.business.knowledgebase.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.wx.fbsir.business.knowledgebase.domain.SysUserExtend;

/**
 * 用户扩展Mapper接口
 * 
 * @author wxfbsir
 * @date 2025-12-20
 */
public interface SysUserExtendMapper
{
    /**
     * 查询用户扩展信息
     * 
     * @param userId 用户ID
     * @return 用户扩展信息
     */
    public SysUserExtend selectUserExtendByUserId(Long userId);


    /**
     * 更新用户知识库空间额度
     * 
     * @param userId 用户ID
     * @param kbSpaceQuota 知识库空间额度
     * @return 结果
     */
    public int updateKbSpaceQuota(@Param("userId") Long userId, @Param("kbSpaceQuota") Long kbSpaceQuota);

    /**
     * 更新用户已使用的知识库空间
     * 
     * @param userId 用户ID
     * @param kbSpaceUsed 已使用空间（MB）
     * @return 结果
     */
    public int updateKbSpaceUsed(@Param("userId") Long userId, @Param("kbSpaceUsed") Long kbSpaceUsed);

    /**
     * 更新用户知识库空间包含的知识库ID
     * 
     * @param userId 用户ID
     * @param kbSpaceIncludeKbIds 知识库ID列表（逗号分隔）
     * @return 结果
     */
    public int updateKbSpaceIncludeKbIds(@Param("userId") Long userId, @Param("kbSpaceIncludeKbIds") String kbSpaceIncludeKbIds);

    /**
     * 更新用户权限
     * 
     * @param userId 用户ID
     * @param isSuper 是否为超级账户
     * @param isOpenAccountPerm 账户权限管理权限是否开放
     * @param isOpenModulePerm 模块功能操作权限是否开放
     * @return 结果
     */
    public int updateUserPermissions(@Param("userId") Long userId, 
                                      @Param("isSuper") Integer isSuper,
                                      @Param("isOpenAccountPerm") Integer isOpenAccountPerm,
                                      @Param("isOpenModulePerm") Integer isOpenModulePerm);

    /**
     * 更新用户拥有的知识库ID列表
     *
     * @param userId 用户ID
     * @param hasKnowledgeBase 拥有的知识库ID列表（逗号分隔）
     * @return 结果
     */
    public int updateHasKnowledgeBase(@Param("userId") Long userId, @Param("hasKnowledgeBase") String hasKnowledgeBase);

    /**
     * 更新用户收藏的知识库ID列表
     *
     * @param userId 用户ID
     * @param kbLikesIds 收藏的知识库ID列表（逗号分隔）
     * @return 结果
     */
    public int updateKbLikesIds(@Param("userId") Long userId, @Param("kbLikesIds") String kbLikesIds);

    /**
     * 更新用户扩展信息
     * 
     * @param userExtend 用户扩展信息
     * @return 结果
     */
    public int updateUserExtend(SysUserExtend userExtend);

    /**
     * 查询所有用户的权限信息列表
     * 
     * @return 用户扩展信息列表（包含权限信息）
     */
    public List<SysUserExtend> selectAllUserPermissions();

    /**
     * 插入用户扩展信息（如果不存在）
     * 
     * @param userId 用户ID
     * @return 结果
     */
    public int insertUserExtend(Long userId);
}
