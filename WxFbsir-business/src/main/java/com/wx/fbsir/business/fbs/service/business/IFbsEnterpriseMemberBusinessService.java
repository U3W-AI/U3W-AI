package com.wx.fbsir.business.fbs.service.business;

import com.wx.fbsir.business.fbs.domain.entity.FbsEnterpriseMember;
import com.wx.fbsir.business.fbs.dto.business.enterprise.EnterpriseMemberAddRequest;
import com.wx.fbsir.business.fbs.dto.business.enterprise.EnterpriseMemberDetailResponse;

import java.util.List;

/**
 * 企业成员管理BusinessService接口
 *
 * @author wxfbsir
 * @date 2026-04-09
 */
public interface IFbsEnterpriseMemberBusinessService {

    /**
     * 添加企业成员（幂等：已存在则抛异常）
     *
     * @param request 添加请求
     * @param createdBy 创建人
     * @return 成员ID
     */
    Long addMember(EnterpriseMemberAddRequest request, String createdBy);

    /**
     * 移除企业成员（Fail-Closed：仅 status=1 可移除）
     *
     * @param memberId 成员ID
     * @param updatedBy 更新人
     * @return 是否成功
     */
    boolean removeMember(Long memberId, String updatedBy);

    /**
     * 查询企业成员分页列表
     *
     * @param enterpriseId 企业ID
     * @return 成员列表
     */
    List<FbsEnterpriseMember> getMemberPage(Long enterpriseId);

    /**
     * 查询成员详情
     *
     * @param memberId 成员ID
     * @return 详情响应
     */
    EnterpriseMemberDetailResponse getMemberDetail(Long memberId);
}
