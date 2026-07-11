package com.wx.fbsir.business.gitee.controller;

import java.util.Map;

import com.wx.fbsir.business.gitee.service.GiteeAnalysisService;
import com.wx.fbsir.business.point.domain.PointsResult;
import com.wx.fbsir.business.point.service.PointsPrecheckService;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.utils.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gitee分析评测Controller
 *
 * @author FBSir
 * @date 2026-01-03
 */
@RestController
@RequestMapping("/business/gitee/analysis")
public class GiteeAnalysisController {
    private static final Logger log = LoggerFactory.getLogger(GiteeAnalysisController.class);
    private static final String RULE_CODE_GITEE_ANALYSIS = "GITEE_ANALYSIS";

    private final GiteeAnalysisService giteeAnalysisService;
    private final PointsPrecheckService pointsPrecheckService;

    public GiteeAnalysisController(GiteeAnalysisService giteeAnalysisService,
                                   PointsPrecheckService pointsPrecheckService) {
        this.giteeAnalysisService = giteeAnalysisService;
        this.pointsPrecheckService = pointsPrecheckService;
    }

    /**
     * 触发Gitee分析评测并返回结果
     *
     * @return 评测结果
     */
    @PostMapping("/reevaluate")
    public AjaxResult reevaluate() {
        Long userId = SecurityUtils.getUserId();
        try {
            // 积分前置校验：不足则直接拦截评测流程
            PointsResult pointsResult = pointsPrecheckService.tryChangePoints(userId, RULE_CODE_GITEE_ANALYSIS, null);
            if (!pointsResult.isSuccess()) {
                return AjaxResult.error(pointsResult.getMsg());
            }
            Map<String, Object> result = giteeAnalysisService.reevaluate(userId);
            // 评测成功后返回扣减后的积分余额
            result.put("pointsBalance", pointsResult.getBalanceAfter());
            return AjaxResult.success(result);
        } catch (Exception ex) {
            log.error("Gitee分析评测失败", ex);
            return AjaxResult.error(ex.getMessage());
        }
    }

    /**
     * 保存评测报告数据
     *
     * @param analysis 评测结果
     * @return 操作结果
     */
    @PostMapping("/report")
    public AjaxResult saveReport(@RequestBody Map<String, Object> analysis) {
        Long userId = SecurityUtils.getUserId();
        if (analysis == null || analysis.isEmpty()) {
            return AjaxResult.error("评测结果为空");
        }
        giteeAnalysisService.saveAnalysisReport(userId, analysis);
        return AjaxResult.success();
    }
}
