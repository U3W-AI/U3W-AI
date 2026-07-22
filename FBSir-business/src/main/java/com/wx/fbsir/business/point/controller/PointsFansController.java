package com.wx.fbsir.business.point.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.domain.entity.SysUser;
import com.wx.fbsir.common.core.page.TableDataInfo;
import com.wx.fbsir.business.point.domain.PointsRecord;
import com.wx.fbsir.business.point.service.IPointsService;

/**
 * 粉丝积分管理Controller
 * 
 * @author FBSir
 * @date 2025-12-10
 */
@RestController
@RequestMapping("/points/fans")
public class PointsFansController extends BaseController {

    @Autowired
    private IPointsService pointsService;

    /**
     * 获取粉丝列表
     */
    @PreAuthorize("@ss.hasAnyRoles('admin,manager') and @ss.hasPermi('points:fans:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysUser user) {
        startPage();
        // 查询在服务层进入@DataScope，保留全局管理角色的数据范围合同。
        List<SysUser> list = pointsService.getPointsFansList(user);
        return getDataTable(list);
    }

    /**
     * 给用户发放积分
     */
    @PostMapping("/grantPoints")
    public ResponseEntity<AjaxResult> grantPoints() {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(AjaxResult.error(
                        HttpStatus.GONE.value(),
                        "POINTS_ADMIN_GRANT_RETIRED_USE_CREDIT_LEDGER"));
    }

    /**
     * 查询用户积分明细
     */
    @PreAuthorize("@ss.hasAnyRoles('admin,manager') and @ss.hasPermi('points:fans:detail')")
    @GetMapping("/getPointsRecord")
    public TableDataInfo getPointsRecord(PointsRecord pointsRecord) {
        startPage();
        List<PointsRecord> list = pointsService.getPointsRecord(pointsRecord);
        return getDataTable(list);
    }
}
