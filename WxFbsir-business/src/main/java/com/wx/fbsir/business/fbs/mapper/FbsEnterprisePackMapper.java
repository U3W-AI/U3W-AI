package com.wx.fbsir.business.fbs.mapper;

import com.wx.fbsir.business.fbs.domain.entity.FbsEnterprisePack;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 企业场景包Mapper接口
 *
 * @author wxfbsir
 * @date 2026-04-09
 */
@Mapper
public interface FbsEnterprisePackMapper {

    /**
     * 根据ID查询
     *
     * @param id 主键
     * @return 企业包记录
     */
    FbsEnterprisePack selectById(@Param("id") Long id);

    /**
     * 根据企业ID和场景包ID查询（用于幂等检查）
     *
     * @param enterpriseId 企业ID
     * @param packId 场景包ID
     * @return 企业包记录
     */
    FbsEnterprisePack selectByEnterpriseAndPack(@Param("enterpriseId") Long enterpriseId,
                                                 @Param("packId") Long packId);

    /**
     * 根据企业ID查询已获场景包列表
     *
     * @param enterpriseId 企业ID
     * @return 企业包列表
     */
    List<FbsEnterprisePack> selectByEnterpriseId(@Param("enterpriseId") Long enterpriseId);

    /**
     * 列表查询（支持按 enterpriseId / packId / status 筛选）
     *
     * @param filter 过滤条件
     * @return 企业包列表
     */
    List<FbsEnterprisePack> selectEnterprisePackList(@Param("filter") FbsEnterprisePack filter);

    /**
     * 新增企业包
     *
     * @param enterprisePack 企业包
     * @return 影响行数
     */
    int insertEnterprisePack(FbsEnterprisePack enterprisePack);

    /**
     * 修改企业包
     *
     * @param enterprisePack 企业包
     * @return 影响行数
     */
    int updateEnterprisePack(FbsEnterprisePack enterprisePack);

    /**
     * 增量更新已使用配额（usedQuota++，原子操作）
     *
     * @param id 企业包ID
     * @return 影响行数
     */
    int incrementUsedQuota(@Param("id") Long id);
}
