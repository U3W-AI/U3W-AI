package com.wx.fbsir.business.fbs.mapper;

import com.wx.fbsir.business.fbs.domain.entity.FbsEnterpriseMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 企业成员Mapper接口
 *
 * @author wxfbsir
 * @date 2026-04-09
 */
@Mapper
public interface FbsEnterpriseMemberMapper {

    /**
     * 根据ID查询成员
     *
     * @param id 主键
     * @return 成员记录
     */
    FbsEnterpriseMember selectById(@Param("id") Long id);

    /**
     * 根据用户ID和企业ID查询成员（用于幂等检查：是否已是该企业成员）
     *
     * @param enterpriseId 企业ID
     * @param userId 用户ID
     * @return 成员记录
     */
    FbsEnterpriseMember selectByEnterpriseAndUser(@Param("enterpriseId") Long enterpriseId,
                                                 @Param("userId") Long userId);

    /**
     * 根据用户ID查询该用户所属的所有正常企业成员记录
     *
     * @param userId 用户ID
     * @return 企业成员记录列表
     */
    List<FbsEnterpriseMember> selectActiveByUserId(@Param("userId") Long userId);

    /**
     * 根据企业ID查询所有成员列表（用于分发时批量建授权记录）
     *
     * @param enterpriseId 企业ID
     * @return 成员列表（仅 status=1 正常成员）
     */
    List<FbsEnterpriseMember> selectActiveByEnterpriseId(@Param("enterpriseId") Long enterpriseId);

    /**
     * 根据企业ID查询成员列表（分页）
     *
     * @param filter 过滤条件（enterpriseId / status）
     * @return 成员列表
     */
    List<FbsEnterpriseMember> selectMemberList(@Param("filter") FbsEnterpriseMember filter);

    /**
     * 新增成员
     *
     * @param member 成员
     * @return 影响行数
     */
    int insertMember(FbsEnterpriseMember member);

    /**
     * 修改成员状态
     *
     * @param member 成员（含 id 和 status）
     * @return 影响行数
     */
    int updateMember(FbsEnterpriseMember member);
}
