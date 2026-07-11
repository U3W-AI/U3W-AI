package com.wx.fbsir.business.fbs.service.business.impl;

import com.wx.fbsir.business.fbs.domain.entity.FbsUserPack;
import com.wx.fbsir.business.fbs.dto.business.user_pack.UserPackDetailResponse;
import com.wx.fbsir.business.fbs.dto.business.user_pack.UserPackPageRequest;
import com.wx.fbsir.business.fbs.dto.business.user_pack.UserPackStatsResponse;
import com.wx.fbsir.business.fbs.mapper.FbsUserPackMapper;
import com.wx.fbsir.business.fbs.service.business.IFbsUserPackBusinessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户权益查询BusinessService实现
 *
 * @author FBSir
 * @date 2026-04-08
 */
@Service
public class FbsUserPackBusinessServiceImpl implements IFbsUserPackBusinessService {

    @Autowired
    private FbsUserPackMapper userPackMapper;

    @Override
    public List<FbsUserPack> getUserPackPage(UserPackPageRequest request) {
        FbsUserPack filter = new FbsUserPack();
        if (request.getUserId() != null) {
            filter.setUserId(request.getUserId());
        }
        if (request.getStatus() != null) {
            filter.setStatus(request.getStatus());
        }
        return userPackMapper.selectUserPackList(filter);
    }

    @Override
    public UserPackStatsResponse getUserPackStats(Long userId) {
        UserPackStatsResponse resp = new UserPackStatsResponse();
        resp.setUserId(userId);
        resp.setTotalCount(userPackMapper.countByUserIdAndStatus(userId, null));
        resp.setActiveCount(userPackMapper.countByUserIdAndStatus(userId, 1));
        resp.setExpiredCount(userPackMapper.countByUserIdAndStatus(userId, 2));
        resp.setRevokedCount(userPackMapper.countByUserIdAndStatus(userId, 3));
        return resp;
    }
}
