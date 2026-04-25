package com.wx.fbsir.business.fbs.service.business;

import com.wx.fbsir.business.fbs.domain.entity.FbsEnterprisePack;
import com.wx.fbsir.business.fbs.dto.business.enterprise.EnterprisePackDetailResponse;
import com.wx.fbsir.business.fbs.dto.business.enterprise.EnterprisePackGrantRequest;
import com.wx.fbsir.business.fbs.dto.business.enterprise.EnterprisePackPageRequest;

import java.util.List;

/**
 * 企业场景包分发BusinessService接口
 *
 * @author wxfbsir
 * @date 2026-04-09
 */
public interface IFbsEnterprisePackBusinessService {

    /**
     * 平台向企业分发场景包（幂等：已授权→返回已有；已撤销→重新授权）
     *
     * @param request 分发请求
     * @param createdBy 创建人
     * @return 企业包ID
     */
    Long grantPackToEnterprise(EnterprisePackGrantRequest request, String createdBy);

    /**
     * 平台撤销企业场景包（Fail-Closed：仅 status=1 可撤销）
     *
     * @param enterprisePackId 企业包ID
     * @param updatedBy 更新人
     * @return 是否成功
     */
    boolean revokePackFromEnterprise(Long enterprisePackId, String updatedBy);

    /**
     * 查询企业已获场景包列表（分页）
     *
     * @param enterpriseId 企业ID
     * @return 企业包列表
     */
    List<FbsEnterprisePack> getByEnterprise(Long enterpriseId);

    /**
     * 查询企业已获场景包列表（支持按 status 筛选）
     *
     * @param request 查询请求（含 enterpriseId 和可选 status）
     * @return 企业包列表
     */
    List<FbsEnterprisePack> getEnterprisePackPage(EnterprisePackPageRequest request);

    /**
     * 查询企业包详情
     *
     * @param id 企业包ID
     * @return 详情响应
     */
    EnterprisePackDetailResponse getDetail(Long id);
}
