package com.wx.fbsir.business.fbs.mapper;

import com.wx.fbsir.business.fbs.domain.entity.FbsEnterprise;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 企业Mapper接口
 *
 * @author FBSir
 * @date 2026-04-09
 */
@Mapper
public interface FbsEnterpriseMapper {

    /**
     * 根据ID查询企业
     *
     * @param id 主键
     * @return 企业
     */
    FbsEnterprise selectById(@Param("id") Long id);

    /**
     * 根据企业名称查询（用于重名检查）
     *
     * @param enterpriseName 企业名称
     * @return 企业
     */
    FbsEnterprise selectByName(@Param("enterpriseName") String enterpriseName);

    /**
     * 列表查询（支持按 enterpriseName / status 筛选，Mapper 不带 Page 参数）
     * 分页由 Controller 层 startPage() 触发，PageHelper 通过 ThreadLocal 拦截 SQL
     *
     * @param filter 过滤条件（enterpriseName / status）
     * @return 企业列表
     */
    List<FbsEnterprise> selectEnterpriseList(@Param("filter") FbsEnterprise filter);

    /**
     * 新增企业
     *
     * @param enterprise 企业
     * @return 影响行数
     */
    int insertEnterprise(FbsEnterprise enterprise);

    /**
     * 修改企业
     *
     * @param enterprise 企业
     * @return 影响行数
     */
    int updateEnterprise(FbsEnterprise enterprise);
}
