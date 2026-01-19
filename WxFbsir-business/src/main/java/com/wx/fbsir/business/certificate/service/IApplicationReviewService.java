package com.wx.fbsir.business.certificate.service;

import com.wx.fbsir.business.certificate.domain.ApplicationReview;

import java.util.List;

/**
 * 申请审核记录Service接口
 * 
 * @author wxfbsir
 */
public interface IApplicationReviewService 
{
    /**
     * 查询申请审核记录
     * 
     * @param reviewId 申请审核记录ID
     * @return 申请审核记录
     */
    public ApplicationReview selectApplicationReviewById(Long reviewId);

    /**
     * 查询申请审核记录列表
     * 
     * @param applicationReview 申请审核记录
     * @return 申请审核记录集合
     */
    public List<ApplicationReview> selectApplicationReviewList(ApplicationReview applicationReview);

    /**
     * 新增申请审核记录
     * 
     * @param applicationReview 申请审核记录
     * @return 结果
     */
    public int insertApplicationReview(ApplicationReview applicationReview);

    /**
     * 修改申请审核记录
     * 
     * @param applicationReview 申请审核记录
     * @return 结果
     */
    public int updateApplicationReview(ApplicationReview applicationReview);

    /**
     * 批量删除申请审核记录
     * 
     * @param reviewIds 需要删除的申请审核记录ID
     * @return 结果
     */
    public int deleteApplicationReviewByIds(Long[] reviewIds);

    /**
     * 删除申请审核记录信息
     * 
     * @param reviewId 申请审核记录ID
     * @return 结果
     */
    public int deleteApplicationReviewById(Long reviewId);
    
    /**
     * 审核认证申请
     * 
     * @param applicationReview 审核信息
     * @return 结果
     */
    public int reviewApplication(ApplicationReview applicationReview);
    
    /**
     * 查询申请审核记录列表（带详细信息）
     * 
     * @param applicationReview 申请审核记录
     * @return 申请审核记录集合
     */
    public List<com.wx.fbsir.business.certificate.domain.vo.ApplicationReviewVo> selectApplicationReviewListWithDetails(ApplicationReview applicationReview);
}