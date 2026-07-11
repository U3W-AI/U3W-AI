package com.wx.fbsir.business.fbs.service.business;

import com.wx.fbsir.business.fbs.domain.entity.FbsEnterprise;
import com.wx.fbsir.business.fbs.dto.business.enterprise.*;

import java.util.List;

/**
 * 企业组织管理BusinessService接口
 *
 * @author FBSir
 * @date 2026-04-09
 */
public interface IFbsEnterpriseBusinessService {

    /**
     * 创建企业
     *
     * @param request 创建请求
     * @param createdBy 创建人
     * @return 企业ID
     */
    Long createEnterprise(EnterpriseCreateRequest request, String createdBy);

    /**
     * 编辑企业
     *
     * @param id 企业ID
     * @param request 编辑请求
     * @param updatedBy 更新人
     * @return 是否成功
     */
    boolean updateEnterprise(Long id, EnterpriseUpdateRequest request, String updatedBy);

    /**
     * 查询企业详情
     *
     * @param id 企业ID
     * @return 详情响应
     */
    EnterpriseDetailResponse getEnterpriseDetail(Long id);

    /**
     * 查询企业分页列表
     *
     * @param request 分页请求
     * @return 企业列表
     */
    List<FbsEnterprise> getEnterprisePage(EnterprisePageRequest request);

    /**
     * 禁用企业（Fail-Closed：仅正常状态可禁用）
     *
     * @param id 企业ID
     * @param updatedBy 更新人
     * @return 是否成功
     */
    boolean disableEnterprise(Long id, String updatedBy);
}
