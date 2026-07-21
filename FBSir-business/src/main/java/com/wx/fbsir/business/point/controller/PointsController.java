package com.wx.fbsir.business.point.controller;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.page.TableDataInfo;
import com.wx.fbsir.common.enums.BusinessType;
import com.wx.fbsir.business.point.domain.PointsRecord;
import com.wx.fbsir.business.point.service.IPointsService;

/**
 * 积分Controller
 * 
 * @author FBSir
 * @date 2025-12-08
 */
@RestController
@RequestMapping("/points")
public class PointsController extends BaseController {
    
    @Autowired
    private IPointsService pointsService;

    /**
     * 积分发放/扣减
     */
    @Log(title = "积分操作", businessType = BusinessType.OTHER)
    @PostMapping("/changePoints")
    public ResponseEntity<AjaxResult> changePoints() {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(AjaxResult.error(HttpStatus.GONE.value(),
                        "POINTS_DIRECT_MUTATION_DISABLED"));
    }

    /**
     * 查询用户积分余额
     */
    @GetMapping("/getUserPoints")
    public AjaxResult getUserPoints() {
        Long userId = getUserId();
        Integer points = pointsService.getUserPoints(userId);
        return AjaxResult.success(points);
    }

    /**
     * 查询积分明细记录
     */
    @GetMapping("/getPointsRecord")
    public TableDataInfo getPointsRecord(PointsRecord pointsRecord) {
        startPage();
        Long userId = getUserId();
        pointsRecord.setUserId(userId);
        List<PointsRecord> list = pointsService.getPointsRecord(pointsRecord);
        return getDataTable(list);
    }

    /**
     * 获取积分概览
     */
    @GetMapping("/getPointsSummary")
    public AjaxResult getPointsSummary() {
        Long userId = getUserId();
        Map<String, Object> summary = pointsService.getPointsSummary(userId);
        return AjaxResult.success(summary);
    }

    /**
     * 获取积分任务清单
     */
    @GetMapping("/getPointTaskList")
    public AjaxResult getPointTaskList() {
        Long userId = getUserId();
        List<Map<String, Object>> taskList = pointsService.getPointsTaskList(userId);
        return AjaxResult.success(taskList);
    }
}

