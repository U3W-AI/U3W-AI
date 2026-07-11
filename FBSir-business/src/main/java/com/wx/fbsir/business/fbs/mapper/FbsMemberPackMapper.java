package com.wx.fbsir.business.fbs.mapper;

import com.wx.fbsir.business.fbs.domain.entity.FbsMemberPack;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 成员场景包授权Mapper接口
 *
 * @author FBSir
 * @date 2026-04-09
 */
@Mapper
public interface FbsMemberPackMapper {

    /**
     * 根据ID查询
     *
     * @param id 主键
     * @return 授权记录
     */
    FbsMemberPack selectById(@Param("id") Long id);

    /**
     * 根据成员ID查询所有授权记录
     *
     * @param memberId 企业成员ID
     * @return 授权列表
     */
    List<FbsMemberPack> selectByMemberId(@Param("memberId") Long memberId);

    /**
     * 批量新增成员包授权（平台分发时为所有成员建授权记录）
     *
     * @param list 授权记录列表
     * @return 影响行数
     */
    int insertMemberPackBatch(@Param("list") List<FbsMemberPack> list);

    /**
     * 根据企业包ID批量更新成员授权状态（级联撤销）
     *
     * @param enterprisePackId 企业包ID
     * @param status 新状态（3=已撤销）
     * @param updatedBy 更新人
     * @return 影响行数
     */
    int updateStatusByEnterprisePackId(@Param("enterprisePackId") Long enterprisePackId,
                                       @Param("status") Integer status,
                                       @Param("updatedBy") String updatedBy);

    /**
     * 查询成员是否获特定场景包授权（用于企业配额路径校验成员授权凭证）
     * 仅查 status=1 且未过期的活跃授权
     *
     * @param memberId 企业成员ID
     * @param packId 场景包ID
     * @return 授权记录（null 表示未获授权）
     */
    FbsMemberPack selectActiveByMemberIdAndPackId(@Param("memberId") Long memberId,
                                                   @Param("packId") Long packId);
}
