package com.wx.fbsir.business.fbs.service.business;

import com.wx.fbsir.business.fbs.domain.entity.FbsUserPack;
import com.wx.fbsir.business.fbs.dto.business.user_pack.UserPackDetailResponse;
import com.wx.fbsir.business.fbs.dto.business.user_pack.UserPackPageRequest;
import com.wx.fbsir.business.fbs.dto.business.user_pack.UserPackStatsResponse;

import java.util.List;

/**
 * 用户权益查询BusinessService接口
 *
 * @author FBSir
 * @date 2026-04-08
 */
public interface IFbsUserPackBusinessService {

    /**
     * 用户-场景包分页列表
     *
     * @param request 分页请求
     * @return 用户权益列表
     */
    List<FbsUserPack> getUserPackPage(UserPackPageRequest request);

    /**
     * 用户权益统计
     *
     * @param userId 用户ID
     * @return 统计结果
     */
    UserPackStatsResponse getUserPackStats(Long userId);
}
