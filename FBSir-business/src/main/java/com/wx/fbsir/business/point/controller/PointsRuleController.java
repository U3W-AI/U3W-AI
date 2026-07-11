package com.wx.fbsir.business.point.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.page.TableDataInfo;
import com.wx.fbsir.common.enums.BusinessType;
import com.wx.fbsir.business.point.domain.PointsRule;
import com.wx.fbsir.business.point.service.IPointsRuleService;

/**
 * 积分规则Controller
 * 
 * @author FBSir
 * @date 2025-12-08
 */
@RestController
@RequestMapping("/points/rule")
public class PointsRuleController extends BaseController {
    
    @Autowired
    private IPointsRuleService pointsRuleService;

    /**
     * 查询积分规则列表
     */
    @PreAuthorize("@ss.hasPermi('points:rule:list')")
    @GetMapping("/list")
    public TableDataInfo list(PointsRule pointsRule) {
        startPage();
        List<PointsRule> list = pointsRuleService.selectPointsRuleList(pointsRule);
        return getDataTable(list);
    }

    /**
     * 获取积分规则详细信息
     */
    @PreAuthorize("@ss.hasPermi('points:rule:query')")
    @GetMapping(value = "/{ruleId}")
    public AjaxResult getInfo(@PathVariable("ruleId") Long ruleId) {
        return success(pointsRuleService.selectPointsRuleByRuleId(ruleId));
    }

    /**
     * 根据规则编码获取积分规则信息
     */
    @PreAuthorize("@ss.hasPermi('points:rule:query')")
    @GetMapping(value = "/code/{ruleCode}")
    public AjaxResult getByCode(@PathVariable("ruleCode") String ruleCode) {
        return success(pointsRuleService.getRuleByCode(ruleCode));
    }
    /**
     * 新增积分规则
     */
    @PreAuthorize("@ss.hasPermi('points:rule:add')")
    @Log(title = "积分规则", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody PointsRule pointsRule) {
        pointsRule.setCreateBy(getUsername());
        return toAjax(pointsRuleService.insertPointsRule(pointsRule));
    }

    /**
     * 修改积分规则
     */
    @PreAuthorize("@ss.hasPermi('points:rule:edit')")
    @Log(title = "积分规则", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody PointsRule pointsRule) {
        pointsRule.setUpdateBy(getUsername());
        return toAjax(pointsRuleService.updatePointsRule(pointsRule));
    }

    /**
     * 删除积分规则
     */
    @PreAuthorize("@ss.hasPermi('points:rule:remove')")
    @Log(title = "积分规则", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ruleIds}")
    public AjaxResult remove(@PathVariable Long[] ruleIds) {
        return toAjax(pointsRuleService.deletePointsRuleByRuleIds(ruleIds));
    }
}

