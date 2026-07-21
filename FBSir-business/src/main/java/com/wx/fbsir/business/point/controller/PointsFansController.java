package com.wx.fbsir.business.point.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.domain.entity.SysUser;
import com.wx.fbsir.common.core.page.TableDataInfo;
import com.wx.fbsir.common.enums.BusinessType;
import com.wx.fbsir.business.point.domain.PointsRecord;
import com.wx.fbsir.business.point.service.IPointsService;
import com.wx.fbsir.system.service.ISysUserService;

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
    private ISysUserService userService;

    @Autowired
    private IPointsService pointsService;

    /**
     * 获取粉丝列表
     */
    @PreAuthorize("@ss.hasPermi('points:fans:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysUser user) {
        startPage();
        // 使用积分服务的专门方法获取粉丝列表，避免数据范围限制
        List<SysUser> list = pointsService.getPointsFansList(user);
        return getDataTable(list);
    }

    /**
     * 给用户发放积分
     */
    @PreAuthorize("@ss.hasRole('admin') and @ss.hasPermi('points:fans:grant')")
    @Log(title = "粉丝积分管理", businessType = BusinessType.GRANT)
    @PostMapping("/grantPoints")
    public AjaxResult grantPoints(@RequestParam Long userId, 
                                  @RequestParam Integer pointsAmount, 
                                  @RequestParam String remark) {
        return pointsService.grantPointsByAdmin(userId, pointsAmount, remark);
    }

    /**
     * 查询用户积分明细
     */
    @PreAuthorize("@ss.hasPermi('points:fans:detail')")
    @GetMapping("/getPointsRecord")
    public TableDataInfo getPointsRecord(PointsRecord pointsRecord) {
        startPage();
        List<PointsRecord> list = pointsService.getPointsRecord(pointsRecord);
        return getDataTable(list);
    }
}
