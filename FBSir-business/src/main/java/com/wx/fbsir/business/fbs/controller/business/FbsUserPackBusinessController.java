package com.wx.fbsir.business.fbs.controller.business;

import com.wx.fbsir.business.fbs.domain.entity.FbsUserPack;
import com.wx.fbsir.business.fbs.dto.business.user_pack.UserPackPageRequest;
import com.wx.fbsir.business.fbs.dto.business.user_pack.UserPackStatsResponse;
import com.wx.fbsir.business.fbs.service.business.IFbsUserPackBusinessService;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.page.TableDataInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户权益查询Controller
 * 路径: /business/fbs/user-pack/*
 *
 * @author FBSir
 * @date 2026-04-08
 */
@RestController
@RequestMapping("/business/fbs/user-pack")
public class FbsUserPackBusinessController extends BaseController {

    @Autowired
    private IFbsUserPackBusinessService userPackBusinessService;

    /**
     * GET /business/fbs/user-pack/list
     * 用户-场景包分页列表
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:userPack:list')")
    @GetMapping("/list")
    public TableDataInfo list(UserPackPageRequest request) {
        startPage();
        List<FbsUserPack> list = userPackBusinessService.getUserPackPage(request);
        return getDataTable(list);
    }

    /**
     * GET /business/fbs/user-pack/stats
     * 用户权益统计
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:userPack:query')")
    @GetMapping("/stats")
    public AjaxResult stats(@RequestParam Long userId) {
        if (userId == null) {
            return AjaxResult.error("userId不能为空");
        }
        UserPackStatsResponse stats = userPackBusinessService.getUserPackStats(userId);
        return AjaxResult.success(stats);
    }
}
